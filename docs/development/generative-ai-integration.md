# Integración de IA generativa — IDENTIPAT-IA

**Estado:** infraestructura F1.2 ampliada por F2-GEMINI-01 e integrada al flujo durable
**Versión:** 1.2
**Última actualización:** 23 de noviembre de 2026

## 1. Alcance actual

La integración de IA generativa está implementada en Java y es compartida por las entradas TEXT,
PDF y AUDIO. OpenAI y Gemini/Vertex AI son adapters alternativos; solo uno está activo por instancia.

F1.2 incorporó:

- la abstracción `GenerativeAiProvider`;
- el adapter `OpenAiGenerativeAiProvider`;
- prompts y JSON Schemas versionados;
- structured output estricto;
- configuración por ambiente;
- traducción agnóstica de errores;
- pruebas técnicas y smoke real.

F1.3 integró esa infraestructura con:

- `Analysis` y `AnalysisInput` persistidos;
- worker asíncrono;
- claim y lease en PostgreSQL;
- `AiInvocation` durable por intento;
- retry controlado por la aplicación;
- validación y persistencia de `AnalysisResult`;
- recuperación después de fallos o reinicios.

F2-GEMINI-01 añadió Google Gen AI Java 2.5.0, Vertex AI API `v1`, ADC, structured output V3 y pruebas
offline. Siguen fuera de alcance RAG, circuit breaker, fallback automático, selección por solicitud y
la política productiva definitiva de retención y confidencialidad.

## 2. Arquitectura vigente

```text
POST /analyses/text
        ↓
AnalysisApplicationService
        ↓
Analysis(RECEIVED) + AnalysisInput(TEXT)
        ↓
AnalysisWorker
        ↓
AnalysisAttemptService
  ├── PromptRenderer
  ├── OutputSchemaRegistry
  └── AiInvocation(STARTED)
        ↓
GenerativeAiProvider
        ├── OpenAiGenerativeAiProvider ── openai-java 4.63.1 ── OpenAI Responses API
        └── GeminiGenerativeAiProvider ── google-genai 2.5.0 ── Vertex AI generateContent v1
        ↓
validación + AnalysisResult + estado terminal
```

La interfaz permite adapters futuros:

```text
GenerativeAiProvider
├── OpenAiGenerativeAiProvider
└── GeminiGenerativeAiProvider
```

Solo los packages `ai/provider/openai` y `ai/provider/gemini` conocen sus respectivos SDKs.
Controllers, servicios de análisis, sesión STANDARD, persistencia y worker utilizan únicamente
modelos internos de IDENTIPAT-IA.

`IDENTIPAT_AI_PROVIDER` admite exactamente `openai` o `gemini` cuando la IA está habilitada. Las
configuraciones Spring son mutuamente excluyentes y el worker exige un bean único; no escoge por
orden ni implementa fallback.

## 3. Contrato común

### 3.1 `GenerativeAiProvider`

La interfaz es síncrona y deliberadamente pequeña:

```java
public interface GenerativeAiProvider {
    String providerId();
    GenerativeAiResponse generate(GenerativeAiRequest request);
}
```

No conoce:

- `Analysis`;
- usuarios o sesiones;
- HTTP;
- PostgreSQL;
- cookies;
- consentimiento;
- reglas jurídicas del caso de uso.

La asincronía, persistencia, reintentos y recuperación pertenecen al worker y a los servicios de análisis, no al adapter.

### 3.2 `GenerativeAiRequest`

Contiene:

| Componente | Propósito |
|---|---|
| `RenderedPrompt` | Mensajes ya renderizados, referencia, hashes y snapshot. |
| `StructuredOutputDefinition` | ID, versión, JSON Schema y flag `strict`. |
| `GenerationOptions` | Opciones agnósticas, actualmente `maxOutputTokens` y `temperature`. |

No contiene provider, modelo, API key, URL, timeout, `userId`, `sessionId` ni `analysisId`.

Si las opciones son nulas, el record aplica valores por defecto.

### 3.3 `GenerativeAiResponse`

Separa:

- `ProviderExecution`: proveedor, modelo efectivo y request/response ID;
- `structuredOutput`: JSON extraído y validado;
- `rawProviderResponse`: representación JSON semántica del objeto SDK;
- `TokenUsage`: conteos normalizados y detalle del proveedor;
- `finishReason`;
- `latencyMs`.

