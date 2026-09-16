# Contrato de análisis — IDENTIPAT-IA

**Estado:** contratos de sesión/consentimiento implementados en F1.1 y contrato de análisis de texto implementado en F1.3  
**Versión:** 1.1  
**Última actualización:** 16 de septiembre de 2026  
**Ámbito:** contratos HTTP consumidos por Angular exclusivamente a través de Java/Spring Boot y contrato canónico `AnalysisResult`.

## 1. Propósito

Este documento define el contrato externo vigente para:

- obtener protección CSRF;
- crear, consultar y cerrar una sesión temporal `STANDARD`;
- registrar consentimiento;
- crear un análisis de texto;
- consultar su estado o resultado;
- representar el resultado canónico validado por Java.

También establece la forma en que PDF y audio deberán incorporarse posteriormente sin crear un dominio paralelo.

Todas las rutas son relativas al context path:

```text
/identipat-ia
```

## 2. Principios del contrato

- La cookie de sesión `STANDARD` es una capacidad temporal; no es JWT, login ni autenticación administrativa.
- DNI/CE identifica o deduplica un registro, pero no verifica criptográfica o presencialmente la identidad real de quien usa el navegador.
- La creación de sesión es independiente de `POST /users/identify` y `POST /users`.
- Java vuelve a resolver el usuario mediante DNI/CE y nunca acepta un `userId` enviado por Angular para crear la sesión.
- Una sesión solo puede crear análisis después de aceptar el consentimiento vigente.
- Consentimiento y disclaimer institucional son conceptos separados.
- Un análisis es asíncrono: el `POST` persiste y devuelve `202`; no espera al LLM.
- El UUID del análisis identifica el recurso, pero no lo autoriza. Java también verifica que pertenezca a la sesión vigente.
- Las respuestas públicas no incluyen PII, token de sesión, IDs internos de usuario/sesión, prompts, respuestas crudas, parámetros del modelo ni intentos técnicos.
- Los contratos rechazan campos desconocidos en las requests de sesión y consentimiento.
- El resultado generado es orientativo; no constituye una decisión oficial ni una probabilidad calibrada.

## 3. Seguridad transversal

### 3.1 Cookies y credenciales

El navegador debe incluir credenciales en las solicitudes que utilicen la sesión STANDARD. En producción, la cookie de sesión es `HttpOnly`, `Secure`, `SameSite=Lax` y está limitada al path `/identipat-ia`.

Angular no lee el token de sesión. Java lo obtiene exclusivamente desde la cookie `IDENTIPAT_STANDARD_SESSION`.

### 3.2 CSRF

F1.1 implementa `CookieCsrfTokenRepository` con el patrón SPA de Spring Security 6.2.

Flujo:

1. Angular llama a `GET /standard-session/csrf`.
2. El backend emite la cookie `XSRF-TOKEN`.
3. Angular envía su valor en el header `X-XSRF-TOKEN` para las mutaciones protegidas.

Actualmente requieren CSRF:

- `POST /standard-sessions`;
- `POST /standard-session/consent`;
- `DELETE /standard-session`;
- `POST /analyses/text`.

La ausencia o discordancia del token responde `403 Forbidden`. `SameSite` y CORS son defensas complementarias, no sustitutos de CSRF.

Las futuras mutaciones PDF/audio deberán quedar igualmente protegidas. Se recomienda una regla general para las mutaciones STANDARD bajo `/analyses/**`.

### 3.3 Aislamiento por sesión

Para consultar un análisis se exige:

```text
cookie STANDARD válida
        +
analysis.session_id = currentSession.session_id
```

Conocer un `analysisId` ajeno no permite recuperarlo. El backend responde como no encontrado y no revela que el recurso pertenece a otra sesión.

## 4. Envelope de errores

Los errores de negocio basados en `ApiException` tienen esta forma:

```json
{
  "timestamp": "2030-01-15T14:10:00",
  "status": 409,
  "error": "Conflict",
  "code": "CONSENT_REQUIRED",
  "message": "Current consent must be accepted before creating an analysis"
}
```

Un body JSON malformado responde:

```json
{
  "timestamp": "2030-01-15T14:10:00",
  "status": 400,
  "error": "Bad Request",
  "code": "INVALID_REQUEST",
  "message": "Request body is invalid"
}
```

Los errores de validación de campos utilizan actualmente un envelope distinto con `details`. Esta heterogeneidad pertenece al contrato vigente y puede unificarse en una evolución futura versionada.

Los errores inesperados devuelven un mensaje genérico y no exponen stack traces. Debe añadirse logging estructurado interno con correlation ID sin registrar PII, cookies, prompts ni textos de consulta.

## 5. CSRF

### 5.1 Obtener token

```http
GET /standard-session/csrf
```

Respuesta `200 OK`:

```json
{
  "token": "<csrf-token>",
  "headerName": "X-XSRF-TOKEN"
}
```

También se emite la cookie `XSRF-TOKEN`. El valor no autentica al usuario; únicamente protege las mutaciones frente a CSRF.

## 6. Sesión temporal STANDARD

### 6.1 Crear sesión

```http
POST /standard-sessions
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>
```

Responsabilidad: crear una sesión temporal para un usuario `STANDARD` activo ya reconocido o recién registrado. No sustituye el reconocimiento/registro y no devuelve datos personales.

Request:

```json
{
  "doi": "DOCUMENTO_DE_EJEMPLO",
  "doiType": "DNI"
}
```

Solo se admiten `doi` y `doiType`, ambos no vacíos. Un campo adicional provoca `INVALID_REQUEST`.

Respuesta `201 Created`:

```http
Set-Cookie: IDENTIPAT_STANDARD_SESSION=<token-opaco>; Path=/identipat-ia; HttpOnly; SameSite=Lax
```

```json
{
  "status": "ACTIVE",
  "expiresAt": "2030-01-15T14:30:00Z",
  "absoluteExpiresAt": "2030-01-15T22:00:00Z",
  "consentRequired": true,
  "requiredConsentVersion": "personal-data/1.0"
}
```

El token no aparece en JSON ni en la URL. En producción la cookie agrega `Secure`; su duración no supera la expiración absoluta.

Si el navegador presenta una sesión activa previa, el backend puede cerrarla por rotación antes de emitir la nueva. Una sesión en otro navegador no se invalida por el solo conocimiento del DNI/CE.

Errores:

| HTTP | Código | Significado |
|---:|---|---|
| `400` | `INVALID_REQUEST` | Body inválido, campos desconocidos o validación de request. |
| `400` | `INVALID_DOCUMENT` | DNI/CE no cumple la validación de forma. |
| `403` | — | CSRF ausente o inválido. |
| `409` | `STANDARD_REGISTRATION_REQUIRED` | No existe un `STANDARD` activo con ese documento. |
| `409` | `STANDARD_SESSION_NOT_AVAILABLE` | El registro existe, pero no puede iniciar la experiencia. |

### 6.2 Consultar sesión actual

```http
GET /standard-session
Cookie: IDENTIPAT_STANDARD_SESSION=<token-opaco>
```

Respuesta `200 OK`: mismo envelope `StandardSessionResponse` mostrado en la creación.

La consulta renueva únicamente el límite de inactividad; nunca extiende el límite absoluto.

Una cookie ausente, inválida, cerrada o expirada responde:

| HTTP | Código |
|---:|---|
| `401` | `STANDARD_SESSION_REQUIRED` |

El backend ordena limpiar la cookie cuando corresponde y no revela si existió una sesión anterior.

### 6.3 Cerrar sesión

```http
DELETE /standard-session
X-XSRF-TOKEN: <csrf-token>
Cookie: IDENTIPAT_STANDARD_SESSION=<token-opaco>
```

Respuesta:

```http
204 No Content
```

El servidor revoca la sesión y elimina la cookie.

| HTTP | Código/causa |
|---:|---|
| `401` | `STANDARD_SESSION_REQUIRED` |
| `403` | CSRF ausente o inválido |

## 7. Consentimiento

### 7.1 Registrar decisión

