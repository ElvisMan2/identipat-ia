# Roadmap funcional — IDENTIPAT-IA

**Estado:** F0 y F1.0 completadas; F1.1–F1.7 implementadas; F1.8 es la siguiente fase
**Versión del roadmap:** 1.6
**Última actualización:** 26 de octubre de 2026
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
| F1.2 | Capa de IA generativa en Java | ✅ Implementada |
| F1.3 | Análisis end-to-end desde texto | ✅ Implementada |
| F1.4 | Diagnóstico de propiedad intelectual | ✅ Implementada |
| F1.4B | Listado administrativo paginado de usuarios (mejora backend) | ✅ Implementada |
| F1.4C | Formalización del acceso ADMIN al análisis | ✅ Implementada |
| F1.4D | Exportación CSV administrativa de usuarios (mejora backend) | ✅ Implementada |
| F1.5 | Entrada mediante PDF | ✅ Implementada |
| F1.6 | Entrada mediante audio | ✅ Implementada |
| F1.7 | Reporte web, PDF y disclaimer versionado | ✅ Implementada |
| F1.8–F1.9 | Recursos e integración E2E | ⏳ Pendientes |
| F2 | Preparación para piloto y producción | ⏳ Pendiente |

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
- consentimiento versionado con evidencia persistente;
- análisis de texto asíncrono y durable;
- abstracción `GenerativeAiProvider` con OpenAI como adapter actual;
- prompts y JSON Schemas versionados;
- persistencia de intentos, resultados y metadata técnica;
- lease, retry y recuperación de trabajos interrumpidos.
- listado administrativo de usuarios con paginación, búsqueda, filtros y ordenamiento controlado.
- exportación CSV administrativa de todos los usuarios coincidentes, sin paginación y con protección contra CSV injection.

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

El consentimiento vigente:

- se registra mediante eventos inmutables;
- queda asociado al usuario y a la sesión STANDARD;
- conserva versión y hash del documento aceptado;
- se valida antes de admitir un análisis.

El disclaimer se incorporará como contenido institucional controlado por la aplicación en F1.7. No debe confundirse con `AnalysisResult.warnings[]` ni generarse libremente por el modelo.

Debe confirmarse con el responsable funcional/legal el momento exacto del consentimiento respecto al registro inicial de datos personales.

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
- eventos inmutables de consentimiento;
- versión y hash del documento de consentimiento;
- bloqueo del análisis sin aceptación vigente;
- protección CSRF con `CookieCsrfTokenRepository` y patrón SPA de Spring Security;
- separación entre sesión STANDARD y autenticación ADMIN con JWT Bearer.

F1.4C cerró la compatibilidad temporal para ADMIN: `POST /standard-sessions` devuelve `409 STANDARD_SESSION_NOT_AVAILABLE` para ese tipo y las sesiones ADMIN históricas no son resolubles.

La mutación `POST /analyses/text` ya está incluida en la estrategia CSRF. Las futuras mutaciones PDF/audio deberán quedar igualmente cubiertas, preferiblemente mediante una regla general aplicable a `/analyses/**`.

## Pendiente funcional/legal

Confirmar si el consentimiento debe producirse antes de persistir los datos personales del registro inicial y, de ser necesario, ajustar el flujo.

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
- los análisis ADMIN conservan `user_id` y guardan `session_id`/`consent_event_id` como `NULL`;
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

**Estado:** pendiente.

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

**Estado:** pendiente.

## Objetivo

Complementar el diagnóstico con requisitos, pasos, formularios, tutoriales, enlaces oficiales y servicios relacionados según la modalidad sugerida.

Esta información debe provenir preferentemente de un catálogo institucional controlado y versionado que Java asocie al resultado. No se debe confiar exclusivamente en generación libre del LLM.

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

# 5. F2 — Preparación para piloto y producción

# F2.0 — Evaluación de calidad

- dataset de casos de prueba;
- evaluación experta;
- métricas y criterios mínimos de aceptación;
- consistencia y regresión de prompts;
- comparación de versiones de modelo;
- detección de respuestas inválidas y alucinaciones.

# F2.1 — Privacidad, retención y gobierno de datos

Cerrar definitivamente:

- retención de consultas, archivos, transcripciones y resultados;
- conservación de prompts renderizados y respuestas crudas;
- cifrado y control de acceso;
- logs y minimización;
- anonimización y derecho de eliminación;
- borrado físico y adaptación del endpoint ADMIN correspondiente;
- tratamiento de información confidencial;
- prohibición de usar casos reales reservados con proveedores externos sin política aprobada.

# F2.2 — Hardening y operación

- rate limiting;
- límites y validación MIME de archivos;
- timeouts y circuit breakers cuando correspondan;
- validación de la relación entre timeout y lease;
- logging estructurado y correlation ID sin PII ni contenido sensible;
- métricas, health y readiness;
- aprovisionamiento inicial ADMIN;
- backups y recuperación;
- manejo de degradación del proveedor;
- fortalecimiento de concurrencia en transiciones terminales.

# F2.3 — Despliegue y aceptación

- ambientes definitivos;
- Docker y estrategia de despliegue;
- empaquetado frontend/backend;
- secrets productivos;
- CI/CD cuando se acuerde;
- pruebas de aceptación;
- guía de pase y manuales;
- capacitación;
- checklist de producción;
- aceptación con INDECOPI.

---

# 6. Dependencias entre fases

```text
F0 + F1.0 + F1.1 + F1.2 + F1.3
                  ↓
                F1.4
          ┌───────┴───────┐
          ↓               ↓
        F1.5            F1.6
          └───────┬───────┘
                  ↓
                F1.7
                  ↓
                F1.8
                  ↓
                F1.9
                  ↓
                  F2
```

PDF y audio deben reutilizar el flujo central ya implementado. El contrato jurídico-funcional de F1.4 debe estabilizarse antes de cerrar la presentación formal del diagnóstico.

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

La siguiente fase es:

```text
F1.7 — Reporte web + PDF + disclaimer
```

F1.7 debe construir la salida formal desde el mismo `AnalysisResult`, sin duplicar el diagnóstico.