Los nodos JSON se copian defensivamente. La respuesta no es todavía un `AnalysisResult`: el worker realiza el mapeo al contrato funcional después de validar.

## 4. Prompts versionados

### 4.1 Ubicación

```text
backend/src/main/resources/prompts/
├── intellectual-property-analysis/v0.1/
│   ├── system.md
│   └── user.md
├── intellectual-property-analysis/v0.2/
│   ├── system.md
│   └── user.md
├── intellectual-property-analysis/v0.3.1/
│   ├── system.md
│   └── user.md
└── provider-smoke-test/v1.0/
    ├── system.md
    └── user.md
```

`provider-smoke-test/1.0` es técnico y sintético. El worker usa
`intellectual-property-analysis/0.3.1`; las versiones anteriores se conservan para trazabilidad.

El prompt funcional actual:

- identifica una o varias modalidades y el objeto protegido por cada una;
- exige el schema `analysis-result/2.0`;
- ejecuta screening limitado de los artículos 15 y 20 y alerta de recursos genéticos;
- prohíbe evaluar novedad, nivel inventivo, aplicación industrial y ventaja técnica;
- prohíbe presentar el resultado como decisión oficial;
- no afirma haber realizado búsquedas de antecedentes;
- no permite inventar hechos, legislación o información ausente;
- prohíbe confianza numérica;
- mantiene el disclaimer institucional fuera de `warnings`.

No constituye un examen completo de patentabilidad o registrabilidad.

### 4.2 Registry y renderizado

`PromptRegistry` carga recursos UTF-8 desde classpath, sin base de datos ni hot reload. Los IDs y versiones aceptan únicamente segmentos seguros para impedir traversal de rutas.

`PromptRenderer` sustituye placeholders con forma:

```text
{{VARIABLE}}
```

Reglas:

- nombres con mayúsculas, números y `_`;
- todas las variables deben declararse;
- no pueden quedar placeholders sin resolver;
- las variables adicionales no utilizadas producen error;
- no se evalúan expresiones ni código.

El prompt vigente recibe:

```text
USER_DESCRIPTION = AnalysisInput.processedText
```

### 4.3 Snapshot y hashes

Antes de calcular hashes se normalizan `CRLF` y `CR` a `LF`.

Formato canónico:

```text
[SYSTEM]
contenido system

[USER]
contenido user
```

Se calculan:

- `templateHash`: SHA-256 lowercase del snapshot de las plantillas;
- `renderedHash`: SHA-256 lowercase del snapshot ya renderizado;
- `renderedSnapshot`: contenido determinista exacto enviado al provider.

El snapshot renderizado se persiste en `AiInvocation`. Puede contener información confidencial del usuario; no debe escribirse en logs y su retención productiva debe revisarse en F2.1.

## 5. Schemas y structured output

### 5.1 Ubicación

```text
backend/src/main/resources/ai-schemas/
├── analysis-result/v1.0/schema.json
├── analysis-result/v2.0/schema.json
├── analysis-result/v3.0/schema.json
└── provider-smoke-result/v1.0/schema.json
```

`analysis-result/3.0` es el contrato utilizado por el worker. Conserva las estructuras V2 y exige además
`traditionalKnowledgeAccess` con `assessment`, `rationale` y `missingInformation`. El prompt vigente es
`intellectual-property-analysis/0.3.1`; cada llamada real sigue generando una sola `AiInvocation`, sin retries
en el SDK y sin una segunda inferencia para reportes.

El contrato histórico `analysis-result/2.0` exigía:

- `schemaVersion`;
- `summary`;
- `protectionOptions`;
- `patentScreening`;
- `geneticResourceAccess`;
- `observations`;
- `warnings`.

Utiliza enums cerrados, campos obligatorios y `additionalProperties: false`.

### 5.2 Registro y envío

`OutputSchemaRegistry` carga el contrato de IDENTIPAT-IA y lo entrega como `StructuredOutputDefinition` con `strict=true`.

OpenAI convierte el JSON Schema a `ResponseFormatTextJsonSchemaConfig.Schema`. Gemini envía el mismo
`JsonNode` canónico mediante `responseJsonSchema`; no fue necesaria una transformación ni un
`GeminiSchemaAdapter`. En ambos casos el contrato sigue perteneciendo a IDENTIPAT-IA.

