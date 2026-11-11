# Roadmap funcional — IDENTIPAT-IA

**Estado:** F2 iniciada; F2-CONSUMO-01 completada e integrada en `develop`, pendiente de despliegue en PREPROD; F1.10A3 y F1.9 pendientes
**Versión del roadmap:** 1.12
**Última actualización:** 9 de noviembre de 2026
**Objetivo:** mantener una visión vigente de la secuencia funcional del producto, diferenciando lo ya materializado de las fases pendientes.

---

# 1. Estado actual

La estabilización técnica F0 está completada. El backend también materializa el diseño de dominio F1.0 y las verticales F1.1–F1.4.

| Fase | Alcance | Estado |
|---|---|---|
| F0.1 | Reorganización del repositorio | ✅ Completada |
| F0.2 | Maven Wrapper y build reproducible | ✅ Completada |
| F0.2A | Alineamiento arquitectónico y funcional | ✅ Completada |
| F0.3 | Seguridad y modelo de acceso | ✅ Completada |
| F0.4 | Configuración por ambientes y secretos | ✅ Completada |
| F0.5 | Flyway + Testcontainers | ✅ Completada |
| F0.6 | OpenAPI + Postman | ✅ Completada |
| F0.7 | Bootstrap de `preprocessing-service` | ✅ Completada |
| F0.8 | CI con GitHub Actions | ✅ Completada |
| F1.0 | Diseño del dominio de análisis | ✅ Completada |
| F1.1 | Sesión STANDARD + consentimiento | ✅ Implementada |
| F1.1B | Consentimiento legal persistente por usuario | ✅ Backend y frontend implementados |
| F1.2 | Capa de IA generativa en Java | ✅ Implementada |
| F1.3 | Análisis end-to-end desde texto | ✅ Implementada |
| F1.4 | Diagnóstico de propiedad intelectual | ✅ Implementada |
| F1.4B | Listado administrativo paginado de usuarios (mejora backend) | ✅ Implementada |
| F1.4C | Formalización del acceso ADMIN al análisis | ✅ Implementada |
| F1.4D | Exportación CSV administrativa de usuarios (mejora backend) | ✅ Implementada |
| F1.5 | Entrada mediante PDF | ✅ Implementada |
| F1.6 | Entrada mediante audio | ✅ Implementada |
| F1.7 | Reporte web, PDF y disclaimer versionado | ✅ Implementada |
| F1.8 | Catálogo institucional y guidance vigente | ✅ Implementada |
| F1.8D | Registro de visualización de próximos pasos | ✅ Implementada |
| F1.9 | Integración frontend y E2E | ⏳ Pendiente |
| F1.10A1 | Dataset operacional backend de análisis | ✅ Implementada |
| F1.10A2 | Serialización y endpoint CSV ADMIN | ✅ Implementada |
| F1.10A2B | API paginada administrativa de análisis | ✅ Implementada |
| F1.10A2C | Filtros y orden compartidos entre tabla y CSV | ✅ Implementada |
| F1.10A3 | Tabla y descarga CSV en Angular ADMIN | ⏳ Pendiente |
| F2 | Preparación para piloto y producción | 🚧 Iniciada: F2-CONSUMO-01 integrada en `develop`, no desplegada en PREPROD |

Los estados **implementada** indican que el alcance técnico existe en el backend y está cubierto por pruebas. No sustituyen la revisión funcional, jurídica, de seguridad o de aceptación que corresponda antes del piloto.

La base actual incluye:

- backend Java 21 / Spring Boot;
- PostgreSQL y migraciones Flyway;
- pruebas de integración con Testcontainers;
- OpenAPI / Swagger y colección Postman;
- `preprocessing-service` Python / FastAPI;
- CI con GitHub Actions;
- autenticación ADMIN mediante JWT;
- identificación STANDARD sin login;
- sesión STANDARD temporal mediante cookie segura y estado server-side;
- protección CSRF para las mutaciones STANDARD implementadas;
- contenido legal único con aceptación persistente por usuario, hash canónico y vigencia efectiva;
- análisis de texto asíncrono y durable;
- abstracción `GenerativeAiProvider` con OpenAI como adapter actual;
- prompts y JSON Schemas versionados;
- persistencia de intentos, resultados y metadata técnica;
- lease, retry y recuperación de trabajos interrumpidos.
- listado administrativo de usuarios con paginación, búsqueda, filtros y ordenamiento controlado.
- exportación CSV administrativa de todos los usuarios coincidentes, sin paginación y con protección contra CSV injection.
- dataset operacional de análisis con una fila por `Analysis`, filtros calendario en `America/Lima`,
  compatibilidad V1/V2 y reconstrucción batch del diagnóstico F1.7 sin endpoint ni CSV todavía.

---

# 2. Decisiones funcionales confirmadas

Estas decisiones son vigentes y obligatorias para las siguientes fases.

## 2.1 Sesión temporal para usuarios STANDARD

Los usuarios STANDARD:

- no tienen login ni contraseña;
- no reciben un JWT de autenticación;
- son reconocidos mediante DNI o Carné de Extranjería;
- disponen de una sesión temporal durante el uso de la herramienta;
- no tienen un panel visible de historial de consultas.

La solución vigente utiliza una sesión server-side. El navegador recibe un token aleatorio en una cookie `HttpOnly`; PostgreSQL conserva únicamente su representación HMAC. La sesión aplica TTL de inactividad y TTL absoluto, ambos configurables.

El DNI/CE sirve para identificación y deduplicación funcional. No autentica ni verifica criptográfica o presencialmente la identidad real de quien opera el navegador.

