# Documentación HTTP de IDENTIPAT-IA

## OpenAPI y Swagger UI

La API se documenta en tiempo de ejecución con `springdoc-openapi-starter-webmvc-ui` 2.5.0, compatible con Spring Boot 3.2.x. La especificación es la fuente de contrato para las rutas reales del backend y está disponible, con el perfil `dev`, en:

- OpenAPI JSON: `http://localhost:8082/identipat-ia/v3/api-docs`
- Swagger UI: `http://localhost:8082/identipat-ia/swagger-ui.html`

`dev` y `test` habilitan OpenAPI/Swagger. `prod` mantiene ambos deshabilitados por defecto mediante `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`.

## Seguridad

La especificación define `bearerAuth` para ADMIN y `standardSession` para la cookie STANDARD. STANDARD no recibe JWT: usa `IDENTIPAT_STANDARD_SESSION` HttpOnly como capacidad temporal. `GET /standard-session/csrf` es público; `POST /standard-sessions` es público + CSRF pero solo crea sesiones para STANDARD activo. `POST /analyses/text`, `/pdf` y `/audio` admiten sesión + consentimiento + CSRF o Bearer ADMIN; `GET /analyses/{analysisId}` usa la misma sesión STANDARD o el ADMIN propietario. `POST /users/identify`, `POST /users` y `POST /users/login` conservan acceso público.

Los reportes conservan esa resolución dual en `GET /analyses/{analysisId}/report` y
`GET /analyses/{analysisId}/report.pdf`. Ambos responden solo para `COMPLETED`, envían
`Cache-Control: no-store` y el PDF añade `Content-Disposition` y `X-Content-Type-Options: nosniff`.
Los endpoints `/admin/report-disclaimers/**` requieren ADMIN JWT, no CSRF, y permiten listar,
consultar, crear/editar DRAFT, clonar el ACTIVE y activar una versión.

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

## Listado operacional paginado de análisis

`GET /admin/analyses` requiere Bearer JWT de un usuario `ADMIN` y devuelve un `PagedResponse` para la tabla administrativa. Los parámetros opcionales son `page` (default `0`), `size` (default `20`, máximo `100`), `search`, `from`, `to`, `status`, `origin`, `inputType` y `sort` (default `createdAt,desc`). La respuesta expone exactamente `analysisId`, `createdAt`, `status`, `firstName`, `lastNames`, `doiType`, `doi`, `inputType`, `conclusion`, `identifiedModalities`, `analysisOrigin` y `guidanceConsulted`; no incluye reporte completo, datos de contacto ni identificadores de sesión/consentimiento.