La solicitud utiliza:

```text
Responses API
└── text.format.type = json_schema
    └── strict = true
```

### 5.3 Validación local

Después de extraer el texto:

1. Java lo parsea como JSON.
2. `StructuredOutputValidator` lo valida contra el mismo schema.
3. El worker vuelve a validar el `structuredOutput` recibido.
4. Jackson lo convierte a `AnalysisResult`.
5. Los records Java aplican invariantes adicionales de versión, nulabilidad y textos no vacíos.

Se utiliza `com.networknt:json-schema-validator:1.0.88`, draft 2020-12.

JSON inválido, violación del schema o invariante Java se traduce a `INVALID_RESPONSE`; no se crea `AnalysisResult`.

## 6. Mapping a Gemini/Vertex AI

El cliente singleton usa `Client.builder().enterprise(true)`, proyecto, ubicación, ADC y
`HttpOptions(apiVersion="v1")`. `GOOGLE_APPLICATION_CREDENTIALS` solo comunica a ADC la ubicación
del archivo privado; la aplicación no abre el JSON ni incorpora credenciales al dominio. La carga
local de ADC ocurre al construir el cliente y el refresh OAuth al enviar la solicitud.

El mensaje SYSTEM se transporta como `systemInstruction`; los mensajes USER permanecen como
`Content(role="user")` en su orden original. `maxOutputTokens` y `temperature` solo se envían cuando
están presentes. No se activan tools, grounding, búsqueda, function calling, caching ni thinking.

La generación usa `responseMimeType=application/json`, `candidateCount=1` y
`responseJsonSchema=<schema canónico>`. Java exige una sola candidate, un solo part textual final y
rechaza bloqueos, múltiples candidatos, partes incompatibles, `MAX_TOKENS` y cualquier finalización
distinta de `STOP`/metadata ausente. Los parts marcados como pensamiento no se concatenan con el JSON.

`ProviderExecution` usa `responseId` cuando existe y `modelVersion` cuando Vertex lo devuelve; si no
hay versión efectiva se conserva transparentemente el modelo configurado. Usage normaliza
`promptTokenCount`, `candidatesTokenCount` y `totalTokenCount`; el detalle completo, incluido
`thoughtsTokenCount`, permanece en `providerDetails` sin sumarlo de nuevo.

`rawProviderResponse` es el JSON semántico serializado por el SDK. No contiene headers, objetos
`Credentials` ni tokens OAuth. La latencia usa reloj monotónico alrededor de la llamada síncrona.

## 7. Mapping a OpenAI

### 7.1 Request

- Cada `AiMessage` se convierte en `ResponseInputItem`.
- Se conservan roles `system` y `user`.
- El modelo proviene de configuración externa.
- `maxOutputTokens` y `temperature` se envían solo cuando están presentes.
- El worker F1.3 establece `maxOutputTokens` desde `ANALYSIS_AI_MAX_OUTPUT_TOKENS` y omite `temperature`.
- El adapter no simula compatibilidad de opciones no soportadas por un modelo.
- La solicitud establece explícitamente `store(false)`.

### 7.2 Extracción de contenido

El adapter recorre todos los elementos de `Response.output`, localiza bloques `output_text` dentro de mensajes y exige exactamente uno.

| Cantidad de bloques | Resultado |
|---:|---|
| `1` | Se parsea y valida. |
| `0` | `INVALID_RESPONSE`. |
| `>1` | `INVALID_RESPONSE` para evitar concatenación ambigua. |

### 7.3 Modelo y request ID

- `providerRequestId` corresponde a `Response.id`.
- El modelo efectivo se obtiene de `ResponsesModel` mediante su `Visitor`.
- Las variantes `String`, `ChatModel` y `ResponsesOnlyModel` usan su valor wire.
- Una variante desconocida solo se acepta si el JSON crudo contiene un string no vacío.
- No se reemplaza silenciosamente con el modelo configurado ni se usa `toString()`.

### 7.4 Usage

Se normalizan:

- `input_tokens`;
- `output_tokens`;
- `total_tokens`.

El objeto de usage completo se conserva como detalle del proveedor. Si OpenAI no devuelve usage, los conteos permanecen nulos.

### 7.5 Finalización y latencia

`finishReason` usa:

```text
incomplete:<incomplete_details.reason>
```