Solo un `STANDARD` activo puede crear esta sesión temporal. El ADMIN analiza con su JWT y no puede habilitar el flujo mediante DNI/CE, sesión STANDARD o consentimiento STANDARD.

## 2.2 Consentimiento y disclaimer

Son conceptos distintos:

```text
Consentimiento
→ tratamiento de datos personales

Disclaimer
→ naturaleza orientativa del resultado generado con IA
```

La aceptación legal vigente:

- pertenece al usuario STANDARD y al SHA-256 del contenido saneado;
- se reutiliza entre sesiones mientras el contenido no cambie;
- se crea atómicamente junto con el registro para usuarios nuevos;
- se valida antes de TEXT/PDF/AUDIO y antes de preprocessing o IA;
- no usa números de versión, drafts, estados ni publicación manual.

El disclaimer se incorpora como contenido institucional controlado por la aplicación desde F1.7. No debe confundirse con `AnalysisResult.warnings[]` ni generarse libremente por el modelo.

F1.1B fija la aceptación expresa antes de completar el registro STANDARD. El texto institucional definitivo y las decisiones de go-live siguen pendientes de validación legal.

## 2.3 Persistencia amplia de las consultas

Durante el desarrollo se conserva la información funcional y técnicamente relevante de cada consulta para permitir:

- trazabilidad y auditoría técnica;
- evaluación de calidad;
- análisis de errores;
- comparación de prompts y modelos;
- métricas;
- evolución del sistema;
- reproducibilidad razonable del resultado.

La persistencia interna no implica que el usuario STANDARD disponga de un historial visible.

La política definitiva de retención, minimización, anonimización, cifrado, borrado, acceso administrativo y tratamiento de información sensible debe cerrarse antes de producción en F2.1.

## 2.4 IA generativa como motor principal

Por decisión acordada con INDECOPI, el análisis funcional utiliza IA generativa.

No se implementará como requisito funcional un pipeline independiente de PLN/NLP o ML clásico basado en embeddings, features, reglas o clasificadores intermedios.

Sí se permiten transformaciones técnicas necesarias para preparar la entrada:

- extracción de texto desde PDF;
- transcripción de audio;
- validación de longitud;
- limpieza mínima de encoding;
- normalización de espacios y caracteres.

Estas transformaciones no constituyen un motor separado de análisis. El razonamiento funcional y la clasificación corresponden al proveedor de IA generativa mediante una salida estructurada y validada.

---

# 3. Arquitectura funcional vigente y objetivo

## 3.1 Flujo vigente para texto

```text
Usuario STANDARD
      ↓
Identificación DNI / CE
      ↓
Registro si no existe
      ↓
Sesión temporal + consentimiento vigente
      ↓
POST /analyses/text
      ↓
Analysis + AnalysisInput (RECEIVED)
      ↓
worker asíncrono con lease
      ↓
GenerativeAiProvider
      ↓
OpenAiGenerativeAiProvider
      ↓
validación de structured output
      ↓
AiInvocation + AnalysisResult
      ↓
Analysis (COMPLETED o FAILED)
```

El request HTTP no permanece abierto durante la inferencia. La creación devuelve `202 Accepted` y el resultado se consulta posteriormente mediante el identificador del análisis.

## 3.2 Flujo objetivo multiformato

```text
Entrada de usuario
 ┌────┼────────┐
 │    │        │
Texto PDF    Audio
 │    │        │
 │    └──→ preprocessing-service
 │             ↓
 │        texto extraído o
 │        transcripción
 │             ↓
 └─────────────┴─────→ Java
                       ↓
                    Analysis
                       ↓
              GenerativeAiProvider
                       ↓
        OpenAI actual / adapters futuros
                       ↓
              resultado estructurado
                       ↓
             persistencia y reporte
```

Principios:

- Angular consume únicamente el backend Java.
- Java es dueño de los casos de uso, la inferencia generativa y la persistencia.
- Python realiza preprocesamiento técnico especializado para PDF y audio.
- Python no accede a PostgreSQL ni ejecuta el diagnóstico de propiedad intelectual.
- El código de negocio depende de `GenerativeAiProvider`, no del SDK de un proveedor concreto.
- `OpenAiGenerativeAiProvider` es el adapter vigente.
- Gemini u otros proveedores podrán añadirse como adapters futuros sin cambiar el contrato central.

---

# 4. F1 — Construcción funcional del producto

# F1.0 — Diseño del dominio de análisis

**Estado:** completada y materializada posteriormente en F1.1–F1.3.

## Resultado alcanzado

Se definieron y materializaron:

- sesión temporal STANDARD;
- evidencia versionada de consentimiento;
- entidades `Analysis`, `AnalysisInput`, `AiInvocation` y `AnalysisResult`;
- lifecycle y estados del análisis;
- persistencia de entradas, intentos y resultados;
- contrato estructurado `AnalysisResult`;
- metadata de trazabilidad de IA;
- separación entre consentimiento y disclaimer.

El modelo implementado incorpora además control de concurrencia mediante lease, planificación del siguiente intento y datos de fallo.

---

# F1.1 — Sesión STANDARD + consentimiento

**Estado:** implementada; sujeta a revisión funcional/legal antes del piloto.

## Alcance implementado

