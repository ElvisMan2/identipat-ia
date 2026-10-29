# Matriz de autorización vigente — IDENTIPAT-IA

**Estado verificado hasta:** F1.7
**Última actualización:** 26 de octubre de 2026

Las rutas son relativas a `/identipat-ia`. Existen tres categorías separadas:

- `PUBLIC`: no exige una capacidad de acceso; una mutación puede exigir CSRF.
- `ADMIN_JWT`: autenticación ADMIN mediante Bearer JWT.
- `STANDARD_SESSION`: capacidad temporal de análisis exclusiva de un `STANDARD` activo mediante cookie `HttpOnly`. No es login, JWT ni identidad verificada.

| Endpoint | Acceso | Observaciones |
|---|---|---|
| `GET /standard-session/csrf` | `PUBLIC` | Emite `XSRF-TOKEN`; CSRF no autentica. |
| `POST /standard-sessions` | `PUBLIC + CSRF` | Exige `X-XSRF-TOKEN`; solo un STANDARD activo puede obtener `IDENTIPAT_STANDARD_SESSION`. Un ADMIN recibe `409 STANDARD_SESSION_NOT_AVAILABLE`. |
| `GET /standard-session` | `STANDARD_SESSION` | Renueva inactividad sin superar el límite absoluto. |
| `POST /standard-session/consent` | `STANDARD_SESSION + CSRF` | Persiste una decisión inmutable; `REJECTED` cierra la sesión. |
| `DELETE /standard-session` | `STANDARD_SESSION + CSRF` | Revoca server-side y elimina la cookie. |
| `POST /analyses/text` | `STANDARD_SESSION + consentimiento + CSRF` **o** `ADMIN_JWT` | STANDARD conserva el flujo de sesión. ADMIN se deriva del JWT, no requiere sesión ni consentimiento STANDARD y persiste ambos IDs de contexto como `NULL`. |
| `POST /analyses/pdf` | `STANDARD_SESSION + consentimiento + CSRF` **o** `ADMIN_JWT` | Extrae antes de persistir; aplica la misma resolución dual y no conserva el binario. |
| `POST /analyses/audio` | `STANDARD_SESSION + consentimiento + CSRF` **o** `ADMIN_JWT` | Transcribe localmente antes de persistir; aplica la misma resolución dual y no conserva el audio. |
| `GET /analyses/{analysisId}` | `STANDARD_SESSION` propia **o** `ADMIN_JWT` propietario | STANDARD se aísla por `session_id`; ADMIN por `user_id` y `session_id IS NULL`. Un recurso ajeno responde `404`. |
| `GET /analyses/{analysisId}/report[.pdf]` | `STANDARD_SESSION` propia **o** `ADMIN_JWT` propietario | Misma precedencia Bearer y aislamiento que el análisis. Solo `COMPLETED`; no expone PII ni metadata IA. |
| `GET /analyses/{analysisId}/guidance` | `STANDARD_SESSION` propia **o** `ADMIN_JWT` propietario | Reutiliza `AnalysisAccessResolver`, solo acepta `COMPLETED` y resuelve el catálogo institucional ACTIVE vigente. |
| `/admin/report-disclaimers/**` | `ADMIN_JWT` | Gobierno del disclaimer institucional. El actor de auditoría se deriva del JWT; no acepta identidad en el body. |
| `/admin/protection-guides/**` | `ADMIN_JWT` | Gobierno del catálogo de orientación; creación, edición, clonado y activación auditan al actor del JWT. |
| `POST /users/identify` | `PUBLIC` | Solo reconoce el registro; DNI/CE no autentica. |
| `POST /users` | `PUBLIC` | Registra un usuario STANDARD activo sin contraseña. |
| `POST /users/login` | `PUBLIC` | Solo un ADMIN activo recibe JWT. |
| `GET /users/**` | `ADMIN_JWT` | Consulta administrativa. |
| `PUT /users/**` | `ADMIN_JWT` | Mutación administrativa; no depende de la cookie STANDARD. |
| `DELETE /users/{userId}` | `ADMIN_JWT` | Si existe evidencia histórica asociada, la dirección funcional es desactivar para conservar trazabilidad; nunca realizar borrado en cascada. |

