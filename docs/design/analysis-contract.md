# Contrato conceptual de análisis — F1.0

**Estado:** sesión/consentimiento implementados en F1.1 y contrato de texto implementado en F1.3.
**Ámbito:** contrato que Angular consumirá exclusivamente a través de Java/Spring Boot. Ninguna ruta autoriza llamadas directas a PostgreSQL, `preprocessing-service` o un proveedor LLM.

## 1. Principios del contrato

- La cookie de sesión `STANDARD` es una capacidad temporal y limitada; no es JWT, login ni autenticación administrativa.
- Las rutas de reconocimiento y registro existentes conservan sus contratos actuales. F1.0 no modifica `POST /users/identify` ni `POST /users`.
- La creación de sesión se separa de reconocer existencia y de registrar persona. El backend vuelve a validar DNI/CE al crearla y nunca acepta un `userId` aportado por Angular.
- La coincidencia de DNI/CE con un registro permite asociar la experiencia al registro `STANDARD`, pero no verifica criptográfica ni presencialmente la identidad de quien usa el navegador.
- Una sesión solo puede iniciar análisis tras un consentimiento explícito vigente. El disclaimer orientativo del diagnóstico no es el consentimiento y se resolverá en F1.7.
- Un análisis es asíncrono: su identificación UUID solo sirve para referencia; Java además verifica que pertenece a la sesión temporal vigente que presenta la cookie.
- Las respuestas a `STANDARD` no incluyen PII del usuario, token de sesión, prompts, respuesta cruda del proveedor, parámetros del modelo, intentos técnicos ni datos de otras sesiones.

Todas las rutas siguientes son relativas al context path actual `/identipat-ia`.

F1.1 usa `CookieCsrfTokenRepository`: `GET /standard-session/csrf` emite `XSRF-TOKEN` y devuelve su valor para el header `X-XSRF-TOKEN`. Creación/cierre de sesión y consentimiento requieren ambos. F1.3 extenderá la protección a mutaciones `/analyses/**`. `SameSite` y CORS no sustituyen CSRF; el JWT Bearer ADMIN sigue separado.

## 2. Contrato conceptual de sesión

### 2.1 Crear sesión

`POST /standard-sessions`

Responsabilidad: crear una sesión temporal para un `User` `STANDARD` activo ya reconocido o recién registrado. No sustituye `POST /users/identify` y no devuelve datos de usuario.

Request ilustrativo:

```json
{
  "doi": "DOCUMENTO_DE_EJEMPLO",
  "doiType": "DNI"
}
```

Respuesta `201 Created` ilustrativa:

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

El token no aparece en JSON ni en URL. En producción la cookie añade `Secure`; su `Max-Age` se limita por la expiración absoluta. La aplicación Angular debe enviar solicitudes cross-origin locales con credenciales y Java debe aceptar solo los orígenes configurados. Si existe una cookie activa del mismo navegador asociada al mismo registro `STANDARD`, Java cierra esa sesión con motivo de rotación antes de emitir la nueva; una sesión de otro navegador no se invalida por el solo conocimiento de DNI/CE.

Errores de negocio previstos:

| Estado | Código conceptual | Significado |
| --- | --- | --- |
| `400` | `INVALID_DOCUMENT` | DNI/CE no cumple validación de forma. |
| `409` | `STANDARD_REGISTRATION_REQUIRED` | No hay `STANDARD` activo con ese documento; el cliente debe usar el flujo de registro. |
| `409` | `STANDARD_SESSION_NOT_AVAILABLE` | El usuario existe, pero no puede iniciar la experiencia por una regla de estado futura. |

### 2.2 Consultar y cerrar la sesión actual

| Método y ruta | Uso conceptual | Respuesta |
| --- | --- | --- |
| `GET /standard-session` | Rehidratar el estado visual del navegador actual tras refrescar la página. | Estado, vencimientos y si falta consentimiento; sin `sessionId`, token ni PII. |
| `DELETE /standard-session` | Cierre explícito. | `204 No Content`, marca `CLOSED` y expira la cookie. |

Una cookie ausente, inválida, cerrada o expirada responde de forma uniforme con `401 STANDARD_SESSION_REQUIRED`; no revela si alguna sesión existió. Al detectar expiración, Java registra `EXPIRED` cuando corresponda y ordena borrar la cookie. Esto solo señala que falta una capacidad temporal de flujo; no convierte `STANDARD` en una identidad autenticada.