- creación y cierre de sesiones temporales de análisis para STANDARD activo;
- asociación con el usuario reconocido o registrado;
- token aleatorio conservado en cookie `HttpOnly`;
- persistencia del token únicamente mediante HMAC;
- TTL de inactividad y absoluto;
- invalidación por expiración o usuario inactivo;
- tabla `consent_events` histórica para el contrato pre-F1.1B;
- bloqueo del análisis sin aceptación vigente;
- protección CSRF con `CookieCsrfTokenRepository` y patrón SPA de Spring Security;
- separación entre sesión STANDARD y autenticación ADMIN con JWT Bearer.

F1.4C cerró la compatibilidad temporal para ADMIN: `POST /standard-sessions` devuelve `409 STANDARD_SESSION_NOT_AVAILABLE` para ese tipo y las sesiones ADMIN históricas no son resolubles.

Las mutaciones `POST /analyses/**` sin Bearer están incluidas en la estrategia CSRF.

## Evolución F1.1B

El modelo por sesión quedó sustituido para operaciones nuevas por aceptación persistente `user_id + accepted_content_sha256 + accepted_document_updated_at`. Coincidir con un hash previamente aceptado no basta: la aceptación debe corresponder además a la vigencia efectiva actual del documento. `consent_events` se conserva como tabla legacy y no se convierte automáticamente en aceptación vigente.

---

# F1.1B — Consentimiento legal persistente por usuario

**Estado:** backend y frontend implementados; F1.9 y el E2E de navegador continúan pendientes.

Incluye `legal_document` singleton, historial técnico inmutable por hash, `user_terms_acceptances`, sanitización `legal-document-html/1.0`, hash `SHA-256(title.trim() + "\n" + sanitizedContentHtml)`, registro atómico, CSRF en `POST /users`, aceptación idempotente por vigencia efectiva y `terms_acceptance_id` en análisis nuevos. A→B→A exige reaceptar A porque A2 tiene un nuevo `legal_document.updated_at`; un PUT idéntico conserva ese valor. No existe versionado legal funcional, drafts ni publicación.

Angular adopta el contrato de esta fase. Esto no declara completada F1.9 ni el E2E de navegador.

---

# F1.2 — Capa de IA generativa en Java

**Estado:** implementada con OpenAI como adapter inicial; integración real validada.

## Arquitectura vigente

```text
AnalysisWorker
      ↓
GenerativeAiProvider
      ↑
OpenAiGenerativeAiProvider
```

## Alcance implementado

- interfaz agnóstica de proveedor;
- integración OpenAI mediante Responses API y SDK oficial;
- prompts versionados;
- JSON Schemas versionados;
- structured output estricto;
- validación local del resultado contra el schema;
- configuración por ambiente y API key externalizada;
- timeout y errores controlados;
- retries del SDK deshabilitados para que la aplicación controle los intentos;
- `store(false)` en las solicitudes al proveedor;
- tests con proveedor simulado;
- persistencia de proveedor, modelo, tokens, latencia y metadata disponible;
- aislamiento de las clases del SDK dentro del adapter OpenAI.

Gemini queda reservado como adapter futuro y no forma parte de la implementación vigente.

## Mejoras técnicas identificadas

- conservar metadata útil cuando OpenAI devuelve una respuesta `incomplete` o contenido inválido;
- validar al iniciar que la duración del lease sea mayor que el timeout del proveedor más un margen operativo;
- fortalecer la arbitrariedad de la transición terminal alrededor del vencimiento del lease.

---

# F1.3 — Análisis end-to-end desde texto

**Estado:** implementado y validado en backend mediante pruebas de integración y ejecución real contra OpenAI.

## Flujo implementado

```text
POST /analyses/text
      ↓
validar sesión, CSRF, consentimiento y longitud
      ↓
persistir Analysis(RECEIVED) + AnalysisInput(TEXT)
      ↓
202 Accepted
      ↓
worker reclama trabajo con FOR UPDATE SKIP LOCKED
      ↓
crear AiInvocation(STARTED)
      ↓
llamar al proveedor fuera de una transacción de base de datos
      ↓
validar y persistir resultado
      ↓
Analysis(COMPLETED) o retry/FAILED
```

## Alcance implementado

- `POST /analyses/text`;
- `GET /analyses/{analysisId}`;
- validación de longitud mínima y máxima;
- normalización técnica conservando texto original y procesado;
- aislamiento de análisis por sesión;
- procesamiento asíncrono durable;
- claim concurrente con `FOR UPDATE SKIP LOCKED`;
- lease con propietario y vencimiento;
- intentos persistidos antes de llamar al proveedor;
- retry sin bloquear threads mediante `next_attempt_at`;
- recuperación de ejecuciones abandonadas;
- límite de intentos y estados terminales;
- atomicidad entre persistencia del resultado y transición a `COMPLETED`;
- respuesta pública sin metadata interna del proveedor.

La vertical de texto ya constituye la base reutilizable para PDF y audio.

---

# F1.4 — Diagnóstico de propiedad intelectual

**Estado:** implementada.

## Objetivo

Estabilizar el contrato jurídico-funcional que debe producir la IA y alinearlo con el diagnóstico requerido por el proyecto. Esta fase no debe limitarse a ampliar el prompt.

## Alcance implementado

1. Criterios jurídico-funcionales documentados y golden set J01–J18.
2. Screening limitado de los artículos 15 y 20 de la Decisión 486, sin examen completo de patentabilidad.
3. Siete modalidades controladas y concurrentes:
   - patente de invención;
   - modelo de utilidad;
   - diseño industrial;
   - signos distintivos;
   - derecho de autor;
   - certificado de obtentor;
   - secreto empresarial.