```http
POST /standard-session/consent
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>
Cookie: IDENTIPAT_STANDARD_SESSION=<token-opaco>
```

La sesión se obtiene exclusivamente de la cookie. El cliente no envía `sessionId`, `userId` ni hash documental.

Request:

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "ACCEPTED"
}
```

Solo se admiten esos dos campos, ambos no vacíos.

Respuesta `200 OK` para aceptación:

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "ACCEPTED",
  "decidedAt": "2030-01-15T14:05:00Z",
  "sessionStatus": "ACTIVE",
  "consentRequired": false
}
```

Para `REJECTED`, el backend persiste la decisión, cierra la sesión y elimina la cookie. La respuesta mantiene `200 OK`, con:

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "REJECTED",
  "decidedAt": "2030-01-15T14:05:00Z",
  "sessionStatus": "CLOSED",
  "consentRequired": true
}
```

El servidor determina el hash del documento vigente y lo guarda internamente. No se registran por este contrato IP, geolocalización, huella del dispositivo ni user-agent.

Una repetición con la misma decisión puede responder de forma idempotente. Una decisión distinta para la misma sesión/versión produce conflicto.

Errores:

| HTTP | Código | Significado |
|---:|---|---|
| `400` | `INVALID_REQUEST` | Body inválido o campos desconocidos. |
| `400` | `INVALID_CONSENT_DECISION` | La decisión no es `ACCEPTED` ni `REJECTED`. |
| `401` | `STANDARD_SESSION_REQUIRED` | No existe sesión activa válida. |
| `403` | — | CSRF ausente o inválido. |
| `409` | `CONSENT_VERSION_OUTDATED` | La pantalla utiliza una versión que ya no es vigente. |
| `409` | `CONSENT_ALREADY_DECIDED` | Existe una decisión previa diferente. |

## 8. Análisis por texto

### 8.1 Crear análisis

```http
POST /analyses/text
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>
Cookie: IDENTIPAT_STANDARD_SESSION=<token-opaco>
```

Precondiciones:

- sesión `STANDARD` activa;
- usuario asociado de tipo `STANDARD` y activo;
- consentimiento `ACCEPTED` para la versión y hash vigentes;
- proveedor de IA habilitado y disponible;
- descripción dentro de los límites configurados.

Request:

```json
{
  "description": "Sistema modular que ajusta el riego según mediciones del suelo."
}
```

El cliente no aporta `userId`, `sessionId`, `consentEventId`, `analysisId`, proveedor, modelo, prompt ni estado.

El backend:

1. resuelve y renueva la sesión;
2. verifica el consentimiento vigente;
3. comprueba la disponibilidad de IA;
4. normaliza técnicamente la descripción;
5. valida su longitud por code points;
6. persiste `Analysis(RECEIVED)` y `AnalysisInput(TEXT)`;
7. responde sin llamar al LLM en el hilo HTTP.

Respuesta `202 Accepted`:

```http
Location: /identipat-ia/analyses/6e3e61f1-0bae-40bc-8c6f-4511b0bb7011
Retry-After: <segundos-configurados-de-polling>
```

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "status": "RECEIVED",
  "createdAt": "2030-01-15T14:10:00Z"
}
```

El frontend debe consultar la URL de `Location` y respetar `Retry-After` como referencia mínima para el polling.

Errores:

| HTTP | Código | Significado |
|---:|---|---|
| `400` | `INVALID_REQUEST` | JSON malformado. |
| `401` | `STANDARD_SESSION_REQUIRED` | Falta sesión activa o la cookie es inválida. |
| `403` | — | CSRF ausente o inválido. |
| `409` | `CONSENT_REQUIRED` | No existe aceptación para versión y hash vigentes. |
| `422` | `INVALID_ANALYSIS_DESCRIPTION` | Descripción normalizada fuera de límites. |
| `503` | `ANALYSIS_AI_UNAVAILABLE` | IA deshabilitada o sin provider disponible; no se crea el análisis. |

### 8.2 Consultar estado o resultado

```http
GET /analyses/{analysisId}
Cookie: IDENTIPAT_STANDARD_SESSION=<token-opaco>
```

