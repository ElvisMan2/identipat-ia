# Flujo backend STANDARD desde Swagger UI

**Estado:** F1.1B integrado en backend y frontend; este documento conserva el flujo de verificación backend por Swagger
**Base local:** `http://localhost:8082/identipat-ia`

## Preparación

Use siempre el mismo host. El perfil `dev` habilita Swagger UI en `/swagger-ui.html` y configura la copia de la cookie `XSRF-TOKEN` al header `X-XSRF-TOKEN`.

Un usuario STANDARD no inicia sesión, no recibe JWT y no usa su DNI/CE como contraseña. La cookie `IDENTIPAT_STANDARD_SESSION` es HttpOnly y solo representa una capacidad temporal.

## Configurar el documento legal como ADMIN

1. Ejecute `POST /users/login` con credenciales ADMIN.
2. Autorice Swagger con el JWT Bearer.
3. Consulte `GET /admin/legal-document`. La primera vez responde `404 LEGAL_DOCUMENT_NOT_FOUND`.
4. Ejecute `PUT /admin/legal-document`:

```json
{
  "title": "Términos de uso y Política de Privacidad",
  "contentHtml": "<h1>Título institucional</h1><p>Contenido aprobado.</p>"
}
```

El backend sanitiza el HTML y devuelve `contentHash`. No existe versión numérica, draft ni botón/publicación separada.

## Obtener CSRF

Ejecute `GET /standard-session/csrf`. La respuesta incluye el token y se emite la cookie `XSRF-TOKEN`. Es obligatorio para:

- `POST /users`;
- `POST /standard-sessions`;
- `POST /standard-session/consent`;
- `DELETE /standard-session`;
- `POST /analyses/**` sin Bearer.

## Usuario nuevo

1. `POST /users/identify` con `doi` y `doiType`.
2. `GET /legal-document` y conserve exactamente `contentHash`.
3. `POST /users` con CSRF:

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
  "profession": "Ingeniero industrial",
  "legalAcceptance": {
    "contentHash": "<hash obtenido por GET /legal-document>",
    "accepted": true
  }
}
```

El backend crea el User STANDARD y la aceptación en una transacción. Errores relevantes:

| HTTP | Código | Causa |
|---:|---|---|
| `400` | `LEGAL_ACCEPTANCE_REQUIRED` | Falta aceptación expresa o `accepted` no es `true`. |
| `403` | — | CSRF ausente o incorrecto. |
| `404` | `LEGAL_DOCUMENT_NOT_FOUND` | ADMIN aún no configuró contenido. |
| `409` | `LEGAL_DOCUMENT_CHANGED` | El contenido cambió mientras se completaba el formulario. |

## Crear y consultar la sesión

Ejecute `POST /standard-sessions` con CSRF:

```json
{
  "doi": "70000001",
  "doiType": "DNI"
}
```

Respuesta:

```json
{
  "status": "ACTIVE",
  "expiresAt": "...",
  "absoluteExpiresAt": "...",
  "consentRequired": false
}
```

`GET /standard-session` devuelve el mismo contrato y renueva solo la inactividad. No existe `requiredConsentVersion`.

## Usuario existente sin aceptación vigente

Si la sesión responde `consentRequired=true`:

1. ejecute `GET /legal-document`;
2. muestre/revise el contenido;
3. ejecute `POST /standard-session/consent` con CSRF y cookie de sesión:

```json
{
  "contentHash": "<hash vigente>",
  "decision": "ACCEPTED"
}
```

Respuesta:

```json
{
  "contentHash": "<hash vigente>",
  "decision": "ACCEPTED",
  "acceptedAt": "...",
  "sessionStatus": "ACTIVE",
  "consentRequired": false
}
```

Repetir `ACCEPTED` durante la misma vigencia efectiva es idempotente y conserva `acceptedAt`. `REJECTED` devuelve `acceptedAt=null`, mantiene la sesión `ACTIVE`, no limpia la cookie y no crea aceptación. Un hash obsoleto responde `409 LEGAL_DOCUMENT_CHANGED`.

## Crear y consultar análisis

Con cookie STANDARD, CSRF y `consentRequired=false`, ejecute `POST /analyses/text`, `/analyses/pdf` o `/analyses/audio`. Sin aceptación vigente responde `409 CONSENT_REQUIRED` antes de preprocessing o IA.

Para texto:

```json
{
  "description": "He diseñado un sistema modular de riego con sensores y válvulas independientes."
}
```

El POST responde `202`, `Location`, `Retry-After` y `analysisId`. Consulte `GET /analyses/{analysisId}` hasta `COMPLETED` o `FAILED`. La lectura sigue autorizada por la misma `session_id` que creó el análisis.

## Cambio legal durante una sesión

1. Acepte el hash A.
2. Cambie el contenido mediante `PUT /admin/legal-document`; el backend produce hash B.
3. `GET /standard-session` pasa a `consentRequired=true`.
4. Un nuevo análisis responde `409 CONSENT_REQUIRED`.
5. Acepte B y repita el análisis.
6. Vuelva a guardar exactamente A mediante `PUT /admin/legal-document`.
7. Aunque el hash vuelva a ser A, `GET /standard-session` sigue mostrando `consentRequired=true`.
8. Acepte A nuevamente para la nueva vigencia efectiva antes de crear otro análisis.

La aceptación A1 permanece como evidencia histórica y no se reutiliza en A2. Coincidir con un hash previamente aceptado no basta: la aceptación debe corresponder además a la vigencia efectiva actual del documento. En cambio, reenviar A cuando A ya es el contenido actual es un PUT idéntico: no cambia `updatedAt` ni invalida la aceptación vigente.

## Cerrar sesión

Ejecute `DELETE /standard-session` con CSRF. Responde `204`, revoca server-side y limpia la cookie. La aceptación del usuario permanece y una sesión futura devolverá `consentRequired=false` mientras no cambien el hash ni la vigencia efectiva actuales.