cuando ese dato existe. En otro caso utiliza el valor wire de `Response.status`; si falta, `unknown`.

La latencia se mide alrededor de la llamada síncrona mediante reloj monotónico.

## 8. Integración con AnalysisWorker

### 8.1 Preparación del intento

`AnalysisAttemptService.prepare()`:

1. verifica que el worker conserve un lease activo;
2. marca como `ABANDONED` una invocación `STARTED` obsoleta si corresponde;
3. verifica el límite de intentos;
4. carga `AnalysisInput.processedText`;
5. renderiza `intellectual-property-analysis/0.3.1`;
6. carga `analysis-result/3.0`;
7. construye `GenerationOptions`;
8. persiste `AiInvocation(STARTED)` antes de llamar al provider.

### 8.2 Llamada remota

La llamada:

```java
provider.generate(attempt.request())
```

ocurre fuera de una transacción de PostgreSQL. Así no se mantiene una conexión ni locks de base de datos durante la latencia del proveedor.

### 8.3 Éxito

En una transacción final se persisten atómicamente:

- `AiInvocation(SUCCEEDED)`;
- provider y modelo efectivos;
- provider request ID;
- respuesta cruda y estructurada;
- tokens;
- latencia;
- finish reason;
- `AnalysisResult` canónico;
- `Analysis(COMPLETED)`.

El lease y `next_attempt_at` se limpian.

### 8.4 Fallo y retry

Los errores del adapter se mapean a códigos del dominio:

| Error generativo | `AnalysisFailureCode` | Estado de invocación |
|---|---|---|
| `TIMEOUT` | `AI_TIMEOUT` | `TIMED_OUT` |
| `RATE_LIMITED` | `AI_RATE_LIMITED` | `FAILED` |
| `AUTHENTICATION` | `AI_AUTHENTICATION` | `FAILED` |
| `INVALID_REQUEST` | `AI_INVALID_REQUEST` | `FAILED` |
| `INVALID_RESPONSE` | `AI_INVALID_RESPONSE` | `INVALID_RESPONSE` |
| `PROVIDER_UNAVAILABLE` | `AI_PROVIDER_UNAVAILABLE` | `FAILED` |
| `PROVIDER_ERROR` | `AI_PROVIDER_ERROR` | `FAILED` |

Si el error es retryable y aún quedan intentos:

- la invocación se cierra;
- `Analysis` permanece `ANALYZING`;
- se persiste `next_attempt_at`;
- se libera el lease;
- el poller la retomará posteriormente.

No se usa `sleep` para implementar retry.

Si el error no es retryable o se agotan los intentos, `Analysis` pasa a `FAILED` con un mensaje interno saneado. La API traduce después el fallo a un código público estable.

## 9. Persistencia de `AiInvocation`

Cada intento conserva:

| Grupo | Datos |
|---|---|
| Identidad | `invocationId`, `analysisId`, `attemptNumber`, estado |
| Proveedor | provider, modelo y provider request ID |
| Prompt | ID, versión, template hash, rendered hash y snapshot |
| Schema | ID y versión |
| Request | parámetros agnósticos |
| Response | payload crudo y structured output |
| Uso | tokens normalizados y detalle completo |
| Operación | latencia y finish reason |
| Error | código, mensaje saneado y retryable |
| Tiempo | creación y finalización |

Una invocación se crea antes de la llamada remota. Si el worker desaparece, queda evidencia `STARTED`. Cuando otro worker recupera el análisis después del vencimiento del lease, la transforma en:

```text
ABANDONED
errorCode = WORKER_LEASE_LOST
retryable = true
```

La semántica general es *at-least-once*.

## 10. Errores de adapters

Ninguna excepción `com.openai.*` o `com.google.genai.*` atraviesa su boundary.

`GenerativeAiException` conserva internamente la causa y publica únicamente:

- tipo agnóstico;
- provider;
- flag `retryable`;
- mensaje controlado.

Mapping vigente:

| Condición | Tipo | Retryable |
|---|---|---:|
| HTTP `401/403` | `AUTHENTICATION` | No |
| HTTP `400/404/422` | `INVALID_REQUEST` | No |
| HTTP `408` | `TIMEOUT` | Sí |
| HTTP `429` | `RATE_LIMITED` | Sí |
| Timeout de transporte | `TIMEOUT` | Sí |
| Error de conexión | `PROVIDER_UNAVAILABLE` | Sí |
| HTTP `5xx` | `PROVIDER_UNAVAILABLE` | Sí |
| JSON/schema/respuesta SDK inválida | `INVALID_RESPONSE` | No |
| Otro error del SDK | `PROVIDER_ERROR` | No |