4. `AnalysisResult` y JSON Schema 2.0 con lectura histórica 1.0.
5. Prompt jurídico-funcional `intellectual-property-analysis/0.2`.
6. Alerta separada de acceso a recursos genéticos.
7. Validaciones de schema, dominio, fake provider e integración PostgreSQL.

## Resultado alcanzado

La IA responde mediante structured output `analysis-result/2.0`, no como texto libre sin contrato.

Java debe:

- validar el schema;
- rechazar respuestas inválidas;
- persistir el JSON estructurado y la explicación legible;
- registrar prompt, schema, proveedor y modelo utilizados;
- mantener trazabilidad por ejecución.

---

# F1.4C — Formalización del acceso ADMIN al análisis

**Estado:** implementada.

## Alcance implementado

- ADMIN crea y consulta análisis en los endpoints comunes con Bearer JWT;
- la identidad se deriva del JWT y se valida contra un ADMIN activo en PostgreSQL;
- los análisis ADMIN conservan `user_id` y guardan `session_id`/`terms_acceptance_id` como `NULL`;
- STANDARD permanece sin JWT, con sesión, consentimiento y CSRF;
- Bearer tiene precedencia sobre cookie y un Bearer inválido nunca hace fallback;
- ADMIN ya no puede crear una sesión pública por DNI/CE y las sesiones ADMIN históricas quedan inutilizables.

El frontend ADMIN debe migrar del flujo anterior `standard-session → consent → análisis` a `login → JWT → análisis`. El frontend STANDARD no cambia. Esta fase no altera F1.4D ni las fases posteriores.

---

# F1.4D — Exportación CSV administrativa de usuarios

**Estado:** implementada.

## Alcance implementado

- `GET /users/export` protegido con JWT ADMIN;
- reutilización de búsqueda, filtros y ordenamiento de `GET /users`;
- exportación de todos los registros coincidentes sin paginación;
- CSV UTF-8 con BOM, CRLF, escape de campos y mitigación de CSV injection;
- pruebas de seguridad, contrato HTTP, contenido y PostgreSQL real.

La implementación actual construye el CSV en memoria. Si el volumen real lo justifica, podrá evolucionar a procesamiento por lotes o streaming sin cambiar el contrato HTTP.

---

# F1.5 — Entrada mediante PDF

**Estado:** implementada.

## Objetivo

Agregar PDF reutilizando el pipeline de análisis existente.

```text
PDF → Java → preprocessing-service → texto extraído → Analysis existente
```

## Resultado implementado

- `POST /analyses/pdf` multipart para STANDARD (sesión, consentimiento y CSRF) y ADMIN (Bearer);
- validación de `application/pdf`, firma `%PDF-`, 10 MiB y 50 páginas por defecto;
- extracción privada `POST /v1/pdf/extract` con `pypdf`, sin OCR ni servicios externos;
- errores saneados para PDF inválido, cifrado, sin texto, límites y servicio no disponible;
- extracción síncrona antes de aceptar; si falla, no se crea `Analysis`;
- persistencia de texto original/procesado y metadata mínima con SHA-256, sin binario ni filename;
- convergencia en el mismo worker, prompt, schema, proveedor y `AnalysisResult` de TEXT.

---

# F1.6 — Entrada mediante audio

**Estado:** implementada.

## Objetivo

Agregar voz reutilizando el pipeline central.

```text
Audio → Java → preprocessing-service → transcripción → Analysis existente
```

## Resultado implementado

- `POST /analyses/audio` multipart para STANDARD y ADMIN con la misma resolución dual;
- WebM, MP3/MPEG, MP4/M4A, WAV, OGG y FLAC; 20 MiB y 180 segundos por defecto;
- español mediante `faster-whisper` local, modelo multilingüe `base`, CPU INT8 y VAD;
- decodificación PyAV en memoria y validación de duración antes de inferencia;
- warm-up en background, singleton por proceso, readiness `/v1/audio/health` y cache Docker;
- timeouts Java separados para conexión, PDF y audio;
- errores públicos/internos saneados y sin detalles nativos;
- transcripción original/normalizada y metadata mínima con SHA-256, sin audio ni filename;
- mismo `AnalysisCreationPersistenceService`, worker, prompt, schema, proveedor y resultado;
- sin STT cloud, streaming, realtime, diarización ni traducción.

F2.0 evaluará calidad y elección de modelo; F2.3 definirá el empaquetado/provisionamiento offline
para producción.

---

# F1.7 — Reporte web + PDF + disclaimer

**Estado:** implementada en backend.

## Objetivo

Construir la salida formal del diagnóstico a partir del mismo `AnalysisResult`.

El resultado web y el PDF deben presentar de forma comprensible:

- resumen;
- evaluación;
- modalidades sugeridas;
- explicación;
- recomendaciones;
- advertencias.

El disclaimer institucional debe indicar que el análisis es orientativo, que la IA es complementaria, que no sustituye una evaluación especializada y que no constituye una decisión oficial.

El disclaimer debe ser controlado y versionable. No debe existir una segunda lógica de diagnóstico exclusiva para el PDF.

---

# F1.8 — Orientación y recursos asociados

**Estado:** implementada en backend.

## Objetivo

Complementar el diagnóstico con requisitos, pasos, formularios, tutoriales, enlaces oficiales y servicios relacionados según la modalidad sugerida.

Esta información debe provenir preferentemente de un catálogo institucional controlado y versionado que Java asocie al resultado. No se debe confiar exclusivamente en generación libre del LLM.

