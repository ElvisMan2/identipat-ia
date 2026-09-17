# Guía de prueba del flujo completo de usuario STANDARD desde Swagger UI

## 1. Objetivo

Esta guía describe cómo validar manualmente, desde Swagger UI, el flujo funcional completo de un usuario `STANDARD` en IDENTIPAT-IA:

1. reconocer o registrar al usuario;
2. obtener protección CSRF;
3. crear la sesión temporal;
4. aceptar el consentimiento vigente;
5. crear un análisis de texto;
6. consultar el resultado asíncrono;
7. cerrar la sesión.

La guía corresponde al backend de la rama `develop` y asume que la aplicación está ejecutándose con el perfil `dev` en:

```text
http://localhost:8082/identipat-ia
```

> Un usuario `STANDARD` no inicia sesión con contraseña ni recibe JWT. El DNI o CE identifica el registro, mientras que el acceso temporal se mantiene mediante una cookie HttpOnly.

## 2. Ubicación

```text
docs/development/standard-user-swagger-flow.md
```

Esta ubicación mantiene la guía junto a la documentación de configuración, API y ejecución local destinada al equipo de desarrollo.

## 3. Requisitos previos

- Backend levantado con el perfil `dev`.
- PostgreSQL disponible y migraciones Flyway aplicadas.
- Integración de IA habilitada si se desea completar el análisis real.
- `OPENAI_API_KEY` configurada cuando el proveedor activo es OpenAI.
- Swagger UI disponible en:

  ```text
  http://localhost:8082/identipat-ia/swagger-ui.html
  ```

Use siempre el mismo host durante toda la prueba. No alterne entre `localhost` y `127.0.0.1`, porque las cookies se asocian al host.

## 4. Habilitar CSRF en Swagger UI

El backend exige el par cookie/header CSRF en las mutaciones del flujo `STANDARD`:

```text
Cookie: XSRF-TOKEN=<token>
Header: X-XSRF-TOKEN: <token>
```

Para que Swagger UI copie automáticamente el valor de la cookie al encabezado, agregue esta configuración en `backend/src/main/resources/application-dev.yml`:

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    csrf:
      enabled: true
      cookie-name: XSRF-TOKEN
      header-name: X-XSRF-TOKEN
