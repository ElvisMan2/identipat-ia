# Consentimiento legal persistente por usuario STANDARD

**Estado:** F1.1B backend y frontend implementados
**Fecha:** 3 de noviembre de 2026

## Regla funcional

```text
sesión STANDARD activa + aceptación del hash y vigencia efectiva actuales = creación de análisis habilitada
```

La sesión conserva el aislamiento temporal. La aceptación pertenece al usuario y se reutiliza entre sesiones. ADMIN no necesita aceptación STANDARD para analizar.

## Contenido legal

`legal_document` contiene una sola fila vigente. PostgreSQL impone el singleton mediante `singleton_key = 1` y `UNIQUE(singleton_key)`. ADMIN edita directamente con `PUT /admin/legal-document`; no existen números de versión, drafts, estados, publicación ni retiro.

El HTML se sanea con `legal-document-html/1.0`. Se permiten `h1`–`h4`, `p`, `br`, `strong`, `em`, `u`, `ul`, `ol`, `li` y enlaces HTTPS. Los enlaces se normalizan a `target="_blank" rel="noopener noreferrer"`; scripts, estilos, formularios, multimedia, handlers y protocolos ejecutables se eliminan.

La identidad exacta es:

```text
canonical = title.trim() + "\n" + sanitizedContentHtml
contentHash = lowercase_hex(SHA-256(UTF-8(canonical)))
```

El historial `legal_document_history` conserva un snapshot inmutable por hash. Volver exactamente a contenido histórico reutiliza ese snapshot, pero inicia una nueva vigencia efectiva en `legal_document.updated_at`. Una aceptación antigua del mismo hash no vuelve a habilitar al usuario.

## Aceptaciones

`user_terms_acceptances` registra `acceptance_id`, `user_id`, `accepted_content_sha256`, `accepted_document_updated_at`, `accepted_at` y `source`. La FK del hash exige que exista el snapshot histórico. La vigencia se comprueba con la fórmula:

```text
acceptance.user_id == current user
AND acceptance.accepted_content_sha256 == legal_document.content_sha256
AND acceptance.accepted_document_updated_at == legal_document.updated_at
```

`UNIQUE(user_id, accepted_content_sha256, accepted_document_updated_at)` e `INSERT ... ON CONFLICT DO NOTHING` garantizan idempotencia concurrente dentro de la misma vigencia y conservan el `accepted_at` original. Coincidir con un hash previamente aceptado no basta: la aceptación debe corresponder además a la vigencia efectiva actual del documento.

Fuentes permitidas:

- `STANDARD_REGISTRATION`;
- `STANDARD_WEB`.

No se persisten sesión, DNI/CE, email, IP, User-Agent ni rechazos.

## Contratos

- `GET /legal-document`: público, solo contenido vigente y hash.
- `GET /admin/legal-document`: ADMIN JWT.
- `PUT /admin/legal-document`: ADMIN JWT, upsert del único documento.
- `POST /users`: público con CSRF; exige `legalAcceptance.contentHash` y `accepted=true`; User + acceptance se crean en una transacción.
- `POST /standard-session/consent`: sesión STANDARD + CSRF; recibe `contentHash` y `decision`.
- `GET /standard-session`: calcula dinámicamente `consentRequired` por usuario, hash y momento efectivo vigentes; no devuelve versión legal.

`ACCEPTED` persiste o reutiliza la aceptación de la vigencia efectiva actual. `REJECTED` no persiste, no cierra la sesión y responde `consentRequired=true`. Si el hash ya no es vigente, responde `409 LEGAL_DOCUMENT_CHANGED`. Un `PUT` canónicamente idéntico conserva `legal_document.updated_at` y no invalida aceptaciones; cualquier cambio efectivo asigna un nuevo momento, incluso al volver A→B→A.

## Análisis y legado

Los análisis STANDARD nuevos almacenan `session_id + terms_acceptance_id`; los ADMIN dejan ambos nulos. La aceptación se valida antes de leer/preprocesar PDF/audio y antes de usar IA.

`consent_events` permanece como tabla legacy sin uso en operaciones nuevas y no se convierte en aceptación válida. `consent_event_id` permanece nullable para análisis históricos. La constraint transitoria admite exactamente ADMIN, STANDARD legacy o STANDARD nuevo y nunca ambas evidencias a la vez.

La autorización histórica de lectura no cambia: STANDARD sigue aislado por la `session_id` que creó el análisis y ADMIN por `user_id` con `session_id IS NULL`.

## Handoff frontend

Angular adopta los nuevos contratos y elimina la autoaceptación del flujo anterior. Esta integración no declara completada F1.9 ni cerrado el E2E de navegador.
