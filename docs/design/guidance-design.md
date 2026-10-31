# Diseño implementado de guidance F1.8

**Versión:** F1.8 v0.3  
**Contrato ciudadano:** `analysis-guidance/1.0`  
**Política HTML:** `protection-guide-html/1.0`

## Alcance

Java mantiene un catálogo institucional versionado por modalidad. Cada `ProtectionGuide` contiene una
`shortDescription` de texto plano y un único `nextStepsContentHtml`. No existen como dominio
`routeType`, `requirements[]`, `steps[]`, `resources[]` ni formatos propietarios de editores.

El diagnóstico continúa siendo histórico. Guidance no se fija al análisis: cada llamada a
`GET /analyses/{analysisId}/guidance` resuelve la guía `ACTIVE` vigente de cada modalidad que aparece
explícitamente en el resultado persistido.

## Persistencia y lifecycle

Las migraciones V6 y V7 crean `protection_guides`, `protection_guide_version_counters` y las siete
guías iniciales `ACTIVE`. Las versiones se asignan por modalidad bloqueando su fila de contador; esto
evita colisiones sin introducir un lock global.

El lifecycle es `DRAFT → ACTIVE → RETIRED`. Solo `DRAFT` es editable, no existe DELETE y un índice
parcial garantiza como máximo un `ACTIVE` por modalidad. La activación bloquea la misma fila de
contador, retira y hace `flush` del `ACTIVE` anterior, y después promueve el nuevo borrador.

El hash usa UTF-8 y la forma canónica:

```text
protectionType + LF + shortDescription.trim() + LF + sanitizedNextStepsContentHtml
```

## Seguridad del contenido

El backend usa OWASP Java HTML Sanitizer. Admite `p`, `br`, `h1`, `h2`, `h3`, `h4`, `div`, `strong`,
`em`, `u`, `s`, `ul`, `ol`, `li`, `img` y `a`. Se conservan estilos inline mediante la política CSS
de OWASP. Las imágenes solo aceptan `src` con protocolo HTTPS y `alt`. En enlaces solo se conserva
`href` con protocolo HTTPS; todos se normalizan a `target="_blank" rel="noopener noreferrer"`.
No se persisten clases, IDs, handlers, etiquetas `<style>`, formularios ni protocolos ejecutables.

El HTML se sanitiza antes de guardar y nuevamente al activar. Debe conservar texto visible. La
descripción breve exige 30–240 caracteres, texto plano y ausencia de URLs.

## API

ADMIN con Bearer JWT:

```text
GET  /admin/protection-guides
GET  /admin/protection-guides/active/{protectionType}
GET  /admin/protection-guides/{guideId}
POST /admin/protection-guides
PUT  /admin/protection-guides/{guideId}
POST /admin/protection-guides/active/{protectionType}/draft
POST /admin/protection-guides/{guideId}/activate
```

Ciudadano/ADMIN propietario:

```text
GET /analyses/{analysisId}/guidance
```

El endpoint reutiliza `AnalysisAccessResolver`, devuelve `Cache-Control: no-store`, preserva el orden
de `protectionOptions` y soporta `analysis-result/1.0` y `analysis-result/2.0` sin reinterpretar el
JSON persistido. En V1, una opción histórica `OTHER` no puede mapearse de forma segura y produce
`GUIDANCE_UNSUPPORTED_RESULT_VERSION`.

`LIKELY` y `POSSIBLE` reciben `nextSteps.contentHtml`. `UNLIKELY` conserva la modalidad, nombre,
aplicabilidad, `guideVersion`, `shortDescription` y `lastVerifiedAt`, pero recibe `nextSteps: null`.
Si falta una guía `ACTIVE`, toda la respuesta falla con `GUIDANCE_CATALOG_INCOMPLETE`.

## Contenido inicial y trazabilidad

Las siete modalidades, descripciones breves y bloques HTML provienen del contenido entregado por
Indecopi. Los enlaces fueron validados manualmente por el responsable del proyecto el 29/10/2026.
La migración seed registra `createdSource=SYSTEM`, versión 1 y `lastVerifiedAt` en esa fecha.

## F1.8D — Registro de apertura de próximos pasos

`POST /analyses/{analysisId}/guidance/{protectionType}/view` registra que el modal de próximos pasos
fue abierto/renderizado y que el frontend notificó exitosamente esa apertura. No demuestra lectura,
comprensión ni duración. El frontend debe invocarlo de forma asíncrona después de mostrar el modal;
un fallo de tracking nunca debe ocultar el contenido.

La mutación admite únicamente una sesión `STANDARD` propietaria con CSRF válido. Reutiliza
`AnalysisAccessResolver`: un Bearer presente conserva precedencia exclusiva, un Bearer inválido no
cae a la cookie y un ADMIN válido recibe `403 GUIDANCE_VIEW_STANDARD_ONLY`.

La evidencia durable se guarda en `guidance_views` con el grano
`analysis_id + protection_type + guide_version`, sin DNI/CE, usuario, sesión, HTML ni otros datos
personales. `first_viewed_at` se genera en el servidor mediante `Clock`. La restricción única y un
`INSERT ... ON CONFLICT DO NOTHING` hacen idempotentes los reintentos y la concurrencia; después se
lee la fila resultante para devolver siempre el timestamp original.

Solo una modalidad explícita del resultado V1/V2 con applicability `LIKELY` o `POSSIBLE` puede
registrarse. `UNLIKELY` se rechaza porque no tiene `nextSteps`. La versión indicada debe pertenecer a
la modalidad y puede estar `ACTIVE` o `RETIRED`; esto preserva el evento cuando el usuario abre una
versión cargada antes de que el ADMIN active otra. Las versiones `DRAFT` o inexistentes se rechazan.
El contrato de `GET /analyses/{analysisId}/guidance` permanece sin cambios y no expone estado de
visualización.

## Ajustes F1.7 asociados

El DTO JSON conserva `scope` por compatibilidad, pero el PDF ya no renderiza la sección general
“Alcance del análisis”. El disclaimer, la nota del semáforo y las notas específicas del screening se
mantienen.

La evaluación de recursos genéticos sigue proviniendo exclusivamente de `AnalysisResultV2` y usa
placement determinístico: oculta para `NOT_INDICATED`; embebida en patente o certificado si hay un
solo anclaje; compartida si hay ambos; y standalone si no hay anclajes. Nunca se duplica.

## Límites

F1.8 no agrega Angular, no incorpora guidance al PDF histórico, no llama al LLM, no modifica
`result_json`, no valida enlaces por HTTP en runtime y no crea snapshots de guidance por análisis.