La implementación usa `ProtectionGuide` versionado por modalidad, HTML enriquecido sanitizado,
API ADMIN y `GET /analyses/{analysisId}/guidance`. Incluye siete guías iniciales `ACTIVE`, resolución
dinámica del catálogo vigente, seguridad dual STANDARD/ADMIN, OpenAPI, Postman y pruebas con
Testcontainers. La presentación Angular permanece en F1.9.

## F1.8D — Registro de visualización de próximos pasos

**Estado:** implementada en backend.

Se agregó evidencia durable de la primera apertura del modal por un usuario STANDARD mediante
`POST /analyses/{analysisId}/guidance/{protectionType}/view`. El registro es idempotente y seguro ante
concurrencia, conserva la versión exacta `ACTIVE` o `RETIRED` mostrada y no incorpora PII. Exige
sesión propietaria y CSRF; ADMIN no registra. La fase no agrega dashboard, lectura de métricas ni
implementación Angular y no modifica el contrato de `GET /guidance`.

---

# F1.9 — Integración frontend y E2E

**Estado:** pendiente.

## Objetivo

Integrar en Angular el flujo completo:

```text
DNI / CE
   ↓
reconocimiento o registro
   ↓
consentimiento y sesión temporal
   ↓
texto / PDF / audio
   ↓
procesamiento asíncrono
   ↓
resultado y reporte
```

## Alcance

- integración con los contratos backend;
- UX de espera, polling y estados terminales;
- manejo de errores;
- carga de PDF/audio;
- presentación del diagnóstico;
- descarga del reporte;
- pruebas E2E y accesibilidad;
- incorporación del frontend al CI.

---

# F1.10A — Reporte operativo CSV de análisis

## F1.10A1 — Dataset operacional backend

**Estado:** implementada.

El backend dispone de un read-model tipado de 21 campos con una fila por `Analysis`. Consulta los
análisis por rango calendario peruano, conserva el orden `createdAt ASC, analysisId ASC`, incorpora
estados no terminales y `FAILED`, usa el perfil actual del usuario y deriva el origen histórico desde
`sessionId`.

Los resultados `analysis-result/1.0` y `analysis-result/2.0` se leen mediante el lector versionado.
Las modalidades, aplicabilidad y conclusión reutilizan la presentación F1.7; el campo de reporte se
ensambla con el contexto y disclaimer históricos y se renderiza a texto plano determinista. La carga
de usuarios, resultados, contextos, disclaimers y existencia de guidance se realiza en batch.

Esta fase no agrega endpoint, CSV, generación física de PDF, Angular, tablas, índices ni migraciones.
El perfil mostrado es el vigente al construir el dataset porque no existe snapshot por análisis.

## F1.10A2 — CSV y endpoint ADMIN

**Estado:** implementada.

El endpoint `GET /admin/analyses/export.csv` reutiliza exclusivamente el dataset A1. La respuesta
ADMIN-only contiene las 20 columnas contractuales, UTF-8 BOM,
registros CRLF, quoting y escape deterministas, protección contra formula injection, nombres de archivo
según el rango y `Cache-Control: no-store, private`. No se agregó ownership por análisis, acceso
STANDARD, consulta por fila, migración ni cambio Angular.

## F1.10A2B — API paginada ADMIN

**Estado:** implementada.

El endpoint `GET /admin/analyses` ofrece `PagedResponse` para la futura tabla Angular, con búsqueda
por perfil actual, rango calendario peruano, estado, origen, tipo de entrada y ordenamiento estable.
La selección, el count y la paginación se ejecutan en PostgreSQL mediante `Specification`; la
búsqueda usa un `EXISTS` correlacionado sobre `users` sin agregar una relación JPA. Solo los IDs de
la página se enriquecen en batch con usuarios, resultados V1/V2 y existencia de guidance. La
presentación de conclusión y modalidades fue extraída como pieza compartida con A1, preservando
OTHER y el contrato CSV A2 de 20 columnas.

## F1.10A2C — Filtros y orden compartidos para CSV

**Estado:** implementada.

`GET /admin/analyses/export.csv` admite `search`, `from`, `to`, `status`, `origin`, `inputType` y
`sort`, pero no `page/size`. Tabla y exportación comparten normalización, validación y
`OperationalAnalysisSpecifications`; la tabla usa `Specification + Pageable` y el CSV
`Specification + Sort`, por lo que exporta todo el conjunto lógico filtrado en el mismo orden global.
Sin sort mantiene `createdAt ASC, analysisId ASC` por compatibilidad con A2; A3 enviará el sort activo
de la tabla, incluido `createdAt,desc` cuando corresponda. El enrichment completo y las 20 columnas A1
permanecen intactos.

## F1.10A3 — Tabla y descarga Angular ADMIN

**Estado:** pendiente.

Angular deberá consumir `GET /admin/analyses` para filtros, tabla, paginación y ordenamiento, y
`GET /admin/analyses/export.csv` para la descarga completa, enviando siempre el mismo estado de filtros
y orden activo. A2C no implementa frontend.

F1.10A completa permanece pendiente hasta cerrar A3.

---

# 5. F2 — Preparación para piloto y producción

F2 ya comenzó con la implementación de F2-CONSUMO-01. Las fases oficiales F2.0, F2.1, F2.2 y
F2.3 se conservan; los identificadores siguientes representan entregas técnicas dentro de ellas y no
las sustituyen ni las renumeran.

F2-QUALITY-01 y F2-DATA-01 pueden comenzar en paralelo entre sí y con las entregas de hardening de
F2.2. La aprobación o el merge de un PR acredita integración de código, pero no equivale a un
despliegue en PREPROD, a una validación QA ni a la aceptación institucional de INDECOPI.

