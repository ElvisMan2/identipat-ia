# Documentación HTTP de IDENTIPAT-IA

## OpenAPI y Swagger UI

La API se documenta en tiempo de ejecución con `springdoc-openapi-starter-webmvc-ui` 2.5.0, compatible con Spring Boot 3.2.x. La especificación es la fuente de contrato para las rutas reales del backend y está disponible, con el perfil `dev`, en:

- OpenAPI JSON: `http://localhost:8082/identipat-ia/v3/api-docs`
- Swagger UI: `http://localhost:8082/identipat-ia/swagger-ui.html`

`dev` y `test` habilitan OpenAPI/Swagger. `prod` mantiene ambos deshabilitados por defecto mediante `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`.

## Seguridad

La especificación define `bearerAuth` para ADMIN. STANDARD no recibe JWT: usa `IDENTIPAT_STANDARD_SESSION` HttpOnly como capacidad temporal. `GET /standard-session/csrf` es público; `POST /standard-sessions` es público pero exige CSRF; consultar, consentir y cerrar requieren la cookie válida. `POST /users/identify`, `POST /users` y `POST /users/login` conservan acceso público y no exigen CSRF porque no se autorizan mediante la cookie STANDARD.

Swagger UI permite usar **Authorize** con el JWT emitido por el login administrativo. No se usa HTTP Basic.

## Postman

La colección está en `postman/identipat-api.collection.json` e incluye `STANDARD - Session and consent`, además de reconocimiento/registro, administración y negativos.

Las variables de colección no contienen secretos reales:

- `baseUrl`: URL base local.
- `adminDoi` y `adminPassword`: placeholders que el operador debe definir localmente.
- `adminJwt`: vacío inicialmente; el request **Login ADMIN and save JWT** lo guarda automáticamente al recibir una respuesta correcta.
- `standardDoi`, `standardDoiType` y `adminUserId`: datos de prueba ajustables.
- `csrfToken`: se guarda dinámicamente desde **Get CSRF and save token**.
- `consentVersion`: versión esperada por el backend.

Ejecuta primero **Login ADMIN and save JWT** y luego las operaciones en `Admin - Users`, que envían `Authorization: Bearer {{adminJwt}}`. Las solicitudes que crean, actualizan o eliminan datos requieren valores de prueba únicos y son intencionalmente manuales.

El flujo STANDARD no usa Bearer token: primero **Recognize document** y, si aplica, **Register STANDARD**. Después ejecuta **Get CSRF**, crea sesión y registra consentimiento. Postman conserva las cookies en su cookie jar y envía `X-XSRF-TOKEN: {{csrfToken}}`. No deben guardarse DNI reales, cookies, pepper, JWT ni secretos en variables compartidas.

## Endpoints F1.1

| Método y ruta | Resultado principal |
| --- | --- |
| `GET /standard-session/csrf` | Cookie `XSRF-TOKEN` y `{token, headerName}`; no autentica. |
| `POST /standard-sessions` | `201`, cookie HttpOnly y estado sin PII/IDs/token. |
| `GET /standard-session` | Estado vigente y renovación de inactividad. |
| `POST /standard-session/consent` | Evento `ACCEPTED`/`REJECTED`; nunca recibe hash/IDs. |
| `DELETE /standard-session` | `204`, revocación y borrado de cookie. |

Las mutaciones anteriores requieren cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`; su ausencia o discordancia responde `403`. Spring Boot 3.2.12 resuelve Spring Security 6.2.8, por lo que se usa un handler SPA: valor plano para cookie/header y manejo XOR/BREACH para atributos de request. F1.3 deberá extender el matcher CSRF a mutaciones `/analyses/**`.

## Errores actuales

F1.1 añade códigos seguros `INVALID_DOCUMENT`, `STANDARD_REGISTRATION_REQUIRED`, `STANDARD_SESSION_NOT_AVAILABLE`, `STANDARD_SESSION_REQUIRED`, `INVALID_CONSENT_DECISION`, `CONSENT_VERSION_OUTDATED` y `CONSENT_ALREADY_DECIDED`. Ningún error expone token, hash, pepper, PII, stack trace o detalle de base. Los rechazos CSRF los produce Spring Security con `403`.