## Aplicación de los controles

La autorización combina la configuración HTTP de Spring Security con validaciones explícitas en los casos de uso:

- las rutas administrativas bajo `/users/**` quedan protegidas mediante `ADMIN_JWT`, excepto los tres endpoints públicos enumerados;
- las rutas `/standard-session/**`, `/standard-sessions` y `/analyses/**` llegan al controller sin una regla JWT única porque el análisis admite dos mecanismos;
- los servicios de sesión temporal solo aceptan usuarios `STANDARD` activos; una sesión histórica de un usuario cuyo tipo actual no sea `STANDARD` se considera inválida para consulta, consentimiento y análisis;
- `AnalysisAccessResolver` selecciona exactamente un modo: Bearer presente implica intento `ADMIN_JWT`; sin Bearer se usa `STANDARD_SESSION`;
- `GET /analyses/{analysisId}` consulta por `analysis_id + session_id` para STANDARD o por `analysis_id + user_id + session_id IS NULL` para ADMIN;
- `POST /analyses/text`, `/analyses/pdf` y `/analyses/audio` exigen consentimiento vigente únicamente en modo STANDARD.

El `permitAll()` técnico de Spring Security para `/analyses/**` no convierte los análisis en recursos públicos: permite que el caso de uso seleccione `STANDARD_SESSION` o `ADMIN_JWT`. Todo endpoint futuro bajo ese prefijo debe conservar esta resolución explícita.

Si coexisten Bearer ADMIN válido y cookie STANDARD, prevalece ADMIN. Si existe Bearer pero es inválido, expirado, inactivo o no ADMIN, la respuesta es `401` y nunca hay fallback a la cookie. Esta regla es inseparable de la exención CSRF del modo Bearer.

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
- `POST /analyses/**` cuando no presenta Bearer.

Los POST de análisis con Bearer se eximen de CSRF porque no se autorizan mediante cookie; un Bearer inválido produce `401`. Las operaciones `GET` no requieren CSRF. CORS admite credenciales únicamente desde los valores explícitos de `CORS_ALLOWED_ORIGINS`; el comodín `*` está prohibido.

## Límites del modelo

La cookie `IDENTIPAT_STANDARD_SESSION` vincula una experiencia de análisis a un registro STANDARD activo, pero no demuestra que quien opera el navegador sea titular real del DNI/CE.

Para STANDARD no existe:

- login convencional;
- contraseña;
- JWT;
- perfil visible;
- historial visible de consultas;
- recuperación de sesiones anteriores.

El consentimiento de datos y el disclaimer orientativo son conceptos distintos. El disclaimer de F1.7
es contenido institucional versionado; cada análisis completado conserva su versión histórica y el
reporte nunca utiliza automáticamente una versión activa posterior.

## Acceso ADMIN formalizado

F1.4C eliminó el acceso público de ADMIN mediante DNI/CE. El flujo es `POST /users/login` → Bearer JWT → `POST /analyses/text|pdf|audio` / `GET /analyses/{id}`. La identidad se vuelve a resolver en PostgreSQL por el subject del JWT y debe conservar `user_type=ADMIN` y `status=A`. Los análisis ADMIN tienen `user_id` obligatorio y `session_id`/`consent_event_id` nulos; no se crean sesiones ni consentimientos ficticios.

Impacto frontend: la experiencia ADMIN debe dejar de invocar `POST /standard-sessions` y `POST /standard-session/consent` para inferencia. El flujo STANDARD no cambia.

La política definitiva de acceso administrativo a consultas/resultados, retención, anonimización y borrado físico debe cerrarse antes de producción.