No requiere CSRF porque es una lectura, pero sí una sesión activa y pertenencia a la misma `session_id`.

| HTTP | Código | Significado |
|---:|---|---|
| `401` | `STANDARD_SESSION_REQUIRED` | Sesión ausente, inválida o expirada. |
| `404` | `ANALYSIS_NOT_FOUND` | UUID inexistente o no perteneciente a la sesión actual. |

#### Respuesta en procesamiento

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "inputType": "TEXT",
  "status": "ANALYZING",
  "createdAt": "2030-01-15T14:10:00Z",
  "startedAt": "2030-01-15T14:10:02Z",
  "completedAt": null,
  "failedAt": null,
  "result": null,
  "failure": null
}
```

#### Respuesta completada

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "inputType": "TEXT",
  "status": "COMPLETED",
  "createdAt": "2030-01-15T14:10:00Z",
  "startedAt": "2030-01-15T14:10:02Z",
  "completedAt": "2030-01-15T14:10:07Z",
  "failedAt": null,
  "result": {
    "schemaVersion": "analysis-result/1.0",
    "summary": "La propuesta describe un sistema técnico de riego modular.",
    "patentabilityAssessment": {
      "outcome": "POTENTIALLY_PATENTABLE",
      "rationale": "La información describe una posible solución técnica, sujeta a evaluación posterior."
    },
    "protectionOptions": [
      {
        "type": "INVENTION_PATENT",
        "applicability": "POSSIBLE",
        "rationale": "La descripción plantea una solución técnica que requiere evaluación especializada."
      }
    ],
    "observations": [
      "Conviene documentar componentes, funcionamiento y diferencias frente a alternativas conocidas."
    ],
    "warnings": [
      "La descripción no detalla el mecanismo de ajuste ni los componentes de medición."
    ]
  },
  "failure": null
}
```

El ejemplo es representativo del contrato, no un dictamen jurídico. No contiene porcentaje de confianza porque la salida del modelo no se trata como probabilidad calibrada.