## 3. Consentimiento

`POST /standard-session/consent`

La ruta obtiene la sesión exclusivamente de la cookie HttpOnly. No recibe `sessionId` ni `userId` en body o path.

Request ilustrativo:

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "ACCEPTED"
}
```

Para una aceptación vigente, respuesta `200 OK`:

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "ACCEPTED",
  "decidedAt": "2030-01-15T14:05:00Z",
  "sessionStatus": "ACTIVE",
  "consentRequired": false
}
```

Para `REJECTED`, Java persiste el evento inmutable, cierra la sesión y borra la cookie. Responde `200` con `decision: "REJECTED"`, `sessionStatus: "CLOSED"` y `consentRequired: true`. Un nuevo intento requiere abrir una sesión nueva y elegir explícitamente.

El servidor valida que la versión enviada sea la vigente y conserva en `consent_events` la versión, hash del documento, decisión, instante, `sessionId` y `userId` internos. La evidencia registra una decisión tomada desde una sesión temporal asociada al registro `STANDARD`; no prueba que quien operó el navegador sea la persona titular real del DNI/CE. No se registran IP, geolocalización, huella de dispositivo ni user-agent. El endpoint no presenta ni acepta el disclaimer de IA como consentimiento.

| Estado | Código conceptual | Significado |
| --- | --- | --- |
| `400` | `INVALID_CONSENT_DECISION` | La decisión no es `ACCEPTED` ni `REJECTED`. |
| `409` | `CONSENT_VERSION_OUTDATED` | La pantalla usa una versión que ya no es la vigente. |
| `401` | `STANDARD_SESSION_REQUIRED` | No hay sesión `ACTIVE` válida. |
| `409` | `CONSENT_ALREADY_DECIDED` | Ya existe una decisión distinta para sesión/versión. |

## 4. Análisis por texto

### 4.1 Crear una consulta

`POST /analyses/text`

Precondiciones: cookie de sesión activa y una aceptación de consentimiento vigente para esa sesión. Java valida tamaño/contenido de forma, crea `Analysis` en estado `RECEIVED`, registra la entrada y programa el procesamiento durable. El cliente no aporta `userId`, `sessionId`, `analysisId`, proveedor, modelo, prompt ni estado.

Request ilustrativo:

```json
{
  "description": "Sistema modular que ajusta el riego según mediciones del suelo."
}
```

Respuesta `202 Accepted` ilustrativa:

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "status": "RECEIVED",
  "createdAt": "2030-01-15T14:10:00Z"
}
```

El frontend consulta posteriormente la ruta de estado. No asume que una consulta termine en una misma conexión HTTP.

| Estado | Código conceptual | Significado |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | JSON malformado. |
| `401` | `STANDARD_SESSION_REQUIRED` | Falta una sesión activa o su cookie no es válida. |
| `409` | `CONSENT_REQUIRED` | La sesión existe pero no cuenta con aceptación vigente. |
| `422` | `INVALID_ANALYSIS_DESCRIPTION` | Texto vacío o fuera de los límites configurados. |
| `503` | `ANALYSIS_AI_UNAVAILABLE` | IA deshabilitada/no disponible; no se crea `Analysis`. |

### 4.2 Consultar estado o resultado

`GET /analyses/{analysisId}`

La autorización se resuelve con la cookie: el análisis debe pertenecer a la misma sesión `ACTIVE` que la presenta. Si el UUID no existe, pertenece a otra sesión o la sesión ya no es válida, la respuesta pública es `404 ANALYSIS_NOT_FOUND` o `401 STANDARD_SESSION_REQUIRED` según corresponda, sin distinguir datos de otra persona.

Respuesta mientras procesa (`200 OK`) ilustrativa:

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "status": "ANALYZING",
  "createdAt": "2030-01-15T14:10:00Z",
  "startedAt": "2030-01-15T14:10:02Z"
}
```

