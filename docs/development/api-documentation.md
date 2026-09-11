# Documentación HTTP de IDENTIPAT-IA

## OpenAPI y Swagger UI

La API se documenta en tiempo de ejecución con `springdoc-openapi-starter-webmvc-ui` 2.5.0, compatible con Spring Boot 3.2.x. La especificación es la fuente de contrato para las rutas reales del backend y está disponible, con el perfil `dev`, en:

- OpenAPI JSON: `http://localhost:8082/identipat-ia/v3/api-docs`
- Swagger UI: `http://localhost:8082/identipat-ia/swagger-ui.html`

`dev` y `test` habilitan OpenAPI/Swagger. `prod` mantiene ambos deshabilitados por defecto mediante `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`.

## Seguridad

La especificación define el esquema HTTP `bearerAuth` con formato `JWT`. Solo las operaciones administrativas de usuarios requieren dicho esquema. Las rutas públicas son `POST /users/identify`, `POST /users` y `POST /users/login`; que una ruta sea pública no modifica que el login solo acepte un ADMIN activo.

Swagger UI permite usar **Authorize** con el JWT emitido por el login administrativo. No se usa HTTP Basic.

## Postman

La colección está en `postman/identipat-api.collection.json` y tiene cuatro grupos: `Public - Standard`, `Admin - Authentication`, `Admin - Users` y `Security - Negative`.

Las variables de colección no contienen secretos reales:

- `baseUrl`: URL base local.
- `adminDoi` y `adminPassword`: placeholders que el operador debe definir localmente.
- `adminJwt`: vacío inicialmente; el request **Login ADMIN and save JWT** lo guarda automáticamente al recibir una respuesta correcta.
- `standardDoi`, `standardDoiType` y `adminUserId`: datos de prueba ajustables.

Ejecuta primero **Login ADMIN and save JWT** y luego las operaciones en `Admin - Users`, que envían `Authorization: Bearer {{adminJwt}}`. Las solicitudes que crean, actualizan o eliminan datos requieren valores de prueba únicos y son intencionalmente manuales.

El flujo STANDARD no usa token: primero **Recognize document** y, si el resultado es `registered: false`, **Register STANDARD**. El request de registro refleja exclusivamente `StandardUserRegistrationRequest`; no envía identificador administrativo, tipo de usuario, estado ni contraseña.

## Errores actuales

Las respuestas de validación y errores de negocio usan el `GlobalExceptionHandler` actual y no se rediseñan en esta fase. La documentación declara los estados relevantes según cada operación: `400` para validación o datos inválidos/DOI duplicado, `401` para JWT ausente o login rechazado, `403` para falta de rol ADMIN y `404` para usuarios inexistentes. La entrada sin JWT de una ruta protegida puede responder solo con el estado 401, conforme a `HttpStatusEntryPoint`.
