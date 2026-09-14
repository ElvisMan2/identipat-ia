# Integración de IA generativa

## Alcance de F1.2

F1.2 incorpora infraestructura interna para invocar un LLM desde Java sin implementar todavía
`Analysis`, endpoints, persistencia de invocaciones, diagnóstico jurídico, RAG, PDF, audio ni cambios
de frontend. OpenAI es el primer adapter porque aún no existen credenciales de Gemini; no forma parte
del contrato del dominio.

```text
caso de uso futuro
        ↓
GenerativeAiProvider
        ↓
OpenAiGenerativeAiProvider
        ↓
openai-java 4.63.1
        ↓
Responses API
```

Un proveedor futuro implementará la misma interfaz:

```text
GenerativeAiProvider
├── OpenAiGenerativeAiProvider
└── GeminiGenerativeAiProvider (futuro)
```

Solo `ai/provider/openai` y su configuración técnica importan `com.openai.*`. Controllers, sesión
STANDARD, persistencia y futuros casos de uso trabajan exclusivamente con modelos IDENTIPAT.

## Contrato común

`GenerativeAiProvider` es síncrono y expone `providerId()` y `generate(request)`. No conoce
`Analysis`, usuarios, sesiones, HTTP ni PostgreSQL. F1.3 será responsable de asincronía durable,
intentos y persistencia.

`GenerativeAiRequest` contiene `RenderedPrompt`, ya renderizado; `StructuredOutputDefinition`, con el
JSON Schema maestro; y `GenerationOptions`, inicialmente `maxOutputTokens` y `temperature` opcionales.
No contiene proveedor, modelo, API key, URL, timeout ni identificadores de usuario/sesión/análisis.

`GenerativeAiResponse` separa:

- `structuredOutput`: JSON extraído y validado localmente;
- `rawProviderResponse`: representación JSON semánticamente cruda del objeto SDK, no bytes HTTP;
- `ProviderExecution`: proveedor, modelo efectivo e ID de respuesta OpenAI;
- `TokenUsage`: conteos normalizados y detalle del proveedor;
- `finishReason` y `latencyMs`.

No es un `AnalysisResult` y F1.2 no guarda estos datos.

## Prompts versionados

Los prompts viven en `backend/src/main/resources/prompts/<promptId>/v<version>/` con archivos
`system.md` y `user.md` separados. F1.2 contiene únicamente `provider-smoke-test/v1.0`, técnico y
sintético.

`PromptRegistry` carga recursos UTF-8 desde classpath, sin base de datos ni hot reload. IDs y
versiones aceptan solo segmentos seguros, por lo que no pueden atravesar rutas.

`PromptRenderer` sustituye placeholders `{{VARIABLE}}`. Los nombres admiten mayúsculas, números y
`_`, deben declararse explícitamente y no puede quedar ninguno sin resolver. No evalúa expresiones ni
código; las variables adicionales no utilizadas también producen error.

Antes de calcular hashes se normalizan `CRLF` y `CR` a `LF`. El formato canónico es:

```text
[SYSTEM]
contenido system

[USER]
contenido user
```

`templateHash` es SHA-256 lowercase del snapshot de las plantillas; `renderedHash` es el hash del
snapshot que contiene exactamente los mensajes normalizados enviados. `renderedSnapshot` conserva
ese snapshot determinista, sin timestamps.

## Schemas y structured output

Los schemas viven en `backend/src/main/resources/ai-schemas/<schemaId>/v<version>/schema.json`.
F1.2 contiene únicamente `provider-smoke-result/v1.0`, con todos sus campos requeridos y
`additionalProperties: false`.

`OutputSchemaRegistry` carga el contrato IDENTIPAT y lo marca `strict=true`. El adapter lo convierte
campo por campo a `ResponseFormatTextJsonSchemaConfig.Schema`; no genera otro contrato desde una
clase del SDK. La petición usa `text.format.type=json_schema` de Responses API.

Después de extraer el texto de salida, Java lo parsea como JSON y `StructuredOutputValidator` lo
valida contra el mismo schema con `com.networknt:json-schema-validator:1.0.88` (draft 2020-12). JSON
inválido o cualquier violación se traduce a `INVALID_RESPONSE`; el body no aparece en mensajes ni
logs.

## Mapping OpenAI

- Cada `AiMessage` se convierte en un `ResponseInputItem` y conserva el rol `system` o `user`.
- El modelo configurado se envía mediante `ResponseCreateParams.model`.
- `maxOutputTokens` y `temperature` solo se envían cuando están presentes. El llamador omite una
  opción que el modelo elegido no soporte; el adapter no simula compatibilidad.