No se propagan mensajes remotos, headers, prompts, salidas, credenciales ni contenido del usuario en las excepciones públicas.

## 11. Retry y timeout

### 11.1 SDK y transporte

Ambos clientes se construyen sin retries automáticos:

```text
maxRetries(0)
HttpRetryOptions.attempts(1)
```

Las pruebas verifican un solo request HTTP por cada `generate()`, incluso ante `429`, `5xx` o timeout.

Esto es esencial: cada retry debe ser visible como una nueva `AiInvocation` controlada por la aplicación.

### 11.2 Política de aplicación

La configuración por defecto actual es:

| Parámetro | Default |
|---|---:|
| `ANALYSIS_MAX_ATTEMPTS` | `2` |
| `ANALYSIS_RETRY_DELAY` | `5s` |
| `ANALYSIS_LEASE_DURATION` | `120s` |
| `ANALYSIS_POLL_INTERVAL` | `2s` |
| `OPENAI_TIMEOUT` en DEV | `60s` |
| `GEMINI_TIMEOUT` en DEV | `60s` |

No existe circuit breaker actualmente.

### 11.3 Invariante timeout/lease

Sin heartbeat, el lease debe superar el timeout del proveedor más un margen operativo:

```text
leaseDuration >= providerTimeout + margin
```

Los defaults DEV cumplen la relación. Gemini valida al arrancar que el lease sea estrictamente mayor
que su timeout; OpenAI conserva esta validación cruzada como pendiente.

Debe añadirse una validación cruzada de configuración antes de producción.

## 12. Configuración y arranque

### 12.1 Proveedor

| Variable | DEV | TEST | PROD |
|---|---|---|---|
| `IDENTIPAT_AI_ENABLED` | `true` | `false` | obligatoria |
| `IDENTIPAT_AI_PROVIDER` | `openai` | `openai` | obligatoria |
| `OPENAI_API_KEY` | requerida si IA activa | vacía con IA deshabilitada | requerida con OpenAI activo |
| `OPENAI_MODEL` | `gpt-5-mini` por defecto | vacío | requerida con OpenAI activo |
| `OPENAI_TIMEOUT` | `60s` | `60s` | requerida con OpenAI activo |
| `GOOGLE_APPLICATION_CREDENTIALS` | ruta ADC privada | no usada | ADC administrada por ambiente |
| `GOOGLE_CLOUD_PROJECT` | `identipat` | vacío | requerida con Gemini activo |
| `GOOGLE_CLOUD_LOCATION` | `global` | vacío | requerida con Gemini activo |
| `GEMINI_MODEL` | `gemini-3.8-flash` | vacío | requerida con Gemini activo |
| `GEMINI_API_VERSION` | `v1` | `v1` | requerida, solo `v1` |
| `GEMINI_TIMEOUT` | `60s` | `60s` | requerida con Gemini activo |

Con IA deshabilitada no se crea cliente ni provider, y el backend puede arrancar sin key/model.

Con IA habilitada, un provider desconocido o su configuración incompleta impiden el arranque. El
provider inactivo no exige sus credenciales. No se llama al proveedor durante startup.

### 12.2 Worker y análisis

| Variable | Default general |
|---|---:|
| `ANALYSIS_TEXT_MIN_LENGTH` | `20` |
| `ANALYSIS_TEXT_MAX_LENGTH` | `20000` |
| `ANALYSIS_WORKER_ENABLED` | `true` |
| `ANALYSIS_POLL_INTERVAL` | `2s` |
| `ANALYSIS_WORKER_THREADS` | `2` |
| `ANALYSIS_WORKER_QUEUE_CAPACITY` | `2` |
| `ANALYSIS_LEASE_DURATION` | `120s` |
| `ANALYSIS_MAX_ATTEMPTS` | `2` |
| `ANALYSIS_RETRY_DELAY` | `5s` |
| `ANALYSIS_AI_MAX_OUTPUT_TOKENS` | `6000` |

En TEST el worker está deshabilitado para que las pruebas controlen explícitamente el procesamiento.

