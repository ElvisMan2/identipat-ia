# Matriz de autorización — F1.1

Las rutas son relativas a `/identipat-ia`. Existen tres categorías separadas:

- `PUBLIC`: no exige una capacidad de acceso; una mutación puede exigir CSRF.
- `ADMIN_JWT`: autenticación ADMIN mediante Bearer JWT.
- `STANDARD_SESSION`: capacidad temporal por cookie HttpOnly; no es login, JWT, identidad verificada ni una authority ADMIN.

| Endpoint | Acceso | Observaciones |
| --- | --- | --- |
| `GET /standard-session/csrf` | `PUBLIC` | Emite `XSRF-TOKEN`; CSRF no autentica. |
| `POST /standard-sessions` | `PUBLIC + CSRF` | Exige `X-XSRF-TOKEN`; valida un STANDARD activo y emite `IDENTIPAT_STANDARD_SESSION`. |
| `GET /standard-session` | `STANDARD_SESSION` | Renueva inactividad sin superar el límite absoluto. |
| `POST /standard-session/consent` | `STANDARD_SESSION + CSRF` | Persiste una decisión inmutable; `REJECTED` cierra la sesión. |
| `DELETE /standard-session` | `STANDARD_SESSION + CSRF` | Revoca server-side y elimina la cookie. |
| `POST /users/identify` | `PUBLIC` | Solo reconoce el registro; DNI/CE no autentica. |
| `POST /users` | `PUBLIC` | Registra STANDARD activo sin password. |
| `POST /users/login` | `PUBLIC` | Solo un ADMIN activo recibe JWT. |
| `GET /users/**` | `ADMIN_JWT` | Consulta administrativa. |
| `PUT /users/**` | `ADMIN_JWT` | Mutación administrativa; no depende de cookie STANDARD. |
| `DELETE /users/{userId}` | `ADMIN_JWT` | Si hay sesiones históricas, desactiva para conservar trazabilidad; nunca hace cascade. |

## CSRF y CORS

Spring Security 6.2 usa `CookieCsrfTokenRepository` con cookie `XSRF-TOKEN` (`HttpOnly=false`, `SameSite=Lax`, `Path=/`) y header `X-XSRF-TOKEN`. La protección es selectiva para las mutaciones STANDARD implementadas. F1.3 deberá extenderla a las mutaciones `/analyses/**`. CORS admite credenciales únicamente para `CORS_ALLOWED_ORIGINS` explícitos; `*` está prohibido.

## Límites

La cookie `IDENTIPAT_STANDARD_SESSION` vincula una experiencia a un registro STANDARD, pero no demuestra que quien opera el navegador sea titular real del DNI/CE. No existe perfil, historial visible ni recuperación de sesiones para STANDARD. Consentimiento de datos y disclaimer orientativo son conceptos distintos; F1.1 no implementa análisis ni disclaimer.
