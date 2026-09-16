# Arquitectura de referencia de IDENTIPAT-IA

**Estado:** arquitectura vigente y materializada hasta F1.3  
**Versión:** 1.1  
**Última actualización:** 16 de septiembre de 2026

## Contexto

Este documento describe la arquitectura lógica, los límites de responsabilidad y el modelo de acceso vigentes de IDENTIPAT-IA.

La arquitectura ya no representa una propuesta previa a F0.3: la estabilización F0, el diseño F1.0 y las capacidades F1.1–F1.3 se encuentran materializadas en el backend. Cuando exista una discrepancia con diagnósticos históricos, prevalecen este documento, el código vigente y las especificaciones especializadas enlazadas desde aquí.

El diagnóstico funcional definitivo de propiedad intelectual, las entradas PDF/audio y el reporte formal continúan pendientes en F1.4–F1.7.

## Componentes y comunicación

```text
Angular (frontend)
        │ REST/HTTPS
        ▼
Java / Spring Boot (backend principal)
  ├── JPA/JDBC ───────────────► PostgreSQL
  ├── REST interno ───────────► Python preprocessing-service
  └── GenerativeAiProvider ───► OpenAiGenerativeAiProvider
                                      │
                                      └──► OpenAI Responses API
```

Angular se comunica exclusivamente con Java. Java es el dueño de los casos de uso, accede a PostgreSQL, encapsula la comunicación futura con Python y ejecuta la inferencia generativa mediante una interfaz independiente del proveedor.

OpenAI es el proveedor implementado actualmente. Gemini u otros proveedores podrán incorporarse como adapters futuros sin alterar el contrato central ni los casos de uso.

## Responsabilidades

### Angular

Angular es responsable de:

- presentación, navegación e interacción;
- identificación y registro del usuario STANDARD;
- obtención y envío del token CSRF según el patrón SPA;
- captura de texto y, en fases posteriores, carga de PDF/audio;
- visualización de estados y resultados de la sesión actual;
- polling del análisis asíncrono;
- presentación y descarga de reportes cuando F1.7 se implemente.

Angular no accede directamente a PostgreSQL, Python ni proveedores LLM. Tampoco implementa reglas de negocio o decisiones de autorización que deban garantizarse en servidor.

### Java / Spring Boot

Java es el backend principal y dueño de los casos de uso. Es responsable de:

- API de negocio;
- usuarios y modelo ADMIN/STANDARD;
- autenticación y autorización ADMIN;
- sesiones temporales STANDARD;
- CSRF y controles de seguridad aplicables;
- consentimiento y validación de su vigencia;
- lifecycle de los análisis;
- orquestación asíncrona, lease, retry y recuperación;
- inferencia con IA generativa;
- validación del structured output;
- persistencia y trazabilidad;
- integración interna con Python;
- reportes, catálogo y orientación de propiedad intelectual en fases posteriores.

La integración generativa se realiza mediante `GenerativeAiProvider`. `OpenAiGenerativeAiProvider` es el primer adapter técnico y utiliza el SDK oficial de OpenAI y Responses API.

Ningún controller ni caso de uso conoce tipos `com.openai.*`. Los prompts y JSON Schemas son contratos versionados de IDENTIPAT-IA. El adapter traduce las respuestas y excepciones del proveedor a modelos y errores internos.

### Python: `preprocessing-service`

Python es un servicio FastAPI especializado en preprocesamiento técnico; no es un segundo backend funcional. Actualmente dispone del bootstrap y `GET /health`.

Sus responsabilidades previstas son:

- extracción y normalización técnica de texto desde PDF;
- detección y manejo de documentos sin texto;
- procesamiento técnico de audio;
- transcripción;
- otras transformaciones de entrada aprobadas.

Python no es responsable de:

- inferencia principal del LLM;
- prompts de diagnóstico;
- clasificación de propiedad intelectual;
- integración directa con OpenAI, Gemini u otro proveedor generativo;
- reglas de negocio;
- usuarios o autenticación;
- persistencia principal en PostgreSQL.

Angular no consume Python directamente. Java encapsula su comunicación mediante un cliente interno.

### PostgreSQL

PostgreSQL es el sistema de persistencia principal y es accedido únicamente por Java.

