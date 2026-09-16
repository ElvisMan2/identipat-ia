# Integración de IA generativa — IDENTIPAT-IA

**Estado:** infraestructura F1.2 integrada al flujo durable F1.3  
**Versión:** 1.1  
**Última actualización:** 16 de septiembre de 2026

## 1. Alcance actual

La integración de IA generativa está implementada en Java y participa en la vertical de análisis de texto.

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

No forman parte todavía de esta integración:

- diagnóstico jurídico definitivo de F1.4;
- PDF y audio;
- RAG;
- adapters distintos de OpenAI;
- circuit breaker;
- política productiva definitiva de retención y confidencialidad.

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
        ↓
OpenAiGenerativeAiProvider
        ↓
openai-java 4.63.1
        ↓
OpenAI Responses API
        ↓
validación + AnalysisResult + estado terminal
```

La interfaz permite adapters futuros:

```text
GenerativeAiProvider
├── OpenAiGenerativeAiProvider          ← actual
└── GeminiGenerativeAiProvider          ← futuro, si se aprueba
```

Solo el paquete `ai/provider/openai` y su configuración técnica conocen tipos `com.openai.*`. Controllers, servicios de análisis, sesión STANDARD, persistencia y worker utilizan únicamente modelos internos de IDENTIPAT-IA.

OpenAI es el proveedor vigente. Gemini no está implementado y no debe describirse como proveedor inicial actual.

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
└── provider-smoke-test/v1.0/
    ├── system.md
    └── user.md
```

`provider-smoke-test/1.0` es técnico y sintético. `intellectual-property-analysis/0.1` es el prompt utilizado actualmente por el worker F1.3.

El prompt funcional actual:

- solicita una evaluación preliminar de propiedad intelectual;
- exige el schema `analysis-result/1.0`;
- prohíbe presentar el resultado como decisión oficial;
- no afirma haber realizado búsquedas de antecedentes;
- no permite inventar hechos, legislación o información ausente;
- prohíbe confianza numérica;
- mantiene el disclaimer institucional fuera de `warnings`.

