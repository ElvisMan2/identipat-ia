# Roadmap funcional — IDENTIPAT-IA

**Estado:** Fase F0 completada  
**Versión del roadmap:** 1.0  
**Objetivo:** definir la secuencia de implementación funcional posterior a la estabilización técnica del proyecto.

---

# 1. Estado actual

La fase de estabilización técnica **F0** está completada.

## F0 cerrada

| Fase | Alcance | Estado |
|---|---|---|
| F0.1 | Reorganización del repositorio | ✅ |
| F0.2 | Maven Wrapper y build reproducible | ✅ |
| F0.2A | Alineamiento arquitectónico y funcional | ✅ |
| F0.3 | Seguridad y modelo de acceso | ✅ |
| F0.4 | Configuración por ambientes y secretos | ✅ |
| F0.5 | Flyway + Testcontainers | ✅ |
| F0.6 | OpenAPI + Postman | ✅ |
| F0.7 | Bootstrap `preprocessing-service` | ✅ |
| F0.8 | CI con GitHub Actions | ✅ |

La base técnica actual incluye:

- backend Java / Spring Boot;
- PostgreSQL;
- Flyway;
- Testcontainers;
- OpenAPI / Swagger;
- colección Postman alineada;
- `preprocessing-service` Python / FastAPI;
- CI automático en GitHub;
- separación clara de responsabilidades Java / Python;
- autenticación ADMIN mediante JWT;
- identificación y registro STANDARD sin login.

---

# 2. Decisiones funcionales confirmadas

Estas decisiones deben considerarse **vigentes y obligatorias** para las siguientes fases.

## 2.1 Sesión temporal para usuarios STANDARD

Los usuarios STANDARD:

- no tendrán login;
- no tendrán password;
- no recibirán JWT de autenticación;
- serán reconocidos mediante DNI o Carné de Extranjería;
- tendrán una **sesión temporal** durante el uso de la herramienta;
- no tendrán un panel visible de historial de consultas.

La sesión temporal permitirá asociar de manera controlada las ejecuciones realizadas durante una misma experiencia de uso.

La implementación técnica exacta de la sesión debe definirse en F1.0/F1.1.

---

## 2.2 Persistencia amplia de las consultas

Se persistirá toda la información **funcional y técnicamente relevante** de cada consulta para permitir:

- trazabilidad;
- evaluación de calidad;
- análisis de errores;
- mejora futura del sistema;
- auditoría técnica;
- comparación entre versiones de prompts/modelos;
- métricas;
- reproducibilidad razonable del resultado.

Como mínimo se deberá evaluar persistir:

- usuario STANDARD asociado;
- sesión;
- fecha/hora;
- tipo de entrada;
- texto original ingresado;
- texto extraído o transcrito cuando corresponda;
- estado de la ejecución;
- proveedor/modelo IA utilizado;
- versión del prompt;
- parámetros relevantes del modelo;
- resultado estructurado;
- explicación generada;
- clasificación propuesta;
- errores;
- tiempos de procesamiento;
- metadatos técnicos necesarios para trazabilidad.

La política definitiva de:

- retención;
- minimización;
- anonimización;
- almacenamiento de archivos originales;
- borrado;
- tratamiento de información sensible;

deberá cerrarse antes de producción.

**Persistencia interna no implica historial visible para STANDARD.**

---

## 2.3 IA generativa como motor principal

Por decisión acordada con INDECOPI, el análisis funcional utilizará **IA generativa**.

Por tanto:

> No se implementará un pipeline independiente de PLN/NLP o ML clásico como etapa funcional del análisis.

No se desarrollarán como componentes separados:

- tokenización semántica;
- embeddings para clasificación;
- clasificadores ML tradicionales;
- extracción de features NLP;
- reglas NLP;
- pipelines de similitud semántica como requisito del diagnóstico.

Sí pueden existir transformaciones técnicas necesarias para preparar una entrada, por ejemplo:

- extracción de texto desde PDF;
- transcripción de audio;
- validación de longitud;
- limpieza técnica mínima de encoding;
- normalización de espacios/caracteres.

Estas transformaciones **no constituyen un motor PLN de análisis**.

El razonamiento funcional y la clasificación serán responsabilidad de la IA generativa.

---

# 3. Arquitectura funcional objetivo