Respuesta completada (`200 OK`) ilustrativa:

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "status": "COMPLETED",
  "createdAt": "2030-01-15T14:10:00Z",
  "startedAt": "2030-01-15T14:10:02Z",
  "completedAt": "2030-01-15T14:10:07Z",
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
      },
      {
        "type": "COPYRIGHT",
        "applicability": "UNLIKELY",
        "rationale": "Puede aplicar a materiales expresivos asociados, no necesariamente a la solución técnica."
      }
    ],
    "observations": [
      "Conviene documentar componentes, funcionamiento y diferencias frente a alternativas conocidas."
    ],
    "warnings": [
      "La descripción no detalla el mecanismo de ajuste ni los componentes de medición."
    ]
  }
}
```

El JSON anterior es válido y representativo, no un dictamen jurídico ni una respuesta ya implementada. No añade porcentaje de confianza: una salida generativa no es una probabilidad calibrada. `warnings[]` contiene solo advertencias surgidas del contenido o contexto del diagnóstico, como la falta de detalle del ejemplo. El disclaimer institucional no depende de Gemini, no es contenido libre del modelo, no se almacena por defecto en `warnings[]` y se diseña como contenido controlado y versionado por la aplicación en F1.7.

Respuesta de fallo terminal (`200 OK`) ilustrativa:

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "status": "FAILED",
  "failedAt": "2030-01-15T14:10:12Z",
  "failure": {
    "code": "ANALYSIS_TEMPORARILY_UNAVAILABLE",
    "message": "No fue posible completar el análisis en este momento."
  }
}
```

El fallo conserva detalles técnicos restringidos en `AiInvocation`; el mensaje público evita stack traces, nombres de secretos o cuerpos del proveedor.

## 5. Estrategia futura para PDF y audio

El dominio interno es el mismo `Analysis`; los contratos de transporte se especializan porque texto usa JSON y archivos requieren `multipart/form-data`.

| Ruta futura | Fase | Body conceptual | Secuencia |
| --- | --- | --- | --- |
| `POST /analyses/pdf` | F1.5 | Multipart con un PDF y metadata permitida. | `RECEIVED → PREPROCESSING → ANALYZING → terminal`. |
| `POST /analyses/audio` | F1.6 | Multipart con audio y metadata permitida. | `RECEIVED → PREPROCESSING → ANALYZING → terminal`. |

Ambas responden `202` con el mismo envelope de creación y se consultan mediante `GET /analyses/{analysisId}`. Java recibe el archivo, persiste/coordina lo que corresponda, llama internamente a `preprocessing-service` y entrega a IA solo `processedText`. Angular nunca llama a Python. F1.0 no define almacenamiento físico, MIME, límites, OCR, transcripción ni formatos.

## 6. Sincronía, asincronía y recuperación

Se adopta asincronía durable para `POST /analyses/text` y para las rutas de archivos futuras:

```text
POST /analyses/text
       ↓
persistir Analysis RECEIVED + Input
       ↓
202 + analysisId
       ↓
ejecutor acotado de Java
       ↓
AiInvocation(es) y resultado/estado persistidos
       ↓
GET /analyses/{analysisId}
```

La decisión evita bloquear la UX por latencia variable de un LLM y conserva una API coherente cuando se agreguen PDF/audio. F1.3 deberá ejecutar sobre estado persistido, recuperar de forma controlada análisis no terminales al reiniciar y definir límites de concurrencia/retry. No introduce Kafka, Redis, Celery ni otro broker en esta fase de diseño.

Una sesión puede vencer después de que autorizó el `POST`: el análisis iniciado puede terminar y persistirse para trazabilidad, pero ya no es legible por `STANDARD` sin una sesión vigente. La expiración no concede recuperación posterior de resultados.

## 7. Contrato `AnalysisResult`

`AnalysisResult` es el resultado canónico aceptado por Java y guardado en `analysis_results.result_json`. No es la respuesta cruda del proveedor. Su envelope inicial es:

| Campo | Tipo conceptual | Regla F1.0 |
| --- | --- | --- |
| `schemaVersion` | string | Obligatorio; coincide con la columna persistida, por ejemplo `analysis-result/1.0`. |
| `summary` | string | Obligatorio; síntesis orientativa legible. |
| `patentabilityAssessment` | object | Obligatorio; contiene `outcome` y `rationale`. |
| `protectionOptions` | array | Obligatorio, posiblemente vacío; cada elemento tiene `type`, `applicability`, `rationale`. |
| `observations` | array de strings | Obligatorio, posiblemente vacío. |
| `warnings` | array de strings | Obligatorio, posiblemente vacío; únicamente advertencias del diagnóstico, nunca el disclaimer institucional de F1.7. |