Almacena, entre otros elementos:

- usuarios ADMIN y STANDARD;
- sesiones temporales STANDARD;
- eventos inmutables de consentimiento;
- `Analysis` y `AnalysisInput`;
- `AiInvocation` por cada intento técnico;
- `AnalysisResult` aceptado;
- estados, errores y metadata de trazabilidad.

En F1.3 PostgreSQL también es la fuente de verdad del trabajo asíncrono. El worker reclama análisis mediante `FOR UPDATE SKIP LOCKED`, registra un lease y libera la transacción antes de esperar la respuesta del LLM. Cada intento se registra de forma durable antes de invocar al proveedor.

La recuperación tiene semántica *at-least-once*: un lease vencido puede permitir que otro worker retome el análisis. Las escrituras terminales y las restricciones de base de datos protegen la consistencia funcional, aunque deben seguir fortaleciéndose los casos límite cercanos al vencimiento del lease antes de producción.

## Flujos principales

### Flujo ADMIN

```text
Credenciales
     ↓
Spring Security
     ↓
JWT Bearer
     ↓
funciones administrativas autorizadas
```

Solo los usuarios ADMIN se autentican. El backend reconstruye el usuario desde PostgreSQL, verifica que sea ADMIN y que permanezca activo antes de conceder acceso.

La autenticación ADMIN y la sesión temporal STANDARD son mecanismos separados. Una sesión STANDARD nunca se convierte en JWT ni concede privilegios administrativos.

### Flujo STANDARD previo al análisis

```text
DNI / CE
   ↓
reconocimiento o registro
   ↓
sesión temporal server-side
   ↓
consentimiento vigente
   ↓
uso de la herramienta
```

STANDARD no realiza login convencional, no utiliza contraseña y no recibe JWT. Tampoco dispone de perfil editable, historial visible persistente ni recuperación de sesiones anteriores desde la interfaz.

El DNI/CE sirve únicamente para identificar o deduplicar un registro en PostgreSQL. No constituye autenticación, contraseña, credencial ni verificación criptográfica o presencial de la identidad real de quien opera el navegador.

Si el registro no existe, el flujo contempla su creación. Si existe y está activo, el usuario continúa conforme a las validaciones de sesión y consentimiento. Los datos personales previamente registrados no se exponen como perfil dentro de este flujo.

### Flujo de análisis de texto

```text
POST /analyses/text
      ↓
validar sesión + CSRF + consentimiento + longitud
      ↓
persistir Analysis(RECEIVED) + AnalysisInput(TEXT)
      ↓
202 Accepted
      ↓
worker reclama trabajo con lease
      ↓
crear AiInvocation(STARTED)
      ↓
invocar GenerativeAiProvider fuera de transacción DB
      ↓
validar structured output
      ↓
persistir AnalysisResult + transición terminal
      ↓
GET /analyses/{analysisId} desde la misma sesión
```

El request HTTP no permanece abierto durante la inferencia. La consulta y el resultado pertenecen a la sesión STANDARD que creó el análisis; otra sesión no puede recuperarlos.

## Sesión STANDARD, consentimiento y disclaimer

F1.1 implementa una capacidad temporal server-side asociada al registro STANDARD:

- token opaco de 256 bits entregado en cookie `HttpOnly`;
- almacenamiento exclusivo de su HMAC-SHA-256 en PostgreSQL;
- expiración por inactividad y por límite absoluto;
- revocación y cierre por usuario inactivo;
- `SameSite=Lax` y `Secure` en producción;
- scope de cookie limitado a la aplicación.