```

Como alternativa, configure las variables de entorno equivalentes antes de iniciar el backend:

```powershell
$env:SPRINGDOC_SWAGGER_UI_CSRF_ENABLED = "true"
$env:SPRINGDOC_SWAGGER_UI_CSRF_COOKIE_NAME = "XSRF-TOKEN"
$env:SPRINGDOC_SWAGGER_UI_CSRF_HEADER_NAME = "X-XSRF-TOKEN"
```

Después de modificar la configuración, reinicie el backend.

> Esta configuración es necesaria para completar el flujo usando los botones **Try it out** y **Execute**. La configuración actual del esquema OpenAPI solo declara `bearerAuth` para ADMIN y no expone el encabezado CSRF como parámetro manual de cada operación.

## 5. Paso 1: reconocer el documento

En el grupo **Users**, ejecute:

```http
POST /users/identify
```

Body de ejemplo:

```json
{
  "doi": "00000000",
  "doiType": "DNI"
}
```

### Usuario STANDARD existente

```json
{
  "registered": true,
  "passwordRequired": false
}
```

Continúe con la obtención del token CSRF.

### Documento no registrado

```json
{
  "registered": false,
  "passwordRequired": false
}
```

Registre al usuario mediante el paso siguiente.

### Documento perteneciente a un ADMIN

```json
{
  "registered": true,
  "passwordRequired": true
}
```

Ese documento no puede iniciar una sesión `STANDARD`. Utilice un documento de prueba diferente.

## 6. Paso 2: registrar un usuario STANDARD cuando no existe

Ejecute:

```http
POST /users
```

Body de ejemplo:

```json
{
  "firstName": "Usuario",
  "paternalLastName": "Prueba",
  "maternalLastName": "Swagger",
  "doi": "70000001",
  "doiType": "DNI",
  "birthDate": "15/05/1995",
  "gender": "M",
  "email": "usuario.swagger@example.com",
  "phone": "014567890",
  "mobilePhone": "987654321",
  "profession": "Ingeniero industrial"
}
```

Respuesta esperada:

```http
201 Created
```

```json
{
  "registered": true
}
```

El servidor asigna automáticamente:

- `userType = STANDARD`;
- `status = A`;
- contraseña nula.

Si el DOI ya existe, la operación responde `400 Bad Request`.

## 7. Paso 3: obtener el token CSRF

En el grupo **STANDARD session**, ejecute:

```http
GET /standard-session/csrf
```

Respuesta esperada:

```http
200 OK
```

```json
{
  "token": "<token-csrf>",
  "headerName": "X-XSRF-TOKEN"
}
```

El backend también emite la cookie:

```text
XSRF-TOKEN=<token-csrf>
```

Para verificar el envío automático del encabezado:

1. abra las herramientas de desarrollo del navegador con `F12`;
2. seleccione la pestaña **Network**;
3. ejecute el siguiente POST protegido;
4. compruebe en **Request Headers** la presencia de `X-XSRF-TOKEN`.

## 8. Paso 4: crear la sesión STANDARD

Ejecute:

```http
POST /standard-sessions
```

Body:

```json
{
  "doi": "70000001",
  "doiType": "DNI"
}
```

Respuesta esperada:

```http
201 Created
```

```json
{
  "status": "ACTIVE",
  "expiresAt": "2026-09-17T...",
  "absoluteExpiresAt": "2026-09-18T...",
  "consentRequired": true,
  "requiredConsentVersion": "personal-data/1.0"
}
```

El navegador recibe además la cookie HttpOnly:

```text
IDENTIPAT_STANDARD_SESSION=<token-opaco>
```

No utilice el botón **Authorize** para el flujo `STANDARD`; este flujo no usa JWT.

### Errores relevantes

| HTTP | Código o causa | Acción recomendada |
|---:|---|---|
| `400` | `INVALID_DOCUMENT` | Verificar que `doiType` sea `DNI` o `CE` y corresponda al registro. |
| `403` | CSRF ausente o inválido | Repetir el GET de CSRF y verificar el encabezado en Network. |
| `409` | `STANDARD_REGISTRATION_REQUIRED` | Registrar primero al usuario. |
| `409` | `STANDARD_SESSION_NOT_AVAILABLE` | Confirmar que el registro sea `STANDARD` y esté activo. |

## 9. Paso 5: verificar la sesión activa

Ejecute:

```http
GET /standard-session
```

Respuesta esperada:

```json
{
  "status": "ACTIVE",
  "expiresAt": "...",
  "absoluteExpiresAt": "...",
  "consentRequired": true,
  "requiredConsentVersion": "personal-data/1.0"
}
```

Esta operación confirma que Swagger UI está enviando la cookie `IDENTIPAT_STANDARD_SESSION`. También renueva el límite de inactividad, pero nunca extiende el vencimiento absoluto.

## 10. Paso 6: aceptar el consentimiento

Copie exactamente el valor de `requiredConsentVersion` recibido al crear o consultar la sesión y ejecute:

```http
POST /standard-session/consent
```

Body:

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "ACCEPTED"
}
```

Respuesta esperada:

```http
200 OK
```

```json
{
  "consentVersion": "personal-data/1.0",
  "decision": "ACCEPTED",
  "decidedAt": "...",
  "sessionStatus": "ACTIVE",
  "consentRequired": false
}
```

No utilice `REJECTED` en el flujo positivo: esa decisión persiste el rechazo, cierra la sesión y elimina la cookie.

## 11. Paso 7: crear el análisis de texto

En el grupo **STANDARD analyses**, ejecute:

```http
POST /analyses/text
```

La descripción normalizada debe contener entre 20 y 20 000 puntos de código Unicode.

Body de ejemplo:

```json
{
  "description": "He diseñado un sistema modular de riego que utiliza sensores de humedad instalados en diferentes zonas del terreno. El sistema ajusta automáticamente el caudal de cada válvula según las mediciones obtenidas y el tipo de cultivo."
}
```

Respuesta esperada:

```http
202 Accepted
Location: /identipat-ia/analyses/<analysisId>
Retry-After: 2
```

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "status": "RECEIVED",
  "createdAt": "..."
}
```

Copie el valor de `analysisId` para consultar el recurso.

### Errores relevantes

| HTTP | Código o causa | Acción recomendada |
|---:|---|---|
| `401` | `STANDARD_SESSION_REQUIRED` | Crear nuevamente la sesión si falta, expiró o fue cerrada. |
| `403` | CSRF ausente o inválido | Renovar el CSRF y verificar el encabezado. |
| `409` | `CONSENT_REQUIRED` | Aceptar la versión vigente del consentimiento. |
| `422` | `INVALID_ANALYSIS_DESCRIPTION` | Ajustar la longitud de la descripción. |
| `503` | `ANALYSIS_AI_UNAVAILABLE` | Revisar `IDENTIPAT_AI_ENABLED`, el proveedor y sus credenciales. |

## 12. Paso 8: consultar el estado o resultado

Ejecute:

```http
GET /analyses/{analysisId}
```

Ingrese el UUID obtenido en el paso anterior. La consulta no requiere CSRF, pero exige la cookie de la misma sesión que creó el análisis.

### Análisis recibido o en proceso

Inicialmente puede responder con `status = RECEIVED` o:

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "inputType": "TEXT",
  "status": "ANALYZING",
  "createdAt": "...",
  "startedAt": "...",
  "completedAt": null,
  "failedAt": null,
  "result": null,
  "failure": null
}
```

