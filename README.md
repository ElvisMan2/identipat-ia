# IDENTIPAT-IA

Repositorio del sistema IDENTIPAT-IA, organizado como una aplicación web con backend Java/Spring Boot, frontend Angular y persistencia PostgreSQL.

## Componentes

- `backend/`: API principal, reglas de negocio y seguridad.
- `frontend/`: interfaz web Angular.
- `preprocessing-service/`: servicio FastAPI interno para futuros preprocesamientos técnicos de PDF y audio.
- `postman/`: colección de pruebas manuales de la API.
- `docs/`: diagnóstico y documentación técnica.
- `docker-compose.yml`: PostgreSQL 16 y pgAdmin para desarrollo local.
- `codex-prompts/`: instrucciones locales para Codex; su contenido no se versiona.

El backend contiene una capa de IA generativa agnóstica al proveedor. OpenAI es el adapter inicial
mediante Responses API; el launcher DEV habilita esta integración y requiere una API key local.
Otros entornos pueden arrancar sin credenciales externas mientras `IDENTIPAT_AI_ENABLED=false`. Consulta la
[guía de integración de IA generativa](docs/development/generative-ai-integration.md).

El flujo de texto persiste la consulta antes de responder `202`, la procesa mediante un worker
PostgreSQL claim/lease y permite consultar el resultado solo desde la misma sesión STANDARD. Consulta
la [guía del flujo de análisis de texto](docs/development/text-analysis-flow.md).

## Requisitos

- JDK 21 (versión oficial del proyecto).
- Docker Compose para PostgreSQL local y Docker Engine para las pruebas de integración.
- Node.js/npm para el frontend.
- Python 3.12 para `preprocessing-service/`.

El backend se construye con el Maven Wrapper incluido; no requiere una instalación global de Maven.

## Backend DEV — inicio rápido

Desde la raíz del repositorio, crea una vez la configuración local, completa los valores privados
necesarios —en especial `OPENAI_API_KEY`— y usa el launcher:

```powershell
Copy-Item .env.example .env
# Edita .env y completa los valores locales/secretos necesarios.
.\scripts\run-backend-dev.ps1
```

El script carga `.env` únicamente en el entorno de su proceso, fuerza
`SPRING_PROFILES_ACTIVE=dev`, asegura el servicio Compose `db` y ejecuta el Maven Wrapper. No levanta
pgAdmin. Puede invocarse desde otra ubicación porque resuelve el repositorio mediante su propia ruta.

Spring Boot no carga `.env` automáticamente. Docker Compose sí usa el `.env` local de la raíz y el
launcher lo carga además para Spring Boot. `application-dev.yml` aporta defaults DEV no sensibles;
`.env` es la configuración local completa y puede contener secretos. `.env.example` es la plantilla
versionada sin secretos reales y `.env` nunca debe versionarse.

Para el arranque manual en Linux/macOS, exporta las variables requeridas y activa el perfil:

```bash
export SPRING_PROFILES_ACTIVE=dev
cd backend
./mvnw spring-boot:run
```

Ejecuta el frontend por separado desde `frontend/` con `npm start`.

La API se publica bajo `http://localhost:8082/identipat-ia` y el frontend de desarrollo utiliza su proxy local `/api`.

## Servicio de preprocesamiento

El servicio Python es un componente técnico interno consumido solo por Java; Angular nunca lo llama
directamente. En desarrollo expone `GET http://127.0.0.1:8090/health`. La URL que usa el backend
puede reemplazarse mediante `PREPROCESSING_SERVICE_BASE_URL`.

Consulta [la guía del servicio de preprocesamiento](docs/development/preprocessing-service.md) para
ejecución local con Python 3.12, pruebas y Docker.

## Documentación de API y Postman

Con el perfil `dev` activo, la especificación OpenAPI y Swagger UI están disponibles en:

- `http://localhost:8082/identipat-ia/v3/api-docs`
- `http://localhost:8082/identipat-ia/swagger-ui.html`

La colección para pruebas manuales está en `postman/identipat-api.collection.json`. Para STANDARD ejecuta **Get CSRF**, reconocimiento/registro, creación de sesión, consentimiento y luego **Create text analysis** / **Get text analysis once**; Postman conserva las cookies y guarda solo los IDs/tokens temporales necesarios. Para ADMIN configura credenciales localmente y usa **Login ADMIN and save JWT**. No versiones documentos reales, cookies, credenciales, JWT ni secretos.

Consulta [la guía de documentación de API](docs/development/api-documentation.md) para la disponibilidad por ambiente y el flujo STANDARD.

## Build y pruebas del backend

Flyway crea y evoluciona el esquema PostgreSQL. Las pruebas de integración usan un PostgreSQL efímero de Testcontainers, por lo que requieren Docker Engine, pero no una instancia PostgreSQL local.

Windows:

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd clean package -DskipTests
```

Linux/macOS:

```bash
cd backend
./mvnw clean test
./mvnw clean package -DskipTests
```

Consulta [la guía de migraciones](docs/development/database-migrations.md) para crear una base nueva y evolucionar el esquema.

## Integración continua

GitHub Actions valida automáticamente el backend (Java 21, Maven, Testcontainers y Flyway) y el
servicio de preprocesamiento (Python 3.12, pytest, Ruff y construcción Docker) en Push y Pull
Request hacia `develop` y `main`. El flujo no despliega ni publica imágenes. Consulta la
[guía de integración continua](docs/development/continuous-integration.md).

## Configuración por ambiente

El perfil se selecciona con `SPRING_PROFILES_ACTIVE`; no hay un perfil activo por defecto. DEV ofrece valores locales no productivos para `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` y `SERVER_PORT`. Las variables siempre pueden reemplazarlos desde el entorno, IDE o mecanismo de despliegue.

PROD exige `SPRING_PROFILES_ACTIVE=prod` y valores externos para `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `STANDARD_SESSION_PEPPER`, `CONSENT_CURRENT_VERSION`, `CONSENT_DOCUMENT_SHA256` y `CORS_ALLOWED_ORIGINS`. No existen fallbacks productivos para secretos, credenciales ni evidencia legal versionada.

La IA se controla con `IDENTIPAT_AI_ENABLED` y `IDENTIPAT_AI_PROVIDER`. DEV habilita OpenAI por
defecto, usa `gpt-5-mini` y `60s` como defaults reemplazables, y exige `OPENAI_API_KEY` local.
PROD continúa requiriendo `OPENAI_API_KEY`, `OPENAI_MODEL` y `OPENAI_TIMEOUT` explícitos.

La sesión STANDARD es server-side y usa `IDENTIPAT_STANDARD_SESSION` HttpOnly; no es login ni JWT. Las mutaciones de sesión/consentimiento requieren `XSRF-TOKEN` y `X-XSRF-TOKEN`. El backend no devuelve PII, IDs internos ni el token de sesión en esos contratos.

El análisis de texto usa las variables `ANALYSIS_*` para límites y worker. Sus valores predeterminados
son técnicos para DEV/TEST y todos pueden sobrescribirse por ambiente.

Spring Boot y Maven no cargan automáticamente un archivo `.env`; el launcher PowerShell lo carga en
el proceso para DEV y Docker Compose lo lee desde la raíz. Consulta [la guía de configuración](docs/development/configuration.md) y [.env.example](.env.example) para conocer todas las variables.
