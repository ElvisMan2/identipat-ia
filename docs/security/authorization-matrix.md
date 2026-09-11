# Matriz de autorización — F0.3

## Principios vigentes

- Solo `ADMIN` se autentica con contraseña, Spring Security y JWT.
- `STANDARD` no usa contraseña para acceder, no recibe JWT y no dispone de perfil ni historial visibles.
- `doi` y `doiType` (DNI/CE) solo determinan si ya existe un registro; no autentican ni prueban identidad.
- Angular consume estas rutas exclusivamente a través del backend Java.

Las rutas de la tabla son relativas al contexto de la aplicación (`/identipat-ia`).

| Método y endpoint | Propósito | Público | ADMIN autenticado | Respuesta relevante | Observaciones de seguridad |
| --- | --- | --- | --- | --- | --- |
| `POST /users/identify` | Reconocer si existe un registro por documento | Sí | Sí | `200 { "registered": true|false }` | Solo recibe `doi` y `doiType`; no emite JWT ni devuelve id, PII, rol, estado, contraseña, historial o consultas. |
| `POST /users` | Registrar un nuevo STANDARD | Sí | Sí | `201 { "registered": true }` | El request no contiene id, rol, estado ni contraseña. El servidor fuerza `STANDARD`, estado activo y `password = null`; el documento debe ser único. |
| `POST /users/login` | Autenticar administración | Sin token previo | Sí, si sus credenciales son válidas | `200 { "tokenType": "Bearer", "accessToken": "..." }` | Únicamente ADMIN activo puede autenticarse. STANDARD es rechazado y nunca recibe JWT. |
| `GET /users` | Listar usuarios | No | Sí | `200` con usuarios | Operación administrativa; sin token válido responde `401`. |
| `GET /users/{userId}` | Consultar detalle de usuario | No | Sí | `200` con `UserDTO` | El detalle completo, incluida la consulta por id, es administrativo. |
| `GET /users/doi/{doi}` | Consultar detalle por documento | No | Sí | `200` con `UserDTO` | Ya no es una vía pública de reconocimiento y queda protegida por `ROLE_ADMIN`. |
| `PUT /users/{userId}` | Actualizar datos de usuario | No | Sí | `200` con `UserDTO` | Solo ADMIN; STANDARD no puede editar perfil. |
| `PUT /users/admin/{userId}` | Actualización administrativa integral, incluidos rol/estado | No | Sí | `200` con `UserDTO` | Solo ADMIN. No existe cambio de rol o estado público. |
| `DELETE /users/{userId}` | Eliminar usuario | No | Sí | `204` | Solo ADMIN; sin token válido responde `401`. |

## Contrato de reconocimiento público

Request:

```json
{
  "doi": "12345678",
  "doiType": "DNI"
}
```

Response:

```json
{
  "registered": true
}
```

`doiType` es el campo que distingue DNI y CE en el modelo actual. La unicidad vigente del modelo es sobre `doi`; la verificación pública comprueba ambos campos para no reconocer un tipo de documento distinto.

## Límites funcionales

Si `registered` es `false`, el frontend debe continuar con el registro STANDARD. Si es `true`, debe continuar al flujo posterior de la herramienta cuando exista. No se implementan en F0.3 sesiones, perfil, historial, consentimiento, disclaimer, análisis ni consultas.

La respuesta de reconocimiento permite enumerar la existencia de un documento, comportamiento requerido por el flujo actual. Mitigaciones futuras posibles —sin implementar aquí— incluyen límites de frecuencia, monitoreo, CAPTCHA u otra verificación acordada con Indecopi.