#### Respuesta con fallo terminal

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "inputType": "TEXT",
  "status": "FAILED",
  "createdAt": "2030-01-15T14:10:00Z",
  "startedAt": "2030-01-15T14:10:02Z",
  "completedAt": null,
  "failedAt": "2030-01-15T14:10:12Z",
  "result": null,
  "failure": {
    "code": "ANALYSIS_TEMPORARILY_UNAVAILABLE",
    "message": "No fue posible completar el análisis en este momento."
  }
}
```

Los detalles técnicos quedan restringidos en `AiInvocation`. El cliente recibe uno de los códigos públicos controlados:

| Código público | Casos internos |
|---|---|
| `ANALYSIS_TEMPORARILY_UNAVAILABLE` | Timeout, rate limit, proveedor no disponible o error del proveedor. |
| `ANALYSIS_INVALID_RESULT` | El proveedor devolvió un resultado que no pudo validarse. |
| `ANALYSIS_CONFIGURATION_ERROR` | Autenticación o request inválido hacia el proveedor. |
| `ANALYSIS_INTERNAL_ERROR` | Error del worker o código interno no reconocido. |

## 9. Contrato `AnalysisResult`

`AnalysisResult` es el resultado funcional aceptado por Java y persistido en `analysis_results.result_json`. No es la respuesta cruda del proveedor.

### 9.1 Reglas del schema vigente

Identificador:

```text
analysis-result/1.0
```

El JSON Schema establece:

- objeto raíz;
- `additionalProperties: false`;
- todos los campos del envelope obligatorios;
- enums cerrados;
- objetos internos sin propiedades adicionales.

Java vuelve a aplicar invariantes:

- `schemaVersion` debe coincidir exactamente con `analysis-result/1.0`;
- `summary` y los textos de rationale no pueden estar vacíos;
- las listas no pueden ser nulas ni contener valores nulos;
- `observations` y `warnings` no pueden contener textos vacíos;
- enums y objetos obligatorios no pueden ser nulos;
- las listas se conservan como copias inmutables.

### 9.2 Envelope

| Campo | Tipo | Regla |
|---|---|---|
| `schemaVersion` | string | Obligatorio; valor único `analysis-result/1.0`. |
| `summary` | string | Obligatorio y no vacío según Java. |
| `patentabilityAssessment` | object | Obligatorio; contiene `outcome` y `rationale`. |
| `protectionOptions` | array | Obligatorio; puede estar vacío. |
| `observations` | array de strings | Obligatorio; puede estar vacío. |
| `warnings` | array de strings | Obligatorio; puede estar vacío. |

### 9.3 Patentabilidad

```json
{
  "outcome": "POTENTIALLY_PATENTABLE",
  "rationale": "Explicación orientativa no vacía."
}
```

Valores admitidos de `outcome`:

- `POTENTIALLY_PATENTABLE`;
- `POTENTIALLY_NOT_PATENTABLE`;
- `INSUFFICIENT_INFORMATION`.

### 9.4 Opciones de protección

Cada elemento contiene exactamente:

```json
{
  "type": "UTILITY_MODEL",
  "applicability": "POSSIBLE",
  "rationale": "Explicación orientativa no vacía."
}
```

Valores de `type`:

- `INVENTION_PATENT`;
- `UTILITY_MODEL`;
- `INDUSTRIAL_DESIGN`;
- `DISTINCTIVE_SIGN`;
- `COPYRIGHT`;
- `OTHER`.

Valores de `applicability`:

- `LIKELY`;
- `POSSIBLE`;
- `UNLIKELY`.

### 9.5 Observaciones, advertencias y disclaimer

`observations[]` contiene hallazgos o recomendaciones derivados del análisis.

`warnings[]` contiene advertencias derivadas de la entrada o del diagnóstico. No debe incluir por defecto el disclaimer institucional.

El disclaimer:

- no depende de OpenAI, Gemini ni otro proveedor;
- no es texto libre generado por el modelo;
- no se persiste por defecto como `warning`;
- será contenido institucional controlado y versionado en F1.7.

### 9.6 Versionado

Los resultados históricos conservan su `schemaVersion`; no se sobrescriben para aparentar que utilizaron una versión posterior.

Aunque el diseño inicial preveía extensiones compatibles dentro de `1.x`, el schema actual usa `additionalProperties: false`. Por tanto, cualquier campo nuevo exige publicar y consumir explícitamente un schema actualizado; no debe asumirse que los consumidores 1.0 tolerarán campos desconocidos.

F1.4 debe decidir si la evolución jurídica puede expresarse mediante `analysis-result/1.1` o si requiere un cambio mayor. La decisión debe preceder a la modificación del prompt.

## 10. Estados y tipos públicos

| Tipo | Valores vigentes |
|---|---|
| `InputType` | `TEXT`, `PDF`, `AUDIO` |
| `AnalysisStatus` | `RECEIVED`, `PREPROCESSING`, `ANALYZING`, `COMPLETED`, `FAILED` |
| `SessionStatus` | `ACTIVE`, `EXPIRED`, `CLOSED` |
| `ConsentDecision` | `ACCEPTED`, `REJECTED` |
| `AiInvocationStatus` interno | `STARTED`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `INVALID_RESPONSE`, `ABANDONED` |
| `PatentabilityOutcome` | `POTENTIALLY_PATENTABLE`, `POTENTIALLY_NOT_PATENTABLE`, `INSUFFICIENT_INFORMATION` |
| `ProtectionType` | `INVENTION_PATENT`, `UTILITY_MODEL`, `INDUSTRIAL_DESIGN`, `DISTINCTIVE_SIGN`, `COPYRIGHT`, `OTHER` |
| `ProtectionApplicability` | `LIKELY`, `POSSIBLE`, `UNLIKELY` |

`AiInvocationStatus` no se expone al usuario STANDARD. Los retries permanecen dentro de `ANALYZING` y se reflejan únicamente como intentos internos.

## 11. Asincronía, retry y recuperación

El contrato de texto está implementado sobre estado durable:

```text
POST /analyses/text
       ↓