```text
Usuario STANDARD
      ↓
Identificación DNI / CE
      ↓
Registro si no existe
      ↓
Consentimiento
      ↓
Sesión temporal
      ↓
Entrada
 ┌────┼────────┐
 │    │        │
Texto PDF    Audio
 │    │        │
 │    └──→ preprocessing-service
 │             ↓
 │        texto extraído /
 │        transcripción
 │             ↓
 └─────────────┴─────→ Java
                       ↓
                 GenerativeAiProvider
                       ↓
                 GeminiProvider
                       ↓
               resultado estructurado
                       ↓
                  persistencia
                       ↓
               respuesta / reporte
```

Principios:

- Angular solo consume Java.
- Java es dueño de los casos de uso.
- Java orquesta la IA generativa.
- Java persiste estado y resultados.
- Python solo realiza preprocesamiento técnico especializado.
- Python no accede a PostgreSQL.
- Python no integra Gemini.
- La integración IA debe ser reemplazable por proveedor.

---

# 4. F1 — Construcción funcional del producto

# F1.0 — Diseño del dominio de análisis

## Objetivo

Cerrar el modelo funcional central antes de implementar el análisis.

## Definir

### Sesión STANDARD

- identificador de sesión;
- duración;
- expiración;
- mecanismo técnico;
- asociación con usuario STANDARD;
- comportamiento al cerrar/reabrir navegador;
- creación y finalización.

### Ejecución de análisis

Definir la entidad conceptual `Analysis` o equivalente.

Debe contemplar, como mínimo:

```text
id
standardUserId
sessionId
inputType
originalInput
processedText
status
provider
model
promptVersion
result
error
startedAt
completedAt
createdAt
```

### Estados

Ejemplo conceptual:

```text
CREATED
PROCESSING
COMPLETED
FAILED
```

### Resultado estructurado

Definir el contrato JSON que será producido por la IA.

Debe permitir representar, como mínimo:

- evaluación de patentabilidad;
- criterios considerados;
- clasificación de modalidad de propiedad intelectual;
- explicación;
- advertencias;
- información que luego alimentará el reporte.

## Entregables

- especificación funcional;
- modelo de dominio;
- estados;
- esquema de persistencia propuesto;
- contrato `AnalysisResult`;
- decisiones pendientes cerradas;
- migraciones planificadas, pero no necesariamente implementadas todavía.

---

# F1.1 — Sesión STANDARD + consentimiento

**Estado:** implementada; pendiente de revisión humana.

## Objetivo

Completar el flujo previo al análisis.

## Alcance

- crear sesión temporal STANDARD;
- asociarla al usuario reconocido/registrado;
- implementar consentimiento explícito de tratamiento de datos;
- registrar evidencia del consentimiento;
- impedir uso del análisis si el consentimiento requerido no fue aceptado;
- persistir timestamps y versión del consentimiento.
- definir e implementar una estrategia CSRF explícita para todas las rutas STANDARD mutables basadas en la cookie temporal.

## Importante

Consentimiento y disclaimer son conceptos diferentes:

```text
Consentimiento
→ tratamiento de datos personales

Disclaimer
→ naturaleza orientativa del resultado de IA
```

No deben mezclarse.

La evidencia de consentimiento se asocia a la sesión temporal y al registro STANDARD, pero DNI/CE no
autentica ni verifica criptográfica o presencialmente la identidad real de quien opera el navegador.
`SameSite` y CORS no sustituyen CSRF: F1.1 debe seleccionar un mecanismo compatible con
Angular/Spring Security. ADMIN mantiene JWT Bearer separado.

Implementación seleccionada: sesión server-side con cookie HttpOnly y HMAC, TTL de inactividad/absoluto configurables, eventos de consentimiento inmutables y `CookieCsrfTokenRepository` con patrón SPA Spring Security 6.2. F1.3 extenderá CSRF a las mutaciones de análisis.

---

# F1.2 — Capa de IA generativa en Java

**Estado:** implementada con OpenAI como adapter inicial; pendiente de revisión humana y smoke real con credenciales.

## Objetivo

Crear la abstracción de proveedor de IA generativa.

## Arquitectura

```text
GenerativeAiProvider
        ↑
OpenAiGenerativeAiProvider
```

El resto de la aplicación depende de la interfaz, no directamente de OpenAI. Gemini se incorporará
como adapter futuro cuando existan credenciales, sin modificar el contrato común.

## Alcance

- interfaz del proveedor;
- implementación OpenAI inicial mediante Responses API y SDK oficial directo;
- prompts y JSON Schemas versionados;
- structured output estricto con validación local;
- configuración por ambiente;
- secreto/API key externalizado;
- timeout;
- errores controlados;
- request/response interno;
- structured output;
- tests con provider simulado;
- observabilidad técnica básica;
- registro de proveedor/modelo usado.