Todavía no constituye el diagnóstico jurídico definitivo requerido por F1.4.

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
└── provider-smoke-result/v1.0/schema.json
```

`analysis-result/1.0` es el contrato utilizado por el worker. Exige:

- `schemaVersion`;
- `summary`;
- `patentabilityAssessment`;
- `protectionOptions`;
- `observations`;
- `warnings`.

Utiliza enums cerrados, campos obligatorios y `additionalProperties: false`.

### 5.2 Registro y envío

`OutputSchemaRegistry` carga el contrato de IDENTIPAT-IA y lo entrega como `StructuredOutputDefinition` con `strict=true`.

El adapter convierte el JSON Schema campo por campo a `ResponseFormatTextJsonSchemaConfig.Schema`. No deriva un contrato alternativo desde clases del SDK.

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

## 6. Mapping a OpenAI

### 6.1 Request

- Cada `AiMessage` se convierte en `ResponseInputItem`.
- Se conservan roles `system` y `user`.
- El modelo proviene de configuración externa.
- `maxOutputTokens` y `temperature` se envían solo cuando están presentes.
- El worker F1.3 establece `maxOutputTokens` desde `ANALYSIS_AI_MAX_OUTPUT_TOKENS` y omite `temperature`.
- El adapter no simula compatibilidad de opciones no soportadas por un modelo.
- La solicitud establece explícitamente `store(false)`.

### 6.2 Extracción de contenido

El adapter recorre todos los elementos de `Response.output`, localiza bloques `output_text` dentro de mensajes y exige exactamente uno.

| Cantidad de bloques | Resultado |
|---:|---|
| `1` | Se parsea y valida. |
| `0` | `INVALID_RESPONSE`. |
| `>1` | `INVALID_RESPONSE` para evitar concatenación ambigua. |

### 6.3 Modelo y request ID

- `providerRequestId` corresponde a `Response.id`.
- El modelo efectivo se obtiene de `ResponsesModel` mediante su `Visitor`.
- Las variantes `String`, `ChatModel` y `ResponsesOnlyModel` usan su valor wire.
- Una variante desconocida solo se acepta si el JSON crudo contiene un string no vacío.
- No se reemplaza silenciosamente con el modelo configurado ni se usa `toString()`.

### 6.4 Usage

Se normalizan:

- `input_tokens`;
- `output_tokens`;
- `total_tokens`.

El objeto de usage completo se conserva como detalle del proveedor. Si OpenAI no devuelve usage, los conteos permanecen nulos.

### 6.5 Finalización y latencia

`finishReason` usa:

```text
incomplete:<incomplete_details.reason>
```

cuando ese dato existe. En otro caso utiliza el valor wire de `Response.status`; si falta, `unknown`.

La latencia se mide alrededor de la llamada síncrona mediante reloj monotónico.

## 7. Integración con AnalysisWorker

### 7.1 Preparación del intento

`AnalysisAttemptService.prepare()`:

1. verifica que el worker conserve un lease activo;
2. marca como `ABANDONED` una invocación `STARTED` obsoleta si corresponde;
3. verifica el límite de intentos;
4. carga `AnalysisInput.processedText`;
5. renderiza `intellectual-property-analysis/0.1`;
6. carga `analysis-result/1.0`;
7. construye `GenerationOptions`;
8. persiste `AiInvocation(STARTED)` antes de llamar al provider.

### 7.2 Llamada remota

La llamada:

```java
provider.generate(attempt.request())
```

ocurre fuera de una transacción de PostgreSQL. Así no se mantiene una conexión ni locks de base de datos durante la latencia del proveedor.

### 7.3 Éxito

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

### 7.4 Fallo y retry

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

## 8. Persistencia de `AiInvocation`

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

## 9. Errores del adapter

Ninguna excepción `com.openai.*` atraviesa el boundary.

`GenerativeAiException` conserva internamente la causa y publica únicamente:

- tipo agnóstico;
- provider;
- flag `retryable`;
- mensaje controlado.

Mapping vigente:

| Condición | Tipo | Retryable |
|---|---|---:|
| HTTP `401/403` | `AUTHENTICATION` | No |
| HTTP `400/404/409/422` | `INVALID_REQUEST` | No |
| HTTP `429` | `RATE_LIMITED` | Sí |
| Timeout de transporte | `TIMEOUT` | Sí |
| Error de conexión | `PROVIDER_UNAVAILABLE` | Sí |
| HTTP `5xx` | `PROVIDER_UNAVAILABLE` | Sí |
| JSON/schema/respuesta SDK inválida | `INVALID_RESPONSE` | No |
| Otro error del SDK | `PROVIDER_ERROR` | No |

No se propagan mensajes remotos, headers, prompts, salidas, credenciales ni contenido del usuario en las excepciones públicas.

## 10. Retry y timeout

### 10.1 SDK y transporte

El cliente se construye sin retries automáticos:

```text
maxRetries(0)
retryOnConnectionFailure(false)
```

Las pruebas verifican un solo request HTTP por cada `generate()`, incluso ante `429`, `5xx` o timeout.

Esto es esencial: cada retry debe ser visible como una nueva `AiInvocation` controlada por la aplicación.

### 10.2 Política de aplicación

La configuración por defecto actual es:

| Parámetro | Default |
|---|---:|
| `ANALYSIS_MAX_ATTEMPTS` | `2` |
| `ANALYSIS_RETRY_DELAY` | `5s` |
| `ANALYSIS_LEASE_DURATION` | `120s` |
| `ANALYSIS_POLL_INTERVAL` | `2s` |
| `OPENAI_TIMEOUT` en DEV | `60s` |

No existe circuit breaker actualmente.

### 10.3 Invariante timeout/lease

Sin heartbeat, el lease debe superar el timeout del proveedor más un margen operativo:

```text
leaseDuration >= providerTimeout + margin
```

Los defaults DEV cumplen la relación, pero el código todavía no la valida en startup. Una configuración productiva incorrecta podría permitir que otro worker reclame el análisis mientras el primero continúa esperando al LLM.

Debe añadirse una validación cruzada de configuración antes de producción.

## 11. Configuración y arranque

### 11.1 Proveedor

| Variable | DEV | TEST | PROD |
|---|---|---|---|
| `IDENTIPAT_AI_ENABLED` | `true` | `false` | obligatoria |
| `IDENTIPAT_AI_PROVIDER` | `openai` | `openai` | obligatoria |
| `OPENAI_API_KEY` | requerida si IA activa | vacía con IA deshabilitada | requerida con OpenAI activo |
| `OPENAI_MODEL` | `gpt-5-mini` por defecto | vacío | requerida con OpenAI activo |
| `OPENAI_TIMEOUT` | `60s` | `60s` | requerida con OpenAI activo |

Con IA deshabilitada no se crea cliente ni provider, y el backend puede arrancar sin key/model.

Con IA habilitada, un provider desconocido, key/model ausentes o timeout no positivo impiden el arranque. No se usa `fromEnv()` ni se llama al proveedor durante startup.

### 11.2 Worker y análisis

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
| `ANALYSIS_AI_MAX_OUTPUT_TOKENS` | `4000` |

En TEST el worker está deshabilitado para que las pruebas controlen explícitamente el procesamiento.

### 11.3 Inicio local

El inicio recomendado de DEV desde la raíz es:

```powershell
.\scripts\run-backend-dev.ps1
```

El script carga el `.env` local, fuerza el perfil `dev`, valida la API key y asegura PostgreSQL antes de iniciar Spring Boot.

## 12. Pruebas y validación real

### 12.1 Suite automatizada

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

### 12.2 Smoke técnico

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

### 12.3 Validación end-to-end

El flujo real de texto contra OpenAI fue validado hasta:

```text
RECEIVED → ANALYZING → COMPLETED
```

con una `AiInvocation(SUCCEEDED)` y un `AnalysisResult` persistido.

Esta validación confirma la integración técnica; no equivale a aceptación jurídica o funcional del diagnóstico, que corresponde a F1.4 y fases de evaluación.

## 13. Seguridad y privacidad

- La API key proviene únicamente de configuración externa.
- `.env.example` no contiene secretos reales.
- PROD no define fallback para API key, modelo o timeout.
- La solicitud usa `store(false)`.
- Prompts y respuestas no deben aparecer en logs.
- Los mensajes de error están saneados.
- Cookies, tokens STANDARD y PII no atraviesan el provider.
- El adapter recibe solo el prompt renderizado y el contrato técnico.

La base sí conserva durante desarrollo:

- texto original y procesado;
- prompt renderizado;
- respuesta cruda y estructurada;
- resultado canónico;
- usage y metadata.

Por tanto, PostgreSQL puede contener descripciones confidenciales de invenciones y copias redundantes del contenido. F2.1 debe definir retención, acceso, cifrado, minimización, anonimización y borrado.

Mientras no exista una política aprobada, no deben utilizarse casos reales confidenciales o reservados para pruebas con un proveedor externo.

## 14. Limitaciones y mejoras identificadas

### 14.1 Respuestas `incomplete`

El adapter intenta primero extraer y validar `output_text`. Solo después construye `GenerativeAiResponse` y obtiene metadata completa.

Si OpenAI devuelve una respuesta `incomplete` sin un único output válido, se produce `INVALID_RESPONSE` antes de conservar:

- modelo efectivo;
- provider request ID;
- usage y tokens consumidos;
- finish reason e `incomplete_details`;
- raw response;
- latencia.

Debe refactorizarse el adapter para capturar primero metadata técnica segura y adjuntarla también al camino de error.

### 14.2 Lease y concurrencia

- Falta validación startup de `leaseDuration > providerTimeout + margen`.
- Cerca del vencimiento existe una carrera residual entre el worker que termina y otro que reclama.
- Conviene arbitrar la transición terminal mediante un `UPDATE` condicionado por owner/vigencia del lease o locking optimista equivalente.

### 14.3 Observabilidad

- Los 500 inesperados necesitan logging estructurado interno y correlation ID.
- No deben incluir contenido sensible.
- Las métricas deben distinguir intentos técnicos, análisis funcionales, retry, latencia, tokens y errores por tipo.

### 14.4 Contrato jurídico

El prompt `0.1` es deliberadamente preliminar. F1.4 debe comenzar por evolucionar el contrato jurídico de salida y después alinear prompt, schema, DTOs y casos de prueba.

## 15. Extensión futura

### 15.1 Otro proveedor

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

### 15.2 RAG

RAG no modifica el provider. Un futuro `KnowledgeRetriever` obtendría contexto antes del renderer y lo entregaría como variable explícita, por ejemplo:

```text
{{ADDITIONAL_CONTEXT}}
```

El provider continuaría recibiendo únicamente un `RenderedPrompt` y un `StructuredOutputDefinition`.

RAG no está aprobado ni implementado actualmente y deberá evaluarse desde las necesidades jurídicas, de datos y trazabilidad del producto.