# F2.0 — Evaluación de calidad

## F2-QUALITY-01 — Evaluación funcional y jurídica de la IA

**Objetivo:** demostrar con evidencia reproducible que el diagnóstico asistido por IA alcanza un nivel
funcional y jurídico acordado para el piloto.

**Alcance:**

- dataset representativo y versionado de casos de prueba, incluidos casos límite y adversariales;
- protocolo de evaluación por expertos funcionales y jurídicos;
- métricas, umbrales y criterios mínimos de aceptación;
- evaluación de consistencia, respuestas inválidas, omisiones y alucinaciones;
- regresión de prompts, schemas y versiones de modelo.

**Dependencias:** verticales F1.2–F1.8 implementadas y disponibilidad de expertos y criterios de
INDECOPI. Puede comenzar en paralelo con F2-DATA-01 y con F2.2; sus resultados son entrada obligatoria
para F2-RELEASE-01 y F2-UAT-01.

**Estado:** pendiente.

**Criterios de aceptación:**

- dataset, protocolo, responsables, métricas y umbrales aprobados y versionados;
- línea base y regresiones ejecutadas de forma reproducible sobre el proveedor, modelo, prompt y
  schema identificados;
- hallazgos jurídicos y funcionales clasificados, con bloqueantes resueltos o aceptados formalmente;
- evidencia suficiente para decidir el pase a QA/PREPROD sin confundirlo con la aceptación final.

# F2.1 — Privacidad, retención y gobierno de datos

## F2-DATA-01 — Privacidad y ciclo de vida de los datos

**Objetivo:** definir y aplicar la política productiva de privacidad, minimización, retención, acceso,
confidencialidad y eliminación de datos.

**Alcance:**

- clasificación y minimización de consultas, archivos, transcripciones, resultados, prompts
  renderizados y respuestas raw/structured;
- plazos de retención, anonimización y eliminación lógica y física;
- cifrado, control de acceso administrativo, confidencialidad y trazabilidad de accesos;
- saneamiento de logs y tratamiento de información sensible o reservada;
- procedimiento para ejercer derechos de acceso y eliminación;
- reglas para usar casos reales con proveedores externos y adecuación de endpoints ADMIN.

**Dependencias:** decisiones legales, de seguridad y de gobierno de INDECOPI, además del inventario de
datos implementado en F1. Puede comenzar en paralelo con F2-QUALITY-01 y F2.2; sus políticas deben
estar cerradas antes de F2-RELEASE-01.

**Estado:** pendiente.

**Criterios de aceptación:**

- inventario, responsables, base de tratamiento, matriz de acceso y plazos de retención aprobados;
- controles de minimización, confidencialidad, acceso y eliminación implementados y probados;
- logs verificados sin inputs, prompts, respuestas ni resultados completos, y sin metadata técnica
  expuesta a STANDARD;
- procedimiento de eliminación y evidencia de cumplimiento validados en un ambiente no productivo.

# F2.2 — Hardening y operación

## F2-CONSUMO-01 — Admisión durable, cuotas y capacidad

**Objetivo:** proteger los recursos compartidos antes del procesamiento costoso y aplicar límites de
uso consistentes entre múltiples instancias del backend.

**Alcance:**

- admisión durable en PostgreSQL con reserva, confirmación, liberación y expiración;
- cuotas móviles para STANDARD y máximo de un análisis STANDARD activo;
- capacidad global para análisis y límite global de preprocesamiento AUDIO;
- aplicación a TEXT, PDF y AUDIO antes de invocar Python o el proveedor LLM;
- respuestas `429`/`503`, códigos estables y `Retry-After`;
- configuración externalizada, validación al arrancar, backfill y pruebas concurrentes con PostgreSQL.

El diseño y las decisiones implementadas se detallan en
[F2-CONSUMO: control de admisión, cuotas y concurrencia](design/f2-usage-control-design.md).

**Dependencias:** F1.3, F1.5 y F1.6, el lifecycle durable de `Analysis` y PostgreSQL/Flyway.

**Estado:** completada e integrada en `develop` mediante F2-CONSUMO-01. No está desplegada ni validada
en PREPROD.

**Criterios de aceptación:** cumplidos a nivel de implementación e integración:

- las solicitudes rechazadas por cuota o capacidad no invocan PDF, Whisper ni LLM;
- una solicitud inválida o con preprocesamiento fallido libera su reserva y no consume cuota
  definitiva;
- cuotas y slots se serializan en PostgreSQL y se mantienen bajo concurrencia de dos instancias;
- ADMIN conserva su autenticación y exención de cuotas personales, pero participa en capacidad global
  y AUDIO;
- migración, aislamiento STANDARD/ADMIN, trazabilidad y suites automatizadas permanecen válidos.

El despliegue y la comprobación operativa en PREPROD pertenecen a F2-RELEASE-01.

## F2-CONSUMO-02 — Idempotencia HTTP y manejo de saturación en Angular

**Objetivo:** impedir análisis duplicados ante reintentos HTTP y ofrecer una respuesta de interfaz clara
cuando una solicitud supera cuota o capacidad.

**Alcance:**

- contrato `Idempotency-Key` para TEXT, PDF y AUDIO;
- identidad del request por actor, formato y hash del payload, con retención y conflictos de clave;
- repetición segura de la respuesta aceptada sin crear otro análisis ni consumir otra cuota;
- manejo en Angular de HTTP `429` y `503`, incluido `Retry-After` cuando esté disponible;
- prevención de reenvíos duplicados mientras exista una solicitud activa conocida y pruebas
  STANDARD/ADMIN.

