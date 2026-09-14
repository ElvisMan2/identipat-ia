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

Java también integra y ejecuta la inferencia con IA generativa. F1.2 implementa la abstracción
`GenerativeAiProvider` y `OpenAiGenerativeAiProvider` como primer adapter técnico mediante el SDK
oficial y Responses API. Ningún controller ni caso de uso conoce tipos `com.openai.*`; prompts y
schemas siguen siendo contratos versionados de IDENTIPAT. Gemini queda como adapter futuro y podrá
incorporarse sin cambiar el contrato común.

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

## Sesión STANDARD, consentimiento y disclaimer

F1.1 implementa en Java una capacidad temporal server-side asociada al registro STANDARD: token opaco de 256 bits en cookie HttpOnly y solo su HMAC-SHA-256 en PostgreSQL. La sesión es revocable, expira por inactividad y por límite absoluto, y nunca se convierte en JWT o autenticación ADMIN. Las mutaciones usan CSRF de Spring Security mediante cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`; CORS permite credenciales solo desde orígenes explícitos.

El consentimiento de tratamiento de datos se persiste como evento inmutable versionado, distinto del disclaimer orientativo del diagnóstico. La evidencia vincula sesión y registro, pero no prueba la identidad real de quien opera el navegador. F1.1 no inventa texto legal: configura la versión y SHA-256 del documento aprobado externamente.

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

## Diseño F1.0 y decisiones aún pendientes

F1.0 cerró el diseño conceptual de sesión temporal STANDARD, consentimiento, `Analysis`, entradas,
invocaciones de IA, resultado estructurado y contrato asíncrono. Sus decisiones detalladas y no
implementadas se encuentran en [el dominio de análisis](../design/analysis-domain.md) y [su contrato
conceptual](../design/analysis-contract.md). En particular, la sesión se diseñó como una capacidad
server-side temporal mediante cookie HttpOnly con token opaco, independiente del JWT ADMIN; no es una
autenticación equivalente.

La asociación de una sesión o de un evento de consentimiento con `user_id` y `session_id` deja
trazabilidad de la decisión tomada desde una experiencia vinculada al registro STANDARD. No demuestra
la identidad real de quien utiliza el navegador: DNI/CE no es autenticación ni verificación
criptográfica o presencial. F1.1 implementa `CookieCsrfTokenRepository` y un handler SPA Spring Security 6.2 para las
rutas STANDARD mutables que usan la cookie temporal; `SameSite` y CORS son complementarios. ADMIN
mantiene su modelo JWT Bearer separado.

Cuando un usuario tenga sesiones, consentimientos, análisis u otra evidencia histórica, la dirección
funcional es su desactivación lógica y la conservación de trazabilidad, no borrado físico en cascada.
La anonimización de PII, derecho de eliminación, retención, condiciones de borrado físico y adaptación
del endpoint ADMIN actual son decisiones de gobierno de datos/producción.

Permanecen deliberadamente abiertas la política definitiva de retención, minimización, anonimización,
cifrado y condiciones de borrado físico; almacenamiento de archivos PDF/audio, tecnología concreta de extracción y
transcripción; SDK concreto de Gemini; criterios jurídicos detallados del diagnóstico; observabilidad,
acceso administrativo y una eventual autenticación futura de STANDARD. Esas decisiones se cierran en
las fases funcionales y de gobierno indicadas en el roadmap, sin alterar los límites de responsabilidad
de esta arquitectura.

## Roadmap de Fase 0

| Fase | Alcance | Estado |
| --- | --- | --- |
| F0.1 | Reorganización del repositorio | Completada |
| F0.2 | Maven Wrapper / build reproducible | Completada |
| F0.2A | Alineamiento arquitectónico | Completada |
| F0.3 | Seguridad + modelo de acceso usuarios | Completada |
| F0.4 | Configuración dev/test/prod | Completada |
| F0.5 | Flyway + Testcontainers | Completada |
| F0.6 | OpenAPI + Postman | Completada |
| F0.7 | Bootstrap Python `preprocessing-service` | Completada |
| F0.8 | CI GitHub | Completada |