## No incluir

- lógica PDF;
- lógica audio;
- prompts finales sin versionado;
- dependencia de Gemini en controllers;
- llamadas directas desde Angular.

---

# F1.3 — Análisis end-to-end desde texto

**Estado:** implementado en backend; pendiente revisión del reporte y validación HTTP manual.

## Objetivo

Construir la primera vertical funcional completa del producto.

## Flujo

```text
STANDARD
   ↓
sesión válida
   ↓
texto
   ↓
validación técnica
   ↓
crear Analysis
   ↓
persistir input
   ↓
IA generativa
   ↓
validar structured output
   ↓
persistir resultado
   ↓
devolver resultado
```

## Alcance

- endpoint de análisis por texto;
- longitud mínima;
- longitud máxima razonable;
- creación de ejecución;
- estados;
- llamada al provider;
- persistencia completa;
- manejo de errores;
- idempotencia si resulta necesaria;
- respuesta estructurada.

## No habrá

- pipeline NLP;
- embeddings;
- modelo ML intermedio;
- clasificación fuera del LLM.

---

# F1.4 — Diagnóstico de propiedad intelectual

## Objetivo

Estabilizar el contenido funcional que debe producir la IA.

## Debe contemplar

### Patentabilidad

Evaluación orientativa respecto de materia patentable y exclusiones aplicables según el marco definido para el proyecto.

### Clasificación probable

Como mínimo:

- patente de invención;
- modelo de utilidad;
- diseño industrial;
- signos distintivos;
- derecho de autor;
- otras alternativas cuando corresponda.

## Resultado

La IA debe responder mediante **structured output**, no mediante texto libre sin contrato.

Java debe:

- validar el schema;
- rechazar respuestas inválidas;
- persistir el JSON estructurado;
- conservar explicación legible;
- registrar versión de prompt/modelo.

## Prompts

Los prompts deben ser:

- versionados;
- testeables;
- independientes del controller;
- trazables por ejecución.

---

# F1.5 — Entrada mediante PDF

## Objetivo

Agregar PDF reutilizando exactamente el mismo pipeline de análisis existente.

## Flujo

```text
PDF
 ↓
Java
 ↓
preprocessing-service
 ↓
extracción técnica de texto
 ↓
Java
 ↓
Analysis existente
 ↓
IA generativa
```

## Definir

- formatos aceptados;
- tamaño máximo;
- páginas máximas si aplica;
- PDF corrupto;
- PDF protegido;
- PDF sin texto;
- PDF escaneado;
- política inicial sobre OCR;
- almacenamiento temporal o persistente del archivo.

---

# F1.6 — Entrada mediante audio

## Objetivo

Agregar entrada de voz reutilizando el pipeline central.

## Flujo

```text
Audio
 ↓
Java
 ↓
preprocessing-service
 ↓
transcripción
 ↓
Java
 ↓
Analysis existente
 ↓
IA generativa
```

## Definir

- tecnología speech-to-text;
- formatos;
- tamaño;
- duración máxima;
- idioma;
- audio inválido;
- errores de transcripción;
- almacenamiento temporal;
- persistencia de transcripción.

La transcripción debe persistirse como parte relevante de la consulta.

---

# F1.7 — Reporte web + PDF + disclaimer

## Objetivo

Construir la salida formal del diagnóstico.

## Resultado web

Mostrar de forma comprensible:

- resumen;
- evaluación;
- modalidad/es sugeridas;
- explicación;
- recomendaciones;
- advertencias.

## PDF

Generar un reporte descargable basado en el mismo `AnalysisResult`.

No crear una segunda lógica de diagnóstico para PDF.

## Disclaimer

Debe indicar claramente que:

- el análisis es orientativo;
- la IA es una herramienta complementaria;
- no reemplaza evaluación especializada;
- no constituye una decisión oficial.

El disclaimer debe ser versionable.

Es contenido institucional controlado por la aplicación, independiente de Gemini y de
`AnalysisResult.warnings[]`; no se genera ni se persiste como advertencia diagnóstica por defecto.

---

# F1.8 — Orientación y recursos asociados

## Objetivo

Complementar el diagnóstico con información útil para el siguiente paso del usuario.

## Contenido

Según modalidad recomendada:

- requisitos;
- pasos;
- formularios;
- tutoriales;
- enlaces oficiales;
- servicios relacionados.

## Recomendación arquitectónica

No confiar exclusivamente en generación libre del LLM para esta información.