Tipos conceptuales iniciales:

| Tipo | Valores/forma |
| --- | --- |
| `InputType` | `TEXT`, `PDF`, `AUDIO`. |
| `AnalysisStatus` | `RECEIVED`, `PREPROCESSING`, `ANALYZING`, `COMPLETED`, `FAILED`. |
| `SessionStatus` | `ACTIVE`, `EXPIRED`, `CLOSED`. |
| `ConsentDecision` | `ACCEPTED`, `REJECTED`. |
| `AiInvocationStatus` | `STARTED`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `INVALID_RESPONSE`, `ABANDONED`. |
| `ProtectionType` | `INVENTION_PATENT`, `UTILITY_MODEL`, `INDUSTRIAL_DESIGN`, `DISTINCTIVE_SIGN`, `COPYRIGHT`, `OTHER`. |
| `ProtectionApplicability` | `LIKELY`, `POSSIBLE`, `UNLIKELY`. |
| `PatentabilityOutcome` | `POTENTIALLY_PATENTABLE`, `POTENTIALLY_NOT_PATENTABLE`, `INSUFFICIENT_INFORMATION`. |

La versión sigue versionado semántico del envelope. En el mismo major, se permiten campos opcionales nuevos; una ruptura incrementa el major y exige adaptador/contrato compatible. Las respuestas históricas conservan su `schemaVersion` original: no se reinterpretan ni se sobrescriben para aparentar que usaron una taxonomía nueva.

## 8. Visibilidad STANDARD y administración futura

`STANDARD` puede consultar únicamente una ejecución concreta de la sesión temporal vigente que la creó. No habrá rutas como `GET /users/{id}/analyses`, listado de consultas, perfil, restauración de cookie ni navegación de resultados de sesiones anteriores. Un UUID de análisis de otra sesión responde como no encontrado y nunca es suficiente para autorizarlo.

La persistencia habilita en el futuro evaluación interna, QA, métricas y auditoría autorizada, pero F1.0 no crea un endpoint ni panel `ADMIN` para esos fines. Cualquier acceso administrativo deberá definirse con autorización, minimización, retención y auditoría separadas.

Cuando un `User` tenga sesiones, consentimientos, análisis u otra evidencia histórica, la dirección funcional es desactivarlo lógicamente y conservar esa trazabilidad. No se prevé borrado físico en cascada de esas relaciones. Anonimización de PII, derecho de eliminación, retención, condiciones excepcionales de borrado físico y la adaptación del endpoint ADMIN de eliminación quedan para gobierno de datos/producción.

## 9. Reglas que deberán respetar las fases posteriores

### F1.1

- Implementar sesión server-side con token opaco en cookie HttpOnly, HMAC persistido y TTL configurable de 30 minutos de inactividad/8 horas absoluto.
- Mantener `ADMIN` JWT separado y **definir e implementar** una estrategia CSRF explícita para todas las rutas STANDARD mutables basadas en cookie; no modificar reconocimiento/registro para ocultar la sesión dentro de ellos.
- Persistir eventos de consentimiento por sesión, versión y hash; no recolectar telemetría técnica innecesaria.

### F1.2

- Exponer una abstracción de proveedor generativo en Java; Python no integra LLM ni prompts.
- Registrar cada intento como `AiInvocation` con proveedor/modelo, prompt versionado y snapshot, parámetros, payloads, tokens, latencia y errores saneados.

### F1.3

- Crear un único `Analysis` para texto, `analysis_inputs` 1:1 y procesamiento asíncrono durable con recuperación posterior a reinicio.
- Validar y persistir solo el `AnalysisResult` estructurado canónico; no usar un `String freeTextResult` como modelo principal.
- Cumplir las reglas de acceso por cookie de la sesión actual y no añadir historial STANDARD.

### F1.4

- Profundizar criterios de propiedad intelectual, el vocabulario de conclusión, referencias y relevancia sin romper el envelope `analysis-result/1.x` si la compatibilidad lo permite.
- No inventar puntuaciones numéricas de confianza sin calibración y justificación metodológica.

### F1.5/F1.6 y F1.7

- PDF/audio convergen a `processedText` y al mismo flujo de análisis; no crean aggregates paralelos ni mueven negocio a Python.
- El disclaimer de resultados se trata y versiona como presentación/comunicación en F1.7, separado de `consent_events`.
