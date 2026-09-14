# Flujo E2E de análisis de texto

## Alcance F1.3

F1.3 implementa la primera vertical funcional de análisis. Angular continúa comunicándose solo con
Java y el proveedor se consume exclusivamente mediante `GenerativeAiProvider`. PDF, audio, RAG,
Gemini, disclaimer institucional y lógica jurídica definitiva permanecen fuera de alcance.

## Contrato HTTP

`POST /identipat-ia/analyses/text` recibe únicamente `description`. Requiere la cookie
`IDENTIPAT_STANDARD_SESSION`, sesión activa, usuario STANDARD activo, consentimiento `ACCEPTED` para
la versión y hash vigentes, y el par CSRF `XSRF-TOKEN`/`X-XSRF-TOKEN`. Si la IA está deshabilitada,
responde `503` sin crear filas. La descripción se normaliza a Unicode NFC, finales LF y trim de
extremos; el original se conserva sin cambios. Los límites se aplican al texto procesado.

La creación guarda `Analysis(RECEIVED)` y `AnalysisInput(TEXT)` en una transacción corta y responde:

```text
202 Accepted
Location: /identipat-ia/analyses/{analysisId}
Retry-After: 2
```

No se llama al LLM en el hilo HTTP. `GET /identipat-ia/analyses/{analysisId}` no exige CSRF y solo
devuelve un análisis de la misma `session_id` activa. Un UUID inexistente o de otra sesión responde
`404`; una sesión ausente/cerrada/expirada responde `401`. GET no vuelve a exigir el consentimiento
vigente y nunca expone prompts, payloads del proveedor, modelo, tokens, lease ni intentos.

## Persistencia y estados

Flyway V3 crea `analyses`, `analysis_inputs`, `ai_invocations` y `analysis_results`. Las FK compuestas
aseguran que `Analysis.user_id`, `session_id` y `consent_event_id` pertenecen a la misma evidencia.
No hay `ON DELETE CASCADE` hacia el historial.

F1.3 usa `RECEIVED → ANALYZING → COMPLETED|FAILED`. Cada llamada a `generate` tiene previamente una
`AiInvocation STARTED` durable y termina como `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `INVALID_RESPONSE`
o `ABANDONED`. `analysis_results` es 1:1 e inmutable en el flujo normal; resultado, invocación exitosa
y transición `COMPLETED` se confirman en una misma transacción.

## Worker, claim y transacciones

Cada JVM crea un identificador efímero `analysis-worker-<uuid>`. Un poller programado alimenta un
executor Java acotado. PostgreSQL sigue siendo la cola durable y source of truth; el executor solo
recibe trabajos después del claim.

El claim usa un CTE con `FOR UPDATE SKIP LOCKED` y actualiza atómicamente estado, `started_at`, owner y
expiración del lease. La transacción termina antes de renderizar/invocar al LLM. Si el executor rechaza
la tarea, se libera inmediatamente el lease solo si aún pertenece al mismo owner.

```text
TX 1: claim + lease; commit
TX 2: recuperar stale + crear AiInvocation STARTED; commit
sin TX/conexión DB: GenerativeAiProvider.generate
TX 3: persistir éxito/resultado/COMPLETED o fallo/retry/FAILED; commit
```

No hay heartbeat en F1.3. Por ello `ANALYSIS_LEASE_DURATION` debe superar holgadamente el timeout del
provider en cada ambiente.

## Retry, recovery y semántica

Los retries del SDK siguen deshabilitados. Un fallo marcado `retryable`, mientras el número de intento
sea menor que `ANALYSIS_MAX_ATTEMPTS`, deja `ANALYZING`, fija `next_attempt_at` y libera el lease. No
usa `sleep` ni un estado `RETRYING`. Un fallo no retryable o el agotamiento de intentos deja `FAILED`
sin `AnalysisResult`.

Al reclamar un análisis cuyo lease anterior expiró, cualquier invocación `STARTED` se marca
`ABANDONED` con `WORKER_LEASE_LOST` antes de crear el siguiente intento. Un lease activo nunca se
recupera. Si el proveedor respondió y la JVM cayó antes del commit, el nuevo worker volverá a invocar:
la semántica es **at-least-once** y existe riesgo residual de consumo externo duplicado. No se afirma
exactly-once.

## Prompt, schema y resultado

El worker renderiza `intellectual-property-analysis/0.1`, carga `analysis-result/1.0` y usa
`GenerationOptions(maxOutputTokens=ANALYSIS_AI_MAX_OUTPUT_TOKENS, temperature=null)`, con default
`4000`. La descripción es la única variable del user prompt. El schema estricto exige
`additionalProperties=false` y el modelo Java canónico valida enums, campos y colecciones después de
Jackson.

La validación real mostró que `1500` finalizaba como `status=incomplete` por `max_output_tokens`: los
tokens de razonamiento consumían parte del presupuesto y no quedaba margen suficiente para completar
el JSON estructurado. Con `4000`, la misma solicitud terminó como `completed` y produjo un resultado
válido `analysis-result/1.0`; por ello `4000` es el default técnico actual.

Durante desarrollo se persisten snapshot/hashes de prompt, respuesta raw/structured, usage, tokens,
latencia y finish reason. Esos datos no se escriben en logs ni se exponen a STANDARD.

## Errores

Los códigos internos `AI_*` y `WORKER_PROCESSING_ERROR` se conservan para operación. GET transforma
fallos a `ANALYSIS_TEMPORARILY_UNAVAILABLE`, `ANALYSIS_INVALID_RESULT`,
`ANALYSIS_CONFIGURATION_ERROR` o `ANALYSIS_INTERNAL_ERROR`, con mensajes públicos saneados. No
expone OpenAI, HTTP 429, API key, respuesta raw ni detalles del worker.

## Configuración y pruebas

Los límites, poll, threads, capacidad, lease, intentos, delay y tokens máximos se externalizan con
variables `ANALYSIS_*`. `ANALYSIS_WORKER_ENABLED=false` deshabilita el scheduler; `pollOnce()` y
`processOneSynchronously()` permiten pruebas deterministas sin endpoint administrativo.

La suite usa un fake `GenerativeAiProvider` y PostgreSQL 16 Testcontainers, sin Internet. Cubre el
flujo HTTP completo, CSRF/sesión/consentimiento, normalización/límites, aislamiento, claim concurrente,
lease, retry, máximo de intentos, recuperación `ABANDONED`, prompt/schema y no exposición técnica.

El flujo manual real también quedó validado contra OpenAI: el POST respondió `202` y la consulta
progresó `RECEIVED → ANALYZING → COMPLETED`, con una `AiInvocation SUCCEEDED` y resultado persistido.

## Pendientes explícitos

Quedan pendientes retención/minimización final, idempotency key, heartbeat de lease, prompt jurídico
definitivo, disclaimer, PDF/audio, RAG, Gemini, observabilidad avanzada y exactly-once externo. En
particular, debe mejorarse la observabilidad de respuestas OpenAI `status=incomplete`: el adapter
actual intenta extraer y validar `output_text` antes de preservar model, usage, latencia e
`incomplete_details` en la invocación.