Preferir un catálogo controlado/versionado que Java pueda asociar a la clasificación obtenida.

---

# F1.9 — Integración frontend y E2E

## Objetivo

Integrar el flujo completo desde Angular.

## Flujo esperado

```text
DNI / CE
   ↓
reconocimiento
   ↓
registro si aplica
   ↓
consentimiento
   ↓
sesión temporal
   ↓
texto / PDF / audio
   ↓
procesamiento
   ↓
resultado
   ↓
reporte
```

## Alcance

- integración con contratos backend;
- UX de estados de procesamiento;
- errores;
- carga de PDF/audio;
- presentación del diagnóstico;
- descarga de reporte;
- pruebas E2E;
- revisión de accesibilidad;
- incorporación del frontend al CI.

---

# 5. F2 — Preparación para piloto y producción

# F2.0 — Evaluación de calidad

- dataset de casos de prueba;
- evaluación experta;
- métricas;
- consistencia;
- regresión de prompts;
- comparación entre versiones de modelo;
- detección de respuestas inválidas/alucinaciones;
- criterios mínimos de aceptación.

# F2.1 — Privacidad, retención y gobierno de datos

Cerrar definitivamente:

- retención de consultas;
- archivos PDF/audio originales;
- transcripciones;
- inputs;
- resultados;
- logs;
- anonimización;
- derecho de eliminación;
- condiciones de borrado físico y adaptación futura del endpoint ADMIN de eliminación;
- acceso administrativo;
- minimización;
- tratamiento de información confidencial.

# F2.2 — Hardening y operación

- rate limiting;
- límites de archivos;
- validación MIME;
- timeouts;
- circuit breakers si resultan necesarios;
- observabilidad;
- métricas;
- trazabilidad;
- health/readiness;
- aprovisionamiento inicial ADMIN;
- backups;
- recuperación;
- manejo de degradación del proveedor IA.

# F2.3 — Despliegue y aceptación

- ambientes definitivos;
- Docker/deployment;
- secrets productivos;
- CI/CD cuando se decida;
- pruebas de aceptación;
- guía de pase;
- manuales;
- capacitación;
- checklist de producción;
- aceptación con INDECOPI.

---

# 6. Dependencias entre fases

```text
F1.0
 ↓
F1.1
 ↓
F1.2
 ↓
F1.3
 ↓
F1.4
 ├─────────────┐
 ↓             ↓
F1.5          F1.6
 └──────┬──────┘
        ↓
      F1.7
        ↓
      F1.8
        ↓
      F1.9
        ↓
       F2
```

PDF y audio no deben implementarse antes de que el análisis por texto esté estable.

---

# 7. Principios para las siguientes fases

## Vertical antes que amplitud

Primero:

```text
texto → IA → resultado
```

Después:

```text
PDF → texto → mismo análisis
audio → texto → mismo análisis
```

## Una sola lógica de análisis

Todos los tipos de entrada convergen a:

```text
texto utilizable
      ↓
Analysis
      ↓
GenerativeAiProvider
```

## IA sustituible

Siempre:

```text
Use Case
   ↓
GenerativeAiProvider
   ↓
GeminiProvider
```

## Structured output

Evitar:

```text
String resultadoLibre
```

como modelo principal.

Preferir objetos estructurados y validados.

## Persistencia trazable

Cada ejecución debe permitir responder:

```text
¿quién hizo la consulta?
¿en qué sesión?
¿qué entrada se procesó?
¿qué texto llegó al modelo?
¿qué prompt se usó?
¿qué proveedor/modelo?
¿qué resultado devolvió?
¿cuánto tardó?
¿falló?
¿por qué?
```

## No NLP separado

No introducir por iniciativa técnica:

- embeddings;
- clasificadores;
- pipelines NLP;
- modelos ML auxiliares;

salvo una decisión funcional posterior explícita.

---

# 8. Próximo paso inmediato

La siguiente fase recomendada es:

```text
F1.0 — Diseño del dominio de análisis
```

Antes de pedir a Codex que implemente funcionalidades, F1.0 debe cerrar especialmente:

1. mecanismo de sesión temporal STANDARD;
2. modelo de persistencia de consultas;
3. entidades/tablas iniciales;
4. lifecycle/status del análisis;
5. contrato estructurado del resultado;
6. metadata de trazabilidad IA;
7. política inicial para almacenar input/output;
8. separación entre consentimiento y disclaimer.

Una vez cerrado F1.0, se podrá elaborar una especificación técnica precisa para F1.1 y las fases siguientes.
