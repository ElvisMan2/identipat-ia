# IDENTIPAT-IA

## Arquitectura del repositorio

- `frontend/`: aplicación Angular.
- `backend/`: backend principal Java/Spring Boot.
- `preprocessing-service/`: servicio FastAPI de preprocesamiento técnico especializado; actualmente solo expone `/health`, y PDF/audio se implementarán en una fase posterior.
- `docs/`: documentación técnica y de arquitectura.
- `postman/`: colecciones de pruebas de API.

## Responsabilidades y límites

- Angular es responsable de presentación, interacción, navegación, captura de texto/PDF/audio, resultados y sesión visual actual. Solo se comunica con Java; nunca directamente con PostgreSQL, Python, Gemini ni otro proveedor LLM.
- Java/Spring Boot es dueño del caso de negocio: API, usuarios ADMIN y STANDARD, reglas de negocio, persistencia, consentimiento, disclaimer, análisis, orquestación, reportes y comunicación con Python.
- Java/Spring Boot integra y ejecuta la inferencia con IA generativa. La integración abstrae el proveedor; OpenAI es el primer adapter técnico y Gemini queda como proveedor futuro.
- Python se limita al preprocesamiento especializado de PDF, audio y otras entradas técnicas. No es un backend funcional y no implementa inferencia LLM, prompts, integración Gemini, reglas de negocio, persistencia ni autenticación.
- PostgreSQL es el sistema de persistencia principal. Durante desarrollo se almacenan internamente consultas y análisis, aunque STANDARD no tenga historial visible.

## Modelo de acceso

- Solo ADMIN se autentica mediante credenciales, Spring Security y JWT para funciones administrativas.
- STANDARD no hace login, no recibe JWT y no tiene perfil ni historial visibles. Su DNI/CE solo identifica o reconoce si existe un registro; no es una contraseña, credencial ni factor de autenticación.
- El consentimiento para tratamiento de datos y el disclaimer orientativo del diagnóstico son conceptos separados.

## Principios de desarrollo

- No introducir refactors ajenos a una tarea acotada.
- Preservar el comportamiento existente salvo requerimiento explícito.
- Agregar o actualizar pruebas cuando cambie el comportamiento.
- No convertir Python en un segundo backend funcional ni permitir integraciones directas Angular → Python/LLM.
- No confirmar secretos ni archivos locales de entorno.
- Los cambios en workflows de CI deben preservar la validación de ambos stacks: backend con Maven
  Wrapper, Testcontainers y Flyway; y `preprocessing-service` con Python 3.12, pytest, Ruff y Docker.
- `Analysis` y su cola de trabajo son durables en PostgreSQL; un executor local nunca es source of truth.
- Los workers reclaman trabajo mediante claim/lease PostgreSQL y ninguna espera al LLM mantiene una transacción o conexión DB abierta.
- Cada llamada real a `GenerativeAiProvider` corresponde a una `AiInvocation`; los retries pertenecen al orquestador de `Analysis`, no al SDK.
- STANDARD solo puede leer un análisis de la misma `session_id` activa que lo creó.
- Inputs, prompts, respuestas raw/structured y resultados completos no se escriben en logs; metadata técnica del proveedor y de invocaciones tampoco se expone a STANDARD.
