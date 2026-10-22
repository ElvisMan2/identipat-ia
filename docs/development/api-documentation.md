# Documentación HTTP de IDENTIPAT-IA

## OpenAPI y Swagger UI

La API se documenta en tiempo de ejecución con `springdoc-openapi-starter-webmvc-ui` 2.5.0, compatible con Spring Boot 3.2.x. La especificación es la fuente de contrato para las rutas reales del backend y está disponible, con el perfil `dev`, en:

- OpenAPI JSON: `http://localhost:8082/identipat-ia/v3/api-docs`
- Swagger UI: `http://localhost:8082/identipat-ia/swagger-ui.html`

`dev` y `test` habilitan OpenAPI/Swagger. `prod` mantiene ambos deshabilitados por defecto mediante `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`.

## Seguridad

La especificación define `bearerAuth` para ADMIN. STANDARD no recibe JWT: usa `IDENTIPAT_STANDARD_SESSION` HttpOnly como capacidad temporal. `GET /standard-session/csrf` es público; `POST /standard-sessions` es público pero exige CSRF; consultar, consentir y cerrar requieren la cookie válida. `POST /analyses/text` exige sesión, consentimiento y CSRF; `GET /analyses/{analysisId}` exige la misma sesión pero no CSRF. `POST /users/identify`, `POST /users` y `POST /users/login` conservan acceso público y no exigen CSRF porque no se autorizan mediante la cookie STANDARD.

Swagger UI permite usar **Authorize** con el JWT emitido por el login administrativo. No se usa HTTP Basic.

## Listado administrativo de usuarios

`GET /users` requiere JWT de un usuario `ADMIN` y devuelve un objeto paginado, no un array plano. Sus parámetros son:

| Parámetro | Default | Regla |
| --- | --- | --- |
| `page` | `0` | entero mayor o igual a cero |
| `size` | `20` | entero entre 1 y 100 |
| `search` | vacío | términos separados por espacios |
| `userType` | vacío | `ADMIN` o `STANDARD` |
| `status` | vacío | `A` o `I` |
| `doiType` | vacío | `DNI` o `CE` |
| `sort` | `creationDate,desc` | campo permitido y dirección `asc` o `desc` |

Cada término de `search` se busca, sin distinguir mayúsculas/minúsculas, en nombre, apellidos, DOI, email o profesión. Un término puede coincidir en una columna diferente, pero todos los términos y filtros deben coincidir. Los campos ordenables son `userId`, `firstName`, `paternalLastName`, `doi`, `email`, `userType`, `status` y `creationDate`; salvo cuando el orden principal ya es `userId`, se agrega `userId asc` como desempate estable.

La respuesta contiene `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`. Una página válida sin elementos responde `200` con `content: []`. Parámetros inválidos responden `400`; JWT ausente o inválido responde `401`, y un actor autenticado que no sea ADMIN recibe `403`.

Este cambio reemplaza intencionalmente el contrato anterior `List<UserDTO>`. El frontend existente puede quedar temporalmente incompatible hasta que adapte su consumo; esta tarea no modifica Angular.

## Exportación administrativa de usuarios

`GET /users/export` requiere JWT de un usuario `ADMIN` y acepta `search`, `userType`, `status`, `doiType` y `sort` con las mismas reglas de validación y matching que `GET /users`. No acepta parámetros funcionales de paginación: consulta y exporta todos los usuarios coincidentes, manteniendo el orden solicitado y el desempate estable por `userId asc`.

La respuesta `200` usa `Content-Type: text/csv; charset=UTF-8` y `Content-Disposition: attachment; filename="usuarios-YYYY-MM-DD.csv"`. El archivo incluye BOM UTF-8, separador coma, saltos `CRLF`, escape mediante comillas dobles y protección contra fórmulas de hoja de cálculo para valores cuyo primer carácter significativo sea `=`, `+`, `-` o `@`. Si no hay coincidencias, contiene únicamente el BOM y el encabezado. Parámetros inválidos responden `400`; JWT ausente o inválido responde `401`, y un actor autenticado que no sea ADMIN recibe `403`.

Las columnas, en orden, son: `ID`, `Nombre completo`, `Documento`, `Nacimiento`, `Género`, `Correo`, `Teléfono`, `Celular`, `Tipo`, `Profesión` y `Estado`. La contraseña y la metadata interna no se exportan.

## Postman

La colección está en `postman/identipat-api.collection.json` e incluye `STANDARD - Session and consent`, además de reconocimiento/registro, administración y negativos.

Las variables de colección no contienen secretos reales:

- `baseUrl`: URL base local.
- `adminDoi` y `adminPassword`: placeholders que el operador debe definir localmente.
- `adminJwt`: vacío inicialmente; el request **Login ADMIN and save JWT** lo guarda automáticamente al recibir una respuesta correcta.
- `standardDoi`, `standardDoiType` y `adminUserId`: datos de prueba ajustables.
- `csrfToken`: se guarda dinámicamente desde **Get CSRF and save token**.
- `consentVersion`: versión esperada por el backend.
- `analysisId`: se guarda desde la respuesta `202` para una consulta GET manual posterior.

Ejecuta primero **Login ADMIN and save JWT** y luego las operaciones en `Admin - Users`, que envían `Authorization: Bearer {{adminJwt}}`. El grupo incluye ejemplos paginados, de búsqueda, filtros, ordenamiento y exportación CSV completa. Las solicitudes que crean, actualizan o eliminan datos requieren valores de prueba únicos y son intencionalmente manuales.

El flujo STANDARD no usa Bearer token: primero **Recognize document** y, si aplica, **Register STANDARD**. Después ejecuta **Get CSRF**, crea sesión, registra consentimiento, crea el análisis y realiza un GET manual. Postman conserva las cookies en su cookie jar y envía `X-XSRF-TOKEN: {{csrfToken}}`. No hay polling infinito. No deben guardarse DNI reales, cookies, pepper, JWT ni secretos en variables compartidas.

## Endpoints STANDARD

| Método y ruta | Resultado principal |
| --- | --- |
| `GET /standard-session/csrf` | Cookie `XSRF-TOKEN` y `{token, headerName}`; no autentica. |
| `POST /standard-sessions` | `201`, cookie HttpOnly y estado sin PII/IDs/token. |
| `GET /standard-session` | Estado vigente y renovación de inactividad. |
| `POST /standard-session/consent` | Evento `ACCEPTED`/`REJECTED`; nunca recibe hash/IDs. |
| `DELETE /standard-session` | `204`, revocación y borrado de cookie. |
| `POST /analyses/text` | `202`, `Location`, `Retry-After` y UUID público del análisis. |
| `GET /analyses/{analysisId}` | `200` con estado/resultado/fallo saneado de la misma sesión. |

Las mutaciones anteriores, incluido el POST de análisis, requieren cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`; su ausencia o discordancia responde `403`. Spring Boot 3.2.12 resuelve Spring Security 6.2.8, por lo que se usa un handler SPA: valor plano para cookie/header y manejo XOR/BREACH para atributos de request.

## Errores actuales

Los códigos de sesión/consentimiento se complementan con `CONSENT_REQUIRED`, `INVALID_ANALYSIS_DESCRIPTION`, `ANALYSIS_AI_UNAVAILABLE` y `ANALYSIS_NOT_FOUND`. Un análisis `FAILED` devuelve solo categorías públicas saneadas. Ningún error expone token, hash, pepper, PII, stack trace, prompt, respuesta del proveedor o detalle de base. Los rechazos CSRF los produce Spring Security con `403`.
