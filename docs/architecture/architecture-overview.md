# Arquitectura de referencia de IDENTIPAT-IA

## Contexto

Este documento registra la arquitectura lógica y el modelo de acceso vigentes antes de F0.3. Complementa los diagnósticos históricos: cuando exista una discrepancia, las decisiones de este documento prevalecen para el desarrollo futuro.

## Componentes y comunicación

```text
Angular (frontend)
        │ REST/HTTPS
        ▼
Java / Spring Boot (backend principal)
  ├── JDBC ─────────────► PostgreSQL
  ├── REST interno ─────► Python preprocessing-service
  └── GenerativeAiProvider ─► GeminiProvider ─► Gemini
```

Angular se comunica exclusivamente con Java. Java es quien se comunica con PostgreSQL, con el futuro servicio Python y con el proveedor de IA generativa.

## Responsabilidades

### Angular

Angular es responsable de la presentación, interacción, navegación, captura de texto/PDF/audio, visualización de resultados y sesión visual actual. No llama directamente a PostgreSQL, Python, Gemini ni a otros proveedores LLM.

### Java / Spring Boot

Java es el backend principal y dueño de los casos de uso. Es responsable de la API de negocio, usuarios, seguridad y autenticación ADMIN, consentimiento y disclaimer, análisis, reglas de negocio, orquestación, persistencia, sesiones/ejecuciones, reportes, catálogo y orientación de propiedad intelectual, y comunicación con Python.

Java también integra y ejecuta la inferencia con IA generativa. La arquitectura prevista debe abstraer al proveedor, conceptualmente mediante `GenerativeAiProvider` y una implementación inicial `GeminiProvider`; Gemini es el proveedor inicial. Estas clases e integración no se implementan todavía.

### Python: `preprocessing-service`

Python es un servicio FastAPI especializado de preprocesamiento técnico, no el backend principal. Por ahora expone únicamente `GET /health`; sus responsabilidades futuras incluyen extracción y normalización de texto desde PDF, procesamiento de audio, transcripción o transformación de audio, y otros preprocesamientos de entradas que se definan.

Python no realiza inferencia principal del LLM, prompts de diagnóstico, clasificación de propiedad intelectual mediante LLM, integración con Gemini, reglas de negocio, persistencia principal, usuarios ni autenticación. Angular no lo consume directamente; Java encapsula la comunicación mediante un cliente interno.

### PostgreSQL

PostgreSQL es el sistema de persistencia principal, accedido desde Java. Durante el desarrollo se almacenarán internamente consultas/análisis y resultados para pruebas, trazabilidad, evaluación y evolución del sistema. También albergará los datos que correspondan a usuarios, catálogos y auditoría según las decisiones posteriores.

## Modelo de acceso

### ADMIN

Solo ADMIN se autentica. El flujo administrativo es: credenciales → Spring Security → JWT → funciones administrativas. La autenticación tradicional con contraseña y JWT se mantiene para este acceso.

### STANDARD

STANDARD no hace login convencional ni recibe JWT. No usa contraseña para acceder a la herramienta, ni tiene perfil visible, edición de perfil en este flujo, historial visible de ejecuciones ni recuperación de sesiones anteriores desde la interfaz.

El DNI/CE sirve únicamente para identificar o reconocer si existe un registro en PostgreSQL; no constituye autenticación, contraseña, credencial ni factor de autenticación. Si no existe registro, el flujo contempla registro y aceptación previa; si existe, continúa al uso de la herramienta conforme a la validación del flujo. Un usuario registrado no ve sus datos personales previamente registrados y pasa al uso de la herramienta.

## Consentimiento y disclaimer

El consentimiento para el tratamiento de datos personales debe contemplarse en el registro y es distinto del disclaimer del diagnóstico. El disclaimer debe indicar que los resultados de IA son orientativos y no sustituyen evaluación técnica, experta ni decisiones oficiales de Indecopi. Ambos mecanismos se preparan conceptualmente aquí; no se implementan en esta fase.

## Persistencia interna e historial visible

La inexistencia de un historial visible para STANDARD no implica ausencia de persistencia técnica. Durante una sesión, STANDARD solo podrá visualizar los resultados generados en esa sesión. En desarrollo, Java almacenará internamente consultas/análisis y resultados; esa persistencia no crea un panel de historial ni recuperación de sesiones para STANDARD.

La política definitiva permanece pendiente: minimización de datos, contenido de entrada que se conservará, periodos de retención, metadatos necesarios y reducción o eliminación de información sensible. No debe inferirse una política final a partir de la persistencia provisional de desarrollo.

## Principios arquitectónicos

1. Java es dueño del caso de negocio; Python no es un segundo backend funcional.
2. Java es dueño de la inferencia generativa; el proveedor inicial es Gemini y debe permanecer abstraído.
3. Python es un servicio especializado de preprocesamiento consumido por Java cuando la entrada lo requiera.
4. STANDARD no se autentica; DNI/CE solo reconoce el registro.
5. ADMIN se autentica con Spring Security y JWT para el acceso administrativo.
6. La persistencia interna no implica un historial visible para STANDARD.
7. La retención de consultas es provisional durante desarrollo y deberá minimizarse mediante una política futura.
8. El frontend consume únicamente Java; no existen integraciones directas Angular → Python o Angular → LLM.

## Decisiones pendientes

Quedan deliberadamente abiertas la política definitiva de retención y minimización, duración y mecanismo técnico de sesión temporal STANDARD, vínculo permanente o por metadatos de consultas, almacenamiento de archivos PDF/audio, tecnología concreta de extracción PDF y transcripción/procesamiento de audio, SDK o cliente Gemini, estructura final del resultado de IA, observabilidad y auditoría, y una eventual autenticación futura de STANDARD.

## Roadmap de Fase 0

| Fase | Alcance | Estado |
| --- | --- | --- |
| F0.1 | Reorganización del repositorio | Completada |
| F0.2 | Maven Wrapper / build reproducible | Completada |
| F0.2A | Alineamiento arquitectónico | En curso |
| F0.3 | Seguridad + modelo de acceso usuarios | Pendiente |
| F0.4 | Configuración dev/test/prod | Pendiente |
| F0.5 | Flyway + Testcontainers | Pendiente |
| F0.6 | OpenAPI + Postman | Pendiente |
| F0.7 | Bootstrap Python `preprocessing-service` | Completada |
| F0.8 | CI GitHub | Pendiente |