### 12.3 Inicio local

El inicio recomendado de DEV desde la raíz es:

```powershell
.\scripts\run-backend-dev-ps51.ps1
```

El script carga el `.env` local, fuerza `dev`, valida la credencial del provider seleccionado y
asegura PostgreSQL antes de iniciar Spring Boot.

## 13. Pruebas y validación real

### 13.1 Suite automatizada

Las pruebas normales no llaman a Internet. Utilizan provider simulado o servidor HTTP local y cubren:

- request de Responses API;
- roles y mensajes;
- schema estricto;
- opciones;
- extracción de output;
- raw response, usage y metadata;
- errores HTTP;
- timeout;
- salida inválida;
- ausencia de retries ocultos;
- flujo asíncrono completo;
- persistencia de invocaciones;
- retry y agotamiento de intentos;
- lease vencido y recuperación;
- claims concurrentes con `SKIP LOCKED`;
- atomicidad entre resultado y `COMPLETED`;
- aislamiento por sesión;
- no exposición de metadata del proveedor.

### 13.2 Smoke técnico

`OpenAiLiveSmokeIT` usa `provider-smoke-test/1.0` y datos sintéticos. Termina en `IT`, por lo que Surefire no lo ejecuta con `mvn test`.

PowerShell:

```powershell
$env:OPENAI_API_KEY="..."
$env:OPENAI_MODEL="..."
$env:OPENAI_TIMEOUT="60s"
cd backend
.\mvnw.cmd "-Dtest=OpenAiLiveSmokeIT" test
```

Git Bash:

```bash
export OPENAI_API_KEY="..."
export OPENAI_MODEL="..."
export OPENAI_TIMEOUT="60s"
cd backend
./mvnw -Dtest=OpenAiLiveSmokeIT test
```

No imprime key, prompt ni response.

Gemini ofrece dos smokes en `GeminiLiveSmokeIT`: contrato técnico y prompt jurídico sintético V3.
No forman parte de `mvn test` y requieren un opt-in adicional:

```powershell
$env:RUN_GEMINI_LIVE_SMOKE="true"
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\ruta-privada\service-account.json"
$env:GOOGLE_CLOUD_PROJECT="identipat"
$env:GOOGLE_CLOUD_LOCATION="global"
$env:GEMINI_MODEL="gemini-3.8-flash"
$env:GEMINI_API_VERSION="v1"
$env:GEMINI_TIMEOUT="60s"
cd backend
.\mvnw.cmd "-Dtest=GeminiLiveSmokeIT" test
```

Sin el flag, ambos casos se omiten y no realizan tráfico ni consumo facturable.

Para diagnosticar localmente una terminación incompleta sin exponer contenido, habilitar además
`GEMINI_SAFE_DIAGNOSTICS=true`. El diagnóstico registra únicamente el máximo de salida configurado,
`finishReason`, `blockReason`, cantidades de candidatos y parts, indicadores de bloqueo y contadores
de tokens. No registra prompts, parts, respuestas, credenciales ni tokens OAuth.

Reejecución exclusiva del smoke técnico:

```powershell
$env:GEMINI_SAFE_DIAGNOSTICS="true"
cd backend
.\mvnw.cmd "-Dtest=GeminiLiveSmokeIT#callsVertexAiWithSyntheticTechnicalSchema" test
```

El smoke técnico configura `maxOutputTokens=200`; el jurídico configura `6000`. Un
`finishReason=MAX_TOKENS` demostraría que la terminación fue por presupuesto, pero el valor de
`thoughtTokens` y su relación con el límite deben observarse antes de atribuirlo a razonamiento
interno. La respuesta sigue rechazándose: el diagnóstico no relaja la validación.

### 13.3 Validación end-to-end

El flujo real de texto contra OpenAI fue validado hasta:

```text
RECEIVED → ANALYZING → COMPLETED
```

con una `AiInvocation(SUCCEEDED)` y un `AnalysisResult` persistido.

