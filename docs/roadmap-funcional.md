# Roadmap funcional — IDENTIPAT-IA

**Estado:** F0 y F1.0 completadas; F1.1–F1.3 implementadas; F1.4 es la siguiente fase  
**Versión del roadmap:** 1.1  
**Última actualización:** 16 de septiembre de 2026  
**Objetivo:** mantener una visión vigente de la secuencia funcional del producto, diferenciando lo ya materializado de las fases pendientes.

---

# 1. Estado actual

La estabilización técnica F0 está completada. El backend también materializa el diseño de dominio F1.0 y las verticales F1.1–F1.3.

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
| F1.4 | Diagnóstico de propiedad intelectual | ⏭️ Siguiente fase |
| F1.5–F1.9 | PDF, audio, reporte, recursos y E2E | ⏳ Pendientes |
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

- creación y cierre de sesiones temporales STANDARD;
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

**Estado:** siguiente fase.

## Objetivo

Estabilizar el contrato jurídico-funcional que debe producir la IA y alinearlo con el diagnóstico requerido por el proyecto. Esta fase no debe limitarse a ampliar el prompt.

## Trabajo requerido

1. Definir los criterios jurídicos que el resultado debe representar explícitamente.
2. Alinear la evaluación de materia patentable y exclusiones con el marco aprobado para el proyecto, incluidos los artículos 15 y 20 de la Decisión 486 cuando corresponda.
3. Definir las modalidades de protección y su alcance:
   - patente de invención;
   - modelo de utilidad;
   - diseño industrial;
   - signos distintivos;
   - derecho de autor;
   - otras alternativas controladas.
4. Evolucionar el contrato `AnalysisResult` y su JSON Schema con versionado compatible.
5. Diseñar y versionar el prompt jurídico-funcional.
6. Preparar casos de prueba revisables por especialistas.
7. Definir qué referencias legales y explicaciones son controladas por la aplicación y cuáles puede generar el modelo.

## Resultado esperado

La IA debe responder mediante structured output, no como texto libre sin contrato.

Java debe:

- validar el schema;
- rechazar respuestas inválidas;
- persistir el JSON estructurado y la explicación legible;
- registrar prompt, schema, proveedor y modelo utilizados;
- mantener trazabilidad por ejecución.

---

# F1.5 — Entrada mediante PDF

**Estado:** pendiente.

## Objetivo

Agregar PDF reutilizando el pipeline de análisis existente.

```text
PDF → Java → preprocessing-service → texto extraído → Analysis existente
```

## Definir e implementar

- formatos y MIME aceptados;
- tamaño y páginas máximas;
- manejo de PDF corrupto o protegido;
- PDF sin texto o escaneado;
- política inicial de OCR;
- almacenamiento temporal o persistente del archivo;
- extensión de CSRF y autorización al nuevo endpoint;
- pruebas de aislamiento y errores.

---

# F1.6 — Entrada mediante audio

**Estado:** pendiente.

## Objetivo

Agregar voz reutilizando el pipeline central.

```text
Audio → Java → preprocessing-service → transcripción → Analysis existente
```

## Definir e implementar

- tecnología speech-to-text;
- formatos y MIME;
- tamaño y duración máxima;
- idioma;
- manejo de audio inválido y errores de transcripción;
- almacenamiento temporal;
- persistencia de la transcripción;
- extensión de CSRF y autorización al nuevo endpoint.

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
F1.4 — Diagnóstico de propiedad intelectual
```

Antes de modificar el prompt productivo, F1.4 debe cerrar:

1. criterios jurídicos explícitos del diagnóstico;
2. alcance y representación de los artículos 15 y 20 de la Decisión 486;
3. modalidades de protección y taxonomía controlada;
4. evolución versionada de `AnalysisResult` y su JSON Schema;
5. prompt jurídico-funcional versionado;
6. casos de prueba y criterios de aceptación experta;
7. compatibilidad con los consumidores actuales del contrato;
8. separación entre contenido generado, referencias controladas y disclaimer institucional.

En paralelo, sin bloquear el diseño funcional, conviene planificar las mejoras técnicas identificadas: invariantes de roles ADMIN/STANDARD, validación timeout/lease, logging estructurado y conservación de metadata de respuestas `incomplete`.