- `store=false` evita solicitar almacenamiento de la respuesta por esta integración.
- La extracción recorre todos los items de `Response.output`, sin depender de su índice, y localiza
  los bloques `output_text` dentro de mensajes. Como F1.2 espera una única salida estructurada,
  exactamente un bloque es válido; cero o múltiples bloques se consideran `INVALID_RESPONSE` para
  evitar una concatenación ambigua.
- `providerRequestId` corresponde a `Response.id`. El modelo efectivo se extrae de
  `ResponsesModel` mediante su `Visitor`: las variantes `String`, `ChatModel` y
  `ResponsesOnlyModel` entregan su valor wire; `unknown` solo se acepta si el JSON crudo contiene
  un string no vacío. Nunca se sustituye por el modelo configurado ni se usa `toString()`.
- Usage usa `input_tokens`, `output_tokens` y `total_tokens`; el objeto usage completo queda en
  `providerDetails`.
- `finishReason` es `incomplete:<incomplete_details.reason>` cuando ese motivo existe. En otro caso
  es el valor wire de `Response.status`; si no llega, es `unknown`.
- La latencia se mide alrededor de la llamada síncrona con reloj monotónico.

## Errores

Ninguna excepción `com.openai.*` atraviesa el adapter. `GenerativeAiException` conserva internamente
la causa y publica solo tipo, provider, retryable y un mensaje controlado:

| Condición | Tipo | Retryable |
| --- | --- | --- |
| HTTP 401/403 | `AUTHENTICATION` | no |
| HTTP 400/404/409/422 | `INVALID_REQUEST` | no |
| HTTP 429 | `RATE_LIMITED` | sí |
| Timeout de transporte | `TIMEOUT` | sí |
| Error de conexión o HTTP 5xx | `PROVIDER_UNAVAILABLE` | sí |
| JSON/schema/respuesta SDK inválida | `INVALID_RESPONSE` | no |
| Otro error SDK | `PROVIDER_ERROR` | no |

No se propagan mensajes remotos, headers, prompts, salidas, credenciales ni contenido de usuario.

## Retry y timeout

El cliente se construye con `maxRetries(0)`. El transporte OkHttp del SDK 4.63.1 también usa
`retryOnConnectionFailure(false)`. Las pruebas demuestran un solo request HTTP por cada `generate()`,
incluso ante 429/5xx/timeout. F1.3 será dueña de una política de retry trazable donde cada intento
corresponda a una futura `AiInvocation`.

`OPENAI_TIMEOUT` configura el timeout total. DEV y TEST usan provisionalmente `60s`; PROD debe
proporcionarlo. No hay circuit breaker en F1.2.

## Configuración y arranque

| Variable | DEV/TEST | PROD |
| --- | --- | --- |
| `IDENTIPAT_AI_ENABLED` | `false` | obligatoria |
| `IDENTIPAT_AI_PROVIDER` | `openai` | obligatoria |
| `OPENAI_API_KEY` | requerida solo al habilitar | requerida con OpenAI habilitado |
| `OPENAI_MODEL` | requerida solo al habilitar | requerida con OpenAI habilitado |
| `OPENAI_TIMEOUT` | `60s` | requerida con OpenAI habilitado |

Con IA deshabilitada no se crea el cliente ni el provider y el backend arranca sin key/model. Con IA
habilitada, un provider desconocido, credencial/modelo ausentes o timeout no positivo impiden el
arranque. No se usa `fromEnv()`, no hay modelo por defecto y no se realizan llamadas en startup.

## Pruebas y smoke real

La suite normal usa un servidor HTTP local y nunca llama a Internet. Cubre request Responses,
mensajes, schema/strict, opciones, output/raw/usage/metadata, errores HTTP, timeout, salida inválida y
ausencia de retries ocultos.

`OpenAiLiveSmokeIT` termina en `IT`, por lo que Surefire no lo ejecuta con `mvn test`. Solo usa texto
sintético y no imprime key, prompt ni response. Ejecución manual:

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

## Seguridad y extensión futura

La API key solo llega desde configuración externa. `.env.example` contiene placeholders. La capa no
registra prompts, snapshots, entrada, structured output, raw response, cookies, tokens STANDARD ni
PII. Los errores tienen mensajes saneados y conservan la causa solo para manejo interno.

Para incorporar otro proveedor se crea un adapter de `GenerativeAiProvider` que traduzca
mensajes/schema/opciones en su package y convierta errores a `GenerativeAiException`. Casos de uso,
renderer, schemas y modelos comunes no cambian.

RAG tampoco modifica el provider. Un futuro `KnowledgeRetriever` obtiene contexto antes del renderer
y lo entrega como variable explícita, por ejemplo `{{ADDITIONAL_CONTEXT}}`; el provider continúa
recibiendo únicamente un `RenderedPrompt`.