Gemini está validado offline contra un servidor local. En la validación manual del 2026-11-22, el smoke
jurídico sintético V3 pasó. Dos ejecuciones técnicas anteriores fueron rechazadas por un finish reason
no conservado; una ejecución posterior pasó con `STOP`, un candidato, un part y sin bloqueos. Esa
ejecución informó 180 tokens de prompt, 32 de candidato, 96 de thoughts y 308 totales con
`maxOutputTokens=200`; no demuestra la causa de los rechazos antiguos ni justifica aumentar el
límite. El E2E integrado Angular → Java → PostgreSQL → worker → Gemini se reportó aprobado el
2026-11-23; los metadatos de persistencia y el alcance de la evidencia de interfaz constan en el
reporte de validación. Ninguna de estas pruebas equivale a aceptación jurídica ni a autorización de
producción.

## 14. Seguridad y privacidad

- La API key proviene únicamente de configuración externa.
- `.env.example` no contiene secretos reales.
- PROD no define fallback para API key, modelo o timeout.
- La solicitud usa `store(false)`.
- Prompts y respuestas no deben aparecer en logs.
- Los mensajes de error están saneados.
- Cookies, tokens STANDARD y PII no atraviesan el provider.
- El adapter recibe solo el prompt renderizado y el contrato técnico.
- Cuando Gemini está activo, el contenido se transmite a Google Cloud. Antes de producción deben
  aprobarse IAM, cuota, costos, privacidad, región y residencia; `global` es una referencia técnica,
  no una decisión institucional de residencia.

La base sí conserva durante desarrollo:

- texto original y procesado;
- prompt renderizado;
- respuesta cruda y estructurada;
- resultado canónico;
- usage y metadata.

Por tanto, PostgreSQL puede contener descripciones confidenciales de invenciones y copias redundantes del contenido. F2.1 debe definir retención, acceso, cifrado, minimización, anonimización y borrado.

Mientras no exista una política aprobada, no deben utilizarse casos reales confidenciales o reservados para pruebas con un proveedor externo.

## 15. Limitaciones y mejoras identificadas

### 15.1 Respuestas `incomplete`

El adapter intenta primero extraer y validar `output_text`. Solo después construye `GenerativeAiResponse` y obtiene metadata completa.

Si OpenAI devuelve una respuesta `incomplete` sin un único output válido, se produce `INVALID_RESPONSE` antes de conservar:

- modelo efectivo;
- provider request ID;
- usage y tokens consumidos;
- finish reason e `incomplete_details`;
- raw response;
- latencia.

Debe refactorizarse el adapter para capturar primero metadata técnica segura y adjuntarla también al camino de error.

### 15.2 Lease y concurrencia

- OpenAI aún no valida en startup `leaseDuration > providerTimeout`; Gemini valida la desigualdad
  estricta, pero el margen operativo concreto sigue siendo una decisión de despliegue.
- Cerca del vencimiento existe una carrera residual entre el worker que termina y otro que reclama.
- Conviene arbitrar la transición terminal mediante un `UPDATE` condicionado por owner/vigencia del lease o locking optimista equivalente.

### 15.3 Observabilidad

- Los 500 inesperados necesitan logging estructurado interno y correlation ID.
- No deben incluir contenido sensible.
- Las métricas deben distinguir intentos técnicos, análisis funcionales, retry, latencia, tokens y errores por tipo.

### 15.4 Contrato jurídico

F1.4 mantiene prompt/schema 0.1/1.0 para historia y utiliza 0.2/2.0 en análisis nuevos. La revisión
semántica del golden set por especialistas sigue siendo necesaria antes de piloto.

## 16. Extensión futura

### 16.1 Otro proveedor

Para agregar un proveedor:

1. implementar `GenerativeAiProvider`;
2. traducir mensajes, schema y opciones dentro de su package;
3. devolver `GenerativeAiResponse` agnóstica;
4. mapear errores a `GenerativeAiException`;
5. evitar retries ocultos;
6. añadir configuración y pruebas de contrato.

No deben cambiar:

- controllers;
- `AnalysisWorker`;
- `AnalysisResult` por una particularidad del SDK;
- renderer;
- schemas comunes;
- modelo de persistencia.

### 16.2 RAG

RAG no modifica el provider. Un futuro `KnowledgeRetriever` obtendría contexto antes del renderer y lo entregaría como variable explícita, por ejemplo:

```text
{{ADDITIONAL_CONTEXT}}
```

El provider continuaría recibiendo únicamente un `RenderedPrompt` y un `StructuredOutputDefinition`.

RAG no está aprobado ni implementado actualmente y deberá evaluarse desde las necesidades jurídicas, de datos y trazabilidad del producto.