Espere al menos el número de segundos indicado en `Retry-After` —dos segundos con la configuración predeterminada— y vuelva a ejecutar el GET.

### Análisis completado

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "inputType": "TEXT",
  "status": "COMPLETED",
  "createdAt": "...",
  "startedAt": "...",
  "completedAt": "...",
  "failedAt": null,
  "result": {
    "schemaVersion": "analysis-result/1.0",
    "summary": "...",
    "patentabilityAssessment": {
      "outcome": "POTENTIALLY_PATENTABLE",
      "rationale": "..."
    },
    "protectionOptions": [],
    "observations": [],
    "warnings": []
  },
  "failure": null
}
```

El resultado real puede utilizar otros valores válidos del contrato y no constituye un dictamen jurídico oficial.

### Análisis fallido

```json
{
  "analysisId": "6e3e61f1-0bae-40bc-8c6f-4511b0bb7011",
  "inputType": "TEXT",
  "status": "FAILED",
  "createdAt": "...",
  "startedAt": "...",
  "completedAt": null,
  "failedAt": "...",
  "result": null,
  "failure": {
    "code": "ANALYSIS_TEMPORARILY_UNAVAILABLE",
    "message": "No fue posible completar el análisis en este momento."
  }
}
```

## 13. Paso 9: cerrar la sesión

Ejecute:

```http
DELETE /standard-session
```

Respuesta esperada:

```http
204 No Content
```

Después del cierre, una nueva ejecución de:

```http
GET /standard-session
```

debe responder `401 Unauthorized` con el código:

```text
STANDARD_SESSION_REQUIRED
```

## 14. Resumen de la secuencia

| Orden | Endpoint | Resultado esperado |
|---:|---|---|
| 1 | `POST /users/identify` | Determinar si el documento ya está registrado. |
| 2 | `POST /users` | `201`, solamente si el registro no existe. |
| 3 | `GET /standard-session/csrf` | Token JSON y cookie `XSRF-TOKEN`. |
| 4 | `POST /standard-sessions` | `201`, sesión `ACTIVE` y cookie HttpOnly. |
| 5 | `GET /standard-session` | Confirmación de sesión y versión de consentimiento. |
| 6 | `POST /standard-session/consent` | Consentimiento `ACCEPTED`. |
| 7 | `POST /analyses/text` | `202`, estado `RECEIVED` y `analysisId`. |
| 8 | `GET /analyses/{analysisId}` | `RECEIVED`, `ANALYZING`, `COMPLETED` o `FAILED`. |
| 9 | `DELETE /standard-session` | `204` y revocación de la sesión. |

## 15. Criterios de aprobación de la prueba

El flujo se considera satisfactorio si:

- el reconocimiento diferencia un registro inexistente, `STANDARD` y `ADMIN`;
- el registro crea exclusivamente un usuario `STANDARD` activo;
- las mutaciones sin CSRF responden `403`;
- la creación de sesión emite una cookie HttpOnly y no expone el token en el JSON;
- el consentimiento aceptado habilita la creación del análisis;
- el POST del análisis responde `202` sin esperar al proveedor;
- el GET progresa hasta `COMPLETED` o devuelve un fallo público saneado;
- otro navegador o sesión no puede consultar el mismo `analysisId`;
- el cierre de sesión provoca que las consultas posteriores respondan `401`.

## 16. Referencias del repositorio

- `backend/src/main/java/com/mnk/identipatia/controller/StandardSessionController.java`
- `backend/src/main/java/com/mnk/identipatia/analysis/controller/AnalysisController.java`
- `backend/src/main/java/com/mnk/identipatia/config/SecurityConfig.java`
- `backend/src/main/java/com/mnk/identipatia/config/OpenApiConfig.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `docs/development/api-documentation.md`
- `docs/development/text-analysis-flow.md`
- `docs/design/analysis-contract.md`