**Dependencias:** F2-CONSUMO-01 y contratos actuales de creación de análisis; se coordina con F1.9 para
el E2E de navegador.

**Estado:** pendiente.

**Criterios de aceptación:**

- una misma clave y request devuelve el mismo resultado de admisión sin duplicar `Analysis` ni cuota;
- reutilizar la clave con otro payload produce un conflicto estable y documentado;
- concurrencia, expiración y reintento después de timeout se prueban sobre PostgreSQL real;
- Angular presenta mensajes accionables para `429`/`503` y evita reenvíos accidentales;
- OpenAPI, Postman y pruebas E2E reflejan el contrato final.

## F2-CONSUMO-03 — Rate limiting y controles contra abuso

**Objetivo:** reducir automatización, evasión de cuotas y consumo abusivo sin tratar el DNI/CE como
factor de autenticación.

**Alcance:**

- rate limiting por IP con política explícita para proxies confiables e IP real;
- protección graduada contra automatización y patrones anómalos;
- controles de abuso compatibles con STANDARD sin login y ADMIN con JWT;
- auditoría y alertas saneadas, sin PII ni contenido de análisis.
- métricas agregadas de idempotencia `new_claim`, `replayed_accepted`, `in_progress`,
  `payload_conflict`, `expired_claim_recovered` y `release_before_accept`; esta deuda se difiere
  expresamente desde F2-CONSUMO-02 y no autoriza registrar claves, digests, DNI/CE, textos,
  credenciales ni metadata técnica sensible.

**Dependencias:** F2-CONSUMO-01; el comportamiento de reintentos debe alinearse con
F2-CONSUMO-02 y la telemetría con F2-OPS-01.

**Estado:** pendiente.

**Criterios de aceptación:**

- límites, ventanas, excepciones y resolución de IP están documentados y probados detrás del proxy
  objetivo;
- la automatización por encima de los umbrales se rechaza con respuestas estables y observables;
- no se confía en DNI/CE como secreto ni se bloquean indebidamente flujos ADMIN/STANDARD válidos;
- pruebas de evasión, concurrencia y falsos positivos cumplen los umbrales acordados.

## F2-OPS-01 — Observabilidad, seguridad y resiliencia operativa

**Objetivo:** hacer operable y recuperable la solución bajo fallos de infraestructura, proveedores y
carga real.

**Alcance:**

- logging estructurado y correlation ID sin PII ni contenido sensible;
- métricas, health, readiness, monitoreo, alertas y tableros operativos;
- revisión de timeouts, lease, circuit breakers y degradación del proveedor;
- seguridad de configuración, secrets y aprovisionamiento inicial ADMIN;
- backups, restauración, recuperación ante desastres y runbooks;
- fortalecimiento de concurrencia en transiciones terminales.

**Dependencias:** base técnica F1 y controles de F2-CONSUMO-01; incorpora las políticas de
F2-DATA-01 a medida que se aprueben y alimenta F2-LOAD-01.

**Estado:** pendiente.

**Criterios de aceptación:**

- métricas, alertas y estados de salud permiten detectar saturación y fallos sin exponer datos
  sensibles;
- relaciones timeout/lease y escenarios de degradación están documentados y probados;
- backup y restauración se ejecutan con evidencia y objetivos de recuperación acordados;
- secrets, accesos operativos y aprovisionamiento ADMIN superan la revisión de seguridad;
- runbooks cubren incidentes, rollback y recuperación.

## F2-LOAD-01 — Carga, capacidad y dimensionamiento

**Objetivo:** medir capacidad y tiempos del sistema completo para dimensionar el piloto y fijar límites
operativos sustentados en evidencia.

**Alcance:**

- escenarios representativos de TEXT, PDF y AUDIO con concurrencia y mezcla de usuarios;
- medición de throughput, latencias, colas, errores, saturación y recuperación;
- validación de cuotas, capacidad global y límites AUDIO bajo carga;
- dimensionamiento de backend, PostgreSQL, preprocessing y proveedor LLM;
- identificación de cuellos de botella y recomendación de parámetros operativos.

**Dependencias:** F2-CONSUMO-01 y una línea base observable de F2-OPS-01. Los escenarios finales deben
incluir F2-CONSUMO-02 y F2-CONSUMO-03.

**Estado:** pendiente.

**Criterios de aceptación:**

- plan y datos de prueba reproducibles, sin información real reservada;
- resultados comparados con objetivos acordados de capacidad, tiempos y tasa de error;
- límites y configuración recomendados documentados con margen y supuestos explícitos;
- cuellos de botella bloqueantes corregidos o aceptados con plan de mitigación;
- informe de dimensionamiento aprobado como entrada de F2-RELEASE-01.

# F2.3 — Despliegue y aceptación

## F2-RELEASE-01 — Validación QA/PREPROD y preparación productiva

**Objetivo:** demostrar que la versión candidata puede migrarse, desplegarse, operarse y recuperarse de
forma controlada antes de producción.

**Alcance:**

- validación integral en QA/PREPROD de frontend, backend, PostgreSQL y preprocessing-service;
- migraciones Flyway, empaquetado, imágenes, configuración, secrets y estrategia CI/CD acordada;
- smoke, regresión, seguridad, rendimiento y compatibilidad de contratos;
- guía de pase, rollback, backups, restauración y checklist de producción;
- manuales, capacitación operativa y resolución de hallazgos de release.