La paginación, el count y los filtros se ejecutan en PostgreSQL. `search` separa términos por whitespace, combina los términos con AND y busca cada uno con OR, sin distinguir mayúsculas, en nombre, apellidos, DOI y email del perfil actual. La búsqueda usa un `EXISTS` correlacionado sobre `users`, porque `Analysis` conserva `userId` sin asociación JPA. `%`, `_` y `\` se escapan como literales. `from`/`to` representan días calendario de `America/Lima` con intervalo `[fromInclusive, toExclusive)`; `status` acepta los cinco estados de análisis, `origin` usa `STANDARD`/`ADMIN` según la presencia de `sessionId`, e `inputType` acepta `TEXT`, `PDF` o `AUDIO`.

Los campos de orden permitidos son `createdAt`, `status`, `inputType` y `analysisId`, con dirección `asc` o `desc`; `analysisId` se agrega como desempate estable con la misma dirección. Parámetros inválidos responden `400 INVALID_ANALYSIS_QUERY`, salvo `from > to`, que responde `400 INVALID_DATE_RANGE`. JWT ausente o inválido responde `401`, y un actor autenticado no ADMIN recibe `403`. Los resultados V1/V2 se presentan con la misma semántica de A1; el enriquecimiento de usuarios, resultados y existencia de guidance se limita a la página y se realiza en batch.

## Exportación operacional de análisis

`GET /admin/analyses/export.csv` requiere Bearer JWT de un usuario `ADMIN`; no aplica ownership por análisis ni acepta la cookie STANDARD como autorización. Admite los mismos filtros lógicos y el mismo `sort` que la tabla: `search`, `from`, `to`, `status`, `origin`, `inputType` y `sort`; no admite `page` ni `size`. `from` y `to` usan `YYYY-MM-DD` y días calendario de Perú con intervalo `[fromInclusive, toExclusive)`. La búsqueda es case-insensitive, combina términos con AND y los campos de perfil con OR. Los filtros se ejecutan en PostgreSQL mediante la misma `Specification` del listado. Un rango con `from > to` responde `400 INVALID_DATE_RANGE`; enums o sort inválidos responden `400 INVALID_ANALYSIS_QUERY`.

La respuesta `200` usa `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment` y `Cache-Control: no-store, private`. El CSV contiene todo el conjunto filtrado, sin paginación. Sin `sort` conserva el orden histórico `createdAt ASC, analysisId ASC`; con `sort` respeta el campo/dirección solicitados y agrega `analysisId` como desempate en la misma dirección. A3 podrá enviar explícitamente `sort=createdAt,desc` para reflejar el orden activo de la tabla.

Los nombres legacy son `identipat-analisis_completo.csv`, `identipat-analisis_desde_<from>.csv`, `identipat-analisis_hasta_<to>.csv` o `identipat-analisis_<from>_<to>.csv`. Si está activo `search`, `status`, `origin` o `inputType`, se agrega `_filtrado`; `sort` por sí solo no cambia el nombre. El archivo se construye completo en memoria desde `OperationalAnalysisReportRow` y el enrichment F1.10A1, usa UTF-8 con BOM, separador coma, registros `CRLF`, todas las celdas entre comillas y neutralización de formula injection para `=`, `+`, `-` y `@` después de whitespace. Sin resultados devuelve BOM y cabecera.

Las 21 columnas, en orden, son: `Id Analisis`, `Conclusion`, `Estado del Analisis`, `Reporte`, `Reporte PDF Disponible`, `Nombres`, `Apellidos`, `Tipo de Documento`, `Numero de Documento`, `Genero`, `Fecha de Nacimiento`, `Telefono`, `Celular`, `Correo`, `Profesion u Oficio`, `Fecha de Creacion`, `Tipo de Entrada`, `Modalidades Identificadas`, `Aplicabilidad por Modalidad`, `Origen del Analisis` y `Orientacion Consultada`. `Fecha de Creacion` se presenta en `America/Lima`. JWT ausente o inválido responde `401` y un actor autenticado no ADMIN recibe `403`.

## Postman

La colección está en `postman/identipat-api.collection.json` e incluye `STANDARD - Session and consent`, además de reconocimiento/registro, administración y negativos.

Las variables de colección no contienen secretos reales:

- `baseUrl`: URL base local.
- `adminDoi` y `adminPassword`: placeholders que el operador debe definir localmente.
- `adminJwt`: vacío inicialmente; el request **Login ADMIN and save JWT** lo guarda automáticamente al recibir una respuesta correcta.
- `standardDoi`, `standardDoiType` y `adminUserId`: datos de prueba ajustables.
- `csrfToken`: se guarda dinámicamente desde **Get CSRF and save token**.
- `legalContentHash`: hash vigente guardado desde `GET /legal-document` o `PUT /admin/legal-document`.
- `analysisId`: se guarda desde la respuesta `202` del flujo STANDARD.
- `adminAnalysisId`: se guarda por separado desde la respuesta `202` del flujo ADMIN.
- `reportFrom` y `reportTo`: fechas calendario de ejemplo para la exportación operacional; ambos filtros son opcionales.

Ejecuta primero **Login ADMIN and save JWT**. `Admin - Users` incluye ejemplos paginados, de búsqueda, filtros, ordenamiento y exportación CSV de usuarios; `ADMIN - Diagnostic reports and disclaimers` incluye la exportación operacional de análisis. Ambos grupos envían `Authorization: Bearer {{adminJwt}}`. Las solicitudes que crean, actualizan o eliminan datos requieren valores de prueba únicos y son intencionalmente manuales.

El flujo STANDARD no usa Bearer token: obtiene CSRF y `GET /legal-document`; si registra usuario, envía `legalAcceptance`; después crea sesión y acepta el hash solo cuando `consentRequired=true`. El flujo ADMIN incluye gobierno de `/admin/legal-document` y análisis con Bearer, sin consentimiento STANDARD. Los grupos PDF/audio usan selección manual de archivo y no almacenan archivos en el repositorio. No hay polling infinito. No deben guardarse DNI, voz o documentos reales, cookies, pepper, JWT ni secretos en variables compartidas.

## Endpoints STANDARD

| Método y ruta | Resultado principal |
| --- | --- |
| `GET /standard-session/csrf` | Cookie `XSRF-TOKEN` y `{token, headerName}`; no autentica. |
| `GET /legal-document` | Contenido legal vigente y `contentHash`; público y sin PII. |
| `POST /users` | Registro atómico User + acceptance; exige CSRF y `legalAcceptance`. |
| `POST /standard-sessions` | `201`, cookie HttpOnly para STANDARD activo; ADMIN recibe `409`. |
| `GET /standard-session` | Estado vigente y renovación de inactividad. |
| `POST /standard-session/consent` | Recibe `contentHash`; `ACCEPTED` es idempotente y `REJECTED` no persiste ni cierra sesión. |
| `DELETE /standard-session` | `204`, revocación y borrado de cookie. |
| `POST /analyses/text` | `202`, `Location`, `Retry-After` y UUID; STANDARD usa sesión/consentimiento/CSRF y ADMIN usa Bearer. |
| `POST /analyses/pdf` | Multipart `file`; mismo `202` después de extracción exitosa. STANDARD usa sesión/consentimiento/CSRF y ADMIN usa Bearer. |
| `POST /analyses/audio` | Multipart `file`; mismo `202` después de transcripción local exitosa. STANDARD usa sesión/consentimiento/CSRF y ADMIN usa Bearer. |
| `GET /analyses/{analysisId}` | `200` para la misma sesión STANDARD o el ADMIN propietario del análisis ADMIN. |
| `GET /analyses/{analysisId}/report` | `DiagnosticReport` histórico; solo para `COMPLETED`. |
| `GET /analyses/{analysisId}/report.pdf` | PDF en memoria con el mismo contenido y disclaimer histórico. |

## Administración del disclaimer de reporte

`GET /admin/report-disclaimers`, `/active` y `/{id}` consultan versiones; `POST` crea un DRAFT;
`PUT /{id}` edita exclusivamente DRAFT; `POST /active/draft` clona el vigente y
`POST /{id}/activate` retira el ACTIVE anterior y promueve el DRAFT. `title` se normaliza y admite
1..120 caracteres; `content`, 1..5000. La identidad de auditoría siempre proviene del JWT.
Los errores funcionales son `DISCLAIMER_NOT_FOUND` (404), `DISCLAIMER_IMMUTABLE` (409),
`DISCLAIMER_ALREADY_ACTIVE` (409) e `INVALID_DISCLAIMER` (422).

Las mutaciones anteriores, incluidos los POST bajo `/analyses/**` sin Bearer, requieren cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`; su ausencia o discordancia responde `403`. Con Bearer se eximen de CSRF y un token inválido nunca hace fallback a STANDARD. Spring Boot 3.2.12 resuelve Spring Security 6.2.8, por lo que se usa un handler SPA: valor plano para cookie/header y manejo XOR/BREACH para atributos de request.

## Contrato público PDF

`POST /analyses/pdf` consume `multipart/form-data` con un único campo `file`. Solo acepta
`application/pdf` con firma `%PDF-`; los defaults son 10 MiB, 50 páginas y los mismos límites de
texto normalizado de TEXT (20–20000 caracteres). La extracción es síncrona y el `202 Accepted` usa
exactamente `AnalysisCreatedResponse`, `Location` y `Retry-After`; el resultado se consulta con el
GET común. El binario no se persiste y OCR no está incluido.

Respuestas PDF: `413 PDF_TOO_LARGE`, `415 UNSUPPORTED_PDF_MEDIA_TYPE`, `422 INVALID_PDF`,
`PDF_ENCRYPTED_NOT_SUPPORTED`, `PDF_PAGE_LIMIT_EXCEEDED`, `PDF_TEXT_NOT_EXTRACTABLE` o
`PDF_TEXT_TOO_LONG`; `503 PREPROCESSING_SERVICE_UNAVAILABLE` o `ANALYSIS_AI_UNAVAILABLE`.

## Contrato público audio

`POST /analyses/audio` consume `multipart/form-data` con un único campo `file`; no acepta idioma,
modelo, texto ni actor en el body. Los MIME admitidos son `audio/webm`, `audio/mpeg`, `audio/mp4`,
`video/mp4`, `audio/x-m4a`, `audio/3gpp`, `video/3gpp`, `audio/wav`, `audio/x-wav`, `audio/ogg` y
`audio/flac`; se ignoran parámetros como `codecs=opus` o `codecs=mp4a.40.2`. `video/mp4` y
`video/3gpp` son exclusivamente aliases de transporte para contenedores de audio etiquetados así por
el cliente: Python exige al menos una pista de audio y rechaza cualquier pista de video. No se aceptan
`video/*`, `audio/*` ni `application/octet-stream` de forma genérica. Los defaults son 20 MiB,
180 segundos, español y límites de texto normalizado de 20–20000 caracteres. Java valida MIME/tamaño
y Python valida autoritativamente el contenedor y lo decodifica antes de ejecutar `faster-whisper`.
El `202`, `Location`, `Retry-After` y GET son comunes; el GET devuelve `inputType=AUDIO`. El binario
no se persiste.

Respuestas audio: `413 AUDIO_TOO_LARGE`, `415 UNSUPPORTED_AUDIO_MEDIA_TYPE`, `422 INVALID_AUDIO`,
`AUDIO_DURATION_LIMIT_EXCEEDED`, `AUDIO_TRANSCRIPTION_EMPTY` o `AUDIO_TEXT_TOO_LONG`; y
`503 AUDIO_TRANSCRIPTION_UNAVAILABLE` o `ANALYSIS_AI_UNAVAILABLE`.

## Errores actuales

Los códigos de sesión/consentimiento se complementan con `CONSENT_REQUIRED`, `INVALID_ANALYSIS_DESCRIPTION`, los códigos PDF/audio documentados, `ANALYSIS_AI_UNAVAILABLE` y `ANALYSIS_NOT_FOUND`. Un análisis `FAILED` devuelve solo categorías públicas saneadas. Ningún error expone token, hash, pepper, PII, stack trace, prompt, respuesta del proveedor, PyAV/CTranslate2/Hugging Face o detalle de base. Los rechazos CSRF los produce Spring Security con `403`.