persistir Analysis(RECEIVED) + AnalysisInput(TEXT)
       ↓
202 + Location + Retry-After
       ↓
claim PostgreSQL con FOR UPDATE SKIP LOCKED
       ↓
AiInvocation(es), lease, retry y recovery
       ↓
AnalysisResult o fallo terminal persistido
       ↓
GET /analyses/{analysisId}
```

El backend no introduce Kafka, Redis o Celery en esta fase. PostgreSQL es la fuente de verdad del trabajo pendiente.

Los fallos retryable se programan con `next_attempt_at`; no bloquean threads con `sleep`. Si un worker desaparece, otro puede recuperar el análisis después del vencimiento del lease y registrar el intento previo como `ABANDONED`.

Una sesión puede vencer después de autorizar el `POST`. El procesamiento puede terminar y persistirse para trazabilidad, pero el usuario STANDARD no puede consultar el resultado sin una sesión vigente. No existe recuperación de sesiones históricas mediante DNI/CE.

## 12. PDF y audio

PDF y audio utilizarán el mismo `Analysis` y el mismo endpoint de consulta. Solo cambia el transporte de entrada y aparece la etapa `PREPROCESSING`.

| Ruta futura | Fase | Body | Secuencia |
|---|---|---|---|
| `POST /analyses/pdf` | F1.5 | `multipart/form-data` con PDF y metadata permitida | `RECEIVED → PREPROCESSING → ANALYZING → terminal` |
| `POST /analyses/audio` | F1.6 | `multipart/form-data` con audio y metadata permitida | `RECEIVED → PREPROCESSING → ANALYZING → terminal` |

Ambas rutas deberán:

- devolver `202` con el mismo `AnalysisCreatedResponse`;
- publicar `Location` y `Retry-After`;
- utilizar `GET /analyses/{analysisId}`;
- requerir sesión, consentimiento y CSRF;
- mantener el aislamiento por sesión;
- coordinar Python únicamente desde Java;
- converger a `processedText` y al mismo proveedor generativo.

F1.5/F1.6 deben definir MIME, formatos, tamaños, OCR/STT, almacenamiento y tratamiento de archivos originales.

## 13. Visibilidad STANDARD y administración futura

STANDARD puede consultar únicamente una ejecución concreta de la sesión vigente que la creó.

No existen contratos para:

- listar consultas históricas;
- consultar por `userId`;
- recuperar sesiones anteriores;
- restaurar cookies;
- ver prompts, invocaciones o metadata del proveedor;
- acceder a un perfil STANDARD desde este flujo.

La persistencia habilita evaluación, QA, métricas y auditoría internas futuras, pero no existe todavía un contrato ADMIN para consultar estos datos. Cualquier acceso administrativo debe diseñarse con autorización, minimización, retención y auditoría específicas.

## 14. Evolución por fases

### Implementado

| Fase | Contrato materializado |
|---|---|
| F1.1 | CSRF, sesión temporal, consulta/cierre y consentimiento |
| F1.2 | Contrato interno `GenerativeAiProvider`, prompts y schemas versionados |
| F1.3 | `POST /analyses/text`, `GET /analyses/{analysisId}`, asincronía durable y `AnalysisResult 1.0` |

### F1.4

- definir criterios jurídicos explícitos;
- representar adecuadamente los artículos 15 y 20 de la Decisión 486;
- evolucionar el schema y los DTOs de resultado;
- conservar compatibilidad o publicar una versión mayor;
- evitar puntuaciones numéricas de confianza sin calibración metodológica.

### F1.5/F1.6

- agregar contratos multipart de PDF/audio;
- mantener el mismo aggregate y contrato de consulta;
- extender seguridad, CSRF y pruebas de aislamiento.

### F1.7

- incorporar reporte web/PDF;
- controlar y versionar el disclaimer institucional fuera de `warnings[]`.

### F2

- unificar el envelope de errores si se aprueba;
- agregar correlation ID;
- definir rate limiting y anti-enumeración;
- cerrar gobierno de datos y acceso administrativo;
- versionar formalmente la API si aparecen consumidores externos adicionales.