**Dependencias:** F1.9 y F1.10A3 cerradas; F2-QUALITY-01, F2-DATA-01, F2-CONSUMO-02,
F2-CONSUMO-03, F2-OPS-01 y F2-LOAD-01 aceptadas. Incluye desplegar y validar en PREPROD lo ya
integrado por F2-CONSUMO-01.

**Estado:** pendiente. F2-CONSUMO-01 está integrada en `develop`, pero eso no constituye despliegue ni
validación en PREPROD.

**Criterios de aceptación:**

- candidato reproducible desplegado en PREPROD con migraciones y smoke exitosos;
- regresión funcional, seguridad, calidad y carga dentro de los umbrales acordados;
- backup, restauración y rollback ensayados con evidencia;
- configuración, secrets, monitoreo, runbooks, manuales y checklist aprobados;
- no quedan bloqueantes de release abiertos antes de solicitar UAT.

## F2-UAT-01 — Aceptación y conformidad institucional

**Objetivo:** obtener la conformidad funcional, jurídica y operativa de INDECOPI sobre la solución
candidata para el piloto o producción.

**Alcance:**

- plan, casos, datos y participantes de pruebas de aceptación;
- ejecución de journeys STANDARD y ADMIN en el ambiente candidato;
- validación institucional del diagnóstico, disclaimer, guidance, privacidad y operación;
- registro de hallazgos, correcciones, revalidaciones y decisión formal;
- acta o evidencia equivalente de conformidad y condiciones de salida.

**Dependencias:** F2-RELEASE-01 completada y ambiente PREPROD estable; disponibilidad de usuarios y
responsables funcionales, jurídicos y operativos de INDECOPI.

**Estado:** pendiente.

**Criterios de aceptación:**

- casos UAT y criterios de conformidad aprobados antes de ejecutar;
- pruebas completadas con evidencia y trazabilidad de hallazgos;
- bloqueantes corregidos y revalidados, o aceptados formalmente por los responsables;
- conformidad institucional documentada para el alcance acordado.

El merge o la aprobación de los PR técnicos anteriores no sustituye este hito de aceptación.

---

# 6. Dependencias entre fases

```text
F0 + F1.0–F1.8D completadas
          ├──────────────────────→ F1.9 pendiente ───────────────────────┐
          ├→ F1.10A1/A2/A2B/A2C → F1.10A3 pendiente ────────────────────┤
          ├→ F2.0 / F2-QUALITY-01 pendiente ────────────────────────────┤
          ├→ F2.1 / F2-DATA-01 pendiente ───────────────────────────────┤
          └→ F2.2 / F2-CONSUMO-01 completada e integrada                │
                         │                                              │
                         ├→ F2-CONSUMO-02 pendiente ─┐                  │
                         ├→ F2-CONSUMO-03 pendiente ─┼→ F2-LOAD-01 ─────┤
                         └→ F2-OPS-01 pendiente ─────┘                  │
                                                                        ↓
                                                    F2.3 / F2-RELEASE-01
                                                                        ↓
                                                              F2-UAT-01
```

F2-QUALITY-01 y F2-DATA-01 no necesitan esperar a que termine el hardening: pueden avanzar en
paralelo con F2-CONSUMO-02, F2-CONSUMO-03 y F2-OPS-01. F2-LOAD-01 requiere una base observable y los
controles de admisión; su escenario final incorpora la idempotencia y la protección contra abuso.
F2-RELEASE-01 consolida los pendientes funcionales, de calidad, datos y operación antes de UAT.

---

# 7. Principios para las siguientes fases

## Vertical antes que amplitud

La vertical de texto ya está implementada:

```text
texto → Analysis → IA → resultado persistido
```

Las nuevas entradas deben converger al mismo núcleo:

```text
PDF → texto ─┐
             ├→ Analysis → GenerativeAiProvider
audio → texto┘
```

## Una sola lógica de análisis

No duplicar reglas de diagnóstico por tipo de entrada, reporte o canal.

## IA sustituible

```text
Caso de uso
     ↓
GenerativeAiProvider
     ├── OpenAiGenerativeAiProvider (actual)
     └── otros adapters (futuro)
```

## Structured output

El modelo principal del resultado debe ser un objeto estructurado, versionado y validado, no un `String` libre.

## Persistencia trazable

Cada ejecución debe permitir determinar:

- quién realizó la consulta y en qué sesión;
- qué consentimiento vigente la habilitó;
- qué entrada y texto se procesaron;
- qué prompt, schema, proveedor y modelo se usaron;
- qué intentos ocurrieron;
- qué resultado se aceptó;
- cuánto tardó;
- si falló y por qué.

## Privacidad desde el diseño

La trazabilidad amplia de desarrollo debe evolucionar hacia una política productiva explícita de minimización, acceso, retención y borrado.

## Sin pipeline NLP separado

No introducir embeddings, clasificadores o pipelines NLP auxiliares salvo decisión funcional posterior explícita.

---

# 8. Próximo paso inmediato

F2 ya está en ejecución. Los próximos frentes son:

```text
Pendientes funcionales: F1.10A3 + F1.9
Trabajo F2 en paralelo: F2-QUALITY-01 + F2-DATA-01
Siguiente hardening: F2-CONSUMO-02, F2-CONSUMO-03 y F2-OPS-01
```

El cierre de F1.10A3 y F1.9 completa la integración Angular y el E2E que necesita el candidato. En
paralelo deben iniciarse la evaluación de calidad, la política de datos y el hardening pendiente. El
orden de despliegue continúa bajo F2-RELEASE-01: la integración de F2-CONSUMO-01 en `develop` no
autoriza por sí sola un despliegue en PREPROD ni acredita aceptación institucional.