Las mutaciones STANDARD implementadas usan CSRF de Spring Security mediante cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`, con el patrón SPA de Spring Security 6.2. CORS admite credenciales solo desde orígenes explícitos. `SameSite` y CORS son controles complementarios, no sustitutos de CSRF.

El consentimiento de tratamiento de datos se persiste como evento inmutable y versionado. La evidencia vincula sesión, usuario, decisión, versión y hash SHA-256 del documento aprobado externamente. Antes de crear un análisis, el backend verifica que exista una aceptación correspondiente a la versión y al hash vigentes.

El disclaimer es un concepto distinto: informa sobre la naturaleza orientativa del diagnóstico y se implementará como contenido institucional controlado en F1.7. No se genera libremente por el LLM ni equivale a `AnalysisResult.warnings[]`.

La evidencia de sesión y consentimiento no prueba la identidad real de quien utiliza el navegador. También permanece pendiente la confirmación funcional/legal sobre el momento exacto en que debe obtenerse el consentimiento respecto al registro inicial de datos personales.

## Seguridad y límites de confianza

La arquitectura separa dos contextos:

| Contexto | Mecanismo | Uso |
|---|---|---|
| ADMIN | Spring Security + JWT Bearer | Funciones administrativas |
| STANDARD | Cookie temporal + sesión server-side + CSRF | Uso de la herramienta durante la sesión actual |

Los endpoints STANDARD no deben depender solo de reglas `permitAll()` en la configuración HTTP. Cada caso de uso debe resolver la sesión, comprobar su vigencia y verificar que el recurso solicitado pertenece a esa sesión.

Actualmente `POST /analyses/text` está incluido explícitamente en el matcher CSRF. Al incorporar PDF y audio, sus mutaciones deberán quedar protegidas. Se recomienda evolucionar hacia una regla general para las mutaciones STANDARD bajo `/analyses/**`, evitando depender de una enumeración manual por endpoint.

No deben registrarse en logs operativos:

- DNI/CE;
- cookies o tokens;
- texto confidencial de la consulta;
- prompts renderizados;
- respuestas completas del proveedor;
- secretos o credenciales.

Los errores inesperados deben registrarse internamente con un identificador de correlación y detalles técnicos seguros, sin exponer excepciones ni datos sensibles al cliente.

## Persistencia interna e historial visible

La inexistencia de un historial visible para STANDARD no implica ausencia de persistencia técnica.

Durante una sesión, STANDARD solo puede visualizar los análisis generados en esa sesión. Internamente, durante el desarrollo, Java conserva entradas, prompts renderizados, respuestas técnicas, resultados y metadata para trazabilidad, evaluación y evolución del sistema.

Esta persistencia provisional no crea un panel de historial ni habilita la recuperación de sesiones anteriores para STANDARD.

La política productiva permanece pendiente en F2.1:

- minimización;
- periodos de retención;
- cifrado;
- acceso administrativo;
- anonimización;
- derecho de eliminación;
- borrado físico;
- conservación de archivos PDF/audio;
- conservación de prompts renderizados y respuestas crudas;
- tratamiento de información confidencial.

No debe inferirse una política final a partir de la persistencia amplia utilizada durante el desarrollo.

## Inferencia generativa y contratos

El backend diferencia tres conceptos:

```text
Analysis
→ consulta funcional y su lifecycle

AiInvocation
→ intento técnico contra un proveedor

AnalysisResult
→ resultado funcional estructurado aceptado
```

Los prompts y schemas se almacenan como recursos versionados. El adapter solicita structured output y Java valida localmente la respuesta antes de aceptar un `AnalysisResult`.

El contrato actual permite la vertical técnica de F1.3, pero todavía no representa el diagnóstico jurídico definitivo exigido por F1.4. La siguiente evolución debe comenzar por el contrato de salida y los criterios jurídicos explícitos, no únicamente por ampliar el prompt.

La aplicación no interpreta la salida del LLM como una probabilidad calibrada. Cuando el contrato utilice niveles o categorías de evaluación, estos deben considerarse clasificaciones controladas, no porcentajes de confianza.

## Procesamiento asíncrono, retry y recuperación

El procesamiento separa transacciones cortas de base de datos de la llamada remota al proveedor:

```text
TX 1: claim + lease
COMMIT

TX 2: AiInvocation STARTED
COMMIT

sin transacción DB: llamada al proveedor

TX 3: AiInvocation + AnalysisResult + estado terminal
COMMIT
```

Los fallos retryable no bloquean threads con `sleep`. El backend persiste `next_attempt_at`, libera el lease y permite que el poller retome el trabajo posteriormente.

Si un worker desaparece, el lease vence y una nueva ejecución puede marcar el intento previo como `ABANDONED` antes de crear otro. Este diseño evita mantener conexiones de base de datos abiertas durante la inferencia y permite recuperación después de reinicios o fallos.

Antes de producción se debe garantizar por configuración que:

```text
lease duration >= provider timeout + margen operativo
```

También conviene reforzar la transición terminal mediante una actualización condicionada por propietario y vigencia del lease, u otro mecanismo equivalente de control optimista.

## Principios arquitectónicos

1. Java es dueño de los casos de negocio; Python no es un segundo backend funcional.
2. Java es dueño de la inferencia generativa mediante `GenerativeAiProvider`.
3. OpenAI es el adapter implementado actualmente; Gemini u otros proveedores son extensiones futuras.
4. Python realiza preprocesamiento técnico y es consumido únicamente por Java.
5. STANDARD no se autentica; DNI/CE solo identifica o reconoce un registro.
6. ADMIN se autentica mediante Spring Security y JWT Bearer.
7. Sesión STANDARD y autenticación ADMIN permanecen estrictamente separadas.
8. El frontend consume únicamente Java.
9. La persistencia interna no implica historial visible para STANDARD.
10. El análisis se procesa de forma asíncrona y PostgreSQL es su fuente de verdad.
11. Los prompts, schemas y resultados estructurados son contratos versionados.
12. No existe un pipeline funcional separado de PLN/NLP o ML clásico.
13. La retención amplia es provisional durante el desarrollo y debe minimizarse antes de producción.
14. Los tipos de entrada futuros convergen al mismo núcleo `Analysis`.

## Estado materializado hasta F1.3

F1.0 cerró el diseño conceptual de:

- sesión temporal STANDARD;
- consentimiento;
- `Analysis` y sus entradas;
- invocaciones de IA;
- resultado estructurado;
- contrato asíncrono.

F1.1 materializó la sesión server-side, el consentimiento versionado y CSRF para las mutaciones STANDARD implementadas.

F1.2 materializó `GenerativeAiProvider`, el adapter OpenAI, prompts y JSON Schemas versionados, structured output y validación local.

F1.3 materializó el análisis de texto durable mediante `POST /analyses/text`, worker con claim/lease/retry y consulta aislada por sesión mediante `GET /analyses/{analysisId}`.

Las especificaciones detalladas se encuentran en:

- [dominio de análisis](../design/analysis-domain.md);
- [contrato de análisis](../design/analysis-contract.md);
- [integración de IA generativa](../development/generative-ai-integration.md);
- [flujo end-to-end de texto](../development/text-analysis-flow.md);
- [roadmap funcional](../roadmap-funcional.md).

## Decisiones pendientes

Permanecen abiertas y deben cerrarse en las fases correspondientes:

- contrato jurídico-funcional definitivo del diagnóstico y criterios de F1.4;
- evolución versionada de `AnalysisResult`;
- tecnología y límites de extracción PDF;
- política de OCR;
- tecnología y límites de transcripción de audio;
- almacenamiento de archivos PDF/audio;
- catálogo institucional y recursos asociados;
- reporte web/PDF y disclaimer institucional;
- política definitiva de retención, minimización, cifrado y anonimización;
- condiciones de borrado físico y adaptación del endpoint ADMIN;
- gobierno del acceso administrativo a consultas y resultados;
- observabilidad productiva y logging estructurado;
- estrategia definitiva de empaquetado y despliegue;
- eventual autenticación futura de STANDARD;
- incorporación de adapters generativos adicionales si se aprueba.

Cuando un usuario ya tenga sesiones, consentimientos, análisis u otra evidencia histórica, la dirección funcional vigente es la desactivación lógica y conservación de trazabilidad. La anonimización de PII, el derecho de eliminación y las condiciones de borrado físico corresponden a la política de gobierno de datos que se cierre antes de producción.

## Estado de fases

| Fase | Alcance | Estado |
|---|---|---|
| F0 | Estabilización técnica | Completada |
| F1.0 | Diseño del dominio de análisis | Completada |
| F1.1 | Sesión STANDARD + consentimiento | Implementada |
| F1.2 | Capa de IA generativa en Java | Implementada |
| F1.3 | Análisis end-to-end desde texto | Implementada |
| F1.4 | Diagnóstico de propiedad intelectual | Siguiente fase |
| F1.5–F1.9 | PDF, audio, reporte, recursos e integración E2E | Pendientes |
| F2 | Preparación para piloto y producción | Pendiente |
