# Matriz de autorización vigente — IDENTIPAT-IA

**Estado verificado hasta:** F1.3  
**Última actualización:** 16 de septiembre de 2026

Las rutas son relativas a `/identipat-ia`. Existen tres categorías separadas:

- `PUBLIC`: no exige una capacidad de acceso; una mutación puede exigir CSRF.
- `ADMIN_JWT`: autenticación ADMIN mediante Bearer JWT.
- `STANDARD_SESSION`: capacidad temporal por cookie `HttpOnly`; no es login, JWT, identidad verificada ni una authority ADMIN.

| Endpoint | Acceso | Observaciones |
|---|---|---|
| `GET /standard-session/csrf` | `PUBLIC` | Emite `XSRF-TOKEN`; CSRF no autentica. |
| `POST /standard-sessions` | `PUBLIC + CSRF` | Exige `X-XSRF-TOKEN`; valida un STANDARD activo y emite `IDENTIPAT_STANDARD_SESSION`. |
| `GET /standard-session` | `STANDARD_SESSION` | Renueva inactividad sin superar el límite absoluto. |
| `POST /standard-session/consent` | `STANDARD_SESSION + CSRF` | Persiste una decisión inmutable; `REJECTED` cierra la sesión. |
| `DELETE /standard-session` | `STANDARD_SESSION + CSRF` | Revoca server-side y elimina la cookie. |
| `POST /analyses/text` | `STANDARD_SESSION + CSRF` | Exige consentimiento vigente; deriva usuario/sesión del servidor, persiste antes de invocar al LLM y responde `202`. |
| `GET /analyses/{analysisId}` | `STANDARD_SESSION` | Exige sesión activa y pertenencia a la misma `session_id`; otra sesión o un UUID inexistente responde `404`. |
| `POST /users/identify` | `PUBLIC` | Solo reconoce el registro; DNI/CE no autentica. |
| `POST /users` | `PUBLIC` | Registra un usuario STANDARD activo sin contraseña. |
| `POST /users/login` | `PUBLIC` | Solo un ADMIN activo recibe JWT. |
| `GET /users/**` | `ADMIN_JWT` | Consulta administrativa. |
| `PUT /users/**` | `ADMIN_JWT` | Mutación administrativa; no depende de la cookie STANDARD. |
| `DELETE /users/{userId}` | `ADMIN_JWT` | Si existe evidencia histórica asociada, la dirección funcional es desactivar para conservar trazabilidad; nunca realizar borrado en cascada. |

## Aplicación de los controles

La autorización combina la configuración HTTP de Spring Security con validaciones explícitas en los casos de uso:

- las rutas administrativas bajo `/users/**` quedan protegidas mediante `ADMIN_JWT`, excepto los tres endpoints públicos enumerados;
- las rutas `/standard-session/**`, `/standard-sessions` y `/analyses/**` llegan al controller sin exigir JWT ADMIN;
- los servicios STANDARD resuelven la cookie, comprueban vigencia y estado de la sesión y validan las reglas funcionales correspondientes;
- `GET /analyses/{analysisId}` consulta por `analysis_id` y `session_id`, por lo que el UUID por sí solo no autoriza el acceso;
- `POST /analyses/text` exige además consentimiento aceptado para la versión y el hash vigentes.

El `permitAll()` de Spring Security para `/analyses/**` no convierte los análisis en recursos públicos: permite que el caso de uso aplique el mecanismo de sesión STANDARD, distinto del JWT ADMIN. Todo endpoint futuro bajo ese prefijo debe conservar explícitamente esta validación; no debe confiar únicamente en el matcher HTTP.

## CSRF y CORS

Spring Security 6.2 usa `CookieCsrfTokenRepository` con:

- cookie `XSRF-TOKEN`;
- `HttpOnly=false`, para que Angular pueda leer el token CSRF;
- `SameSite=Lax`;
- `Path=/`;
- header `X-XSRF-TOKEN`.

La protección es selectiva para las mutaciones STANDARD implementadas:

- `POST /standard-sessions`;
- `POST /standard-session/consent`;
- `DELETE /standard-session`;
- `POST /analyses/text`.

Las operaciones `GET` no requieren CSRF. CORS admite credenciales únicamente desde los valores explícitos de `CORS_ALLOWED_ORIGINS`; el comodín `*` está prohibido.

Cuando se incorporen `POST /analyses/pdf` y `POST /analyses/audio`, deberán agregarse a la protección CSRF. Se recomienda evolucionar a una regla general para las mutaciones STANDARD bajo `/analyses/**`, evitando depender de una enumeración manual por endpoint.

## Límites del modelo

La cookie `IDENTIPAT_STANDARD_SESSION` vincula una experiencia a un registro STANDARD, pero no demuestra que quien opera el navegador sea titular real del DNI/CE.

Para STANDARD no existe:

- login convencional;
- contraseña;
- JWT;
- perfil visible;
- historial visible de consultas;
- recuperación de sesiones anteriores.

El consentimiento de datos y el disclaimer orientativo son conceptos distintos. El consentimiento y el análisis de texto están implementados en F1.1 y F1.3, respectivamente; el disclaimer institucional permanece pendiente para F1.7.

La política definitiva de acceso administrativo a consultas/resultados, retención, anonimización y borrado físico debe cerrarse antes de producción.
