# IDENTIPAT-IA

Repositorio del sistema IDENTIPAT-IA, organizado como una aplicación web con backend Java/Spring Boot, frontend Angular y persistencia PostgreSQL.

## Componentes

- `backend/`: API principal, reglas de negocio y seguridad.
- `frontend/`: interfaz web Angular.
- `postman/`: colección de pruebas manuales de la API.
- `docs/`: diagnóstico y documentación técnica.
- `docker-compose.yml`: PostgreSQL 16 y pgAdmin para desarrollo local.
- `codex-prompts/`: instrucciones locales para Codex; su contenido no se versiona.

## Requisitos

- JDK 21 (versión oficial del proyecto).
- Docker Compose para PostgreSQL local y Docker Engine para las pruebas de integración.
- Node.js/npm para el frontend.

El backend se construye con el Maven Wrapper incluido; no requiere una instalación global de Maven.

## Ejecución local (perfil DEV)

1. Usa `.env.example` como referencia para configurar PostgreSQL y pgAdmin. `.env` está ignorado por Git.
2. Inicia PostgreSQL y pgAdmin con `docker compose up -d` desde la raíz.
3. Selecciona explícitamente el perfil `dev` y ejecuta el backend.

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
cd backend
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
export SPRING_PROFILES_ACTIVE=dev
cd backend
./mvnw spring-boot:run
```

4. Ejecuta el frontend desde `frontend/` con `npm start`.

La API se publica bajo `http://localhost:8082/identipat-ia` y el frontend de desarrollo utiliza su proxy local `/api`.

## Documentación de API y Postman

Con el perfil `dev` activo, la especificación OpenAPI y Swagger UI están disponibles en:

- `http://localhost:8082/identipat-ia/v3/api-docs`
- `http://localhost:8082/identipat-ia/swagger-ui.html`

La colección para pruebas manuales está en `postman/identipat-api.collection.json`. Configura `adminDoi` y `adminPassword` como variables locales en Postman, ejecuta **Login ADMIN and save JWT** y la colección almacenará el resultado en `adminJwt` para las solicitudes administrativas Bearer. No versiones credenciales ni JWT.

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

## Configuración por ambiente

El perfil se selecciona con `SPRING_PROFILES_ACTIVE`; no hay un perfil activo por defecto. DEV ofrece valores locales no productivos para `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` y `SERVER_PORT`. Las variables siempre pueden reemplazarlos desde el entorno, IDE o mecanismo de despliegue.

PROD exige `SPRING_PROFILES_ACTIVE=prod` y valores externos para `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS`. No existen fallbacks productivos para secretos o credenciales.

Spring Boot y Maven no cargan automáticamente un archivo `.env`; este sirve como referencia y para Docker Compose. Consulta [la guía de configuración](docs/development/configuration.md) y [.env.example](.env.example) para conocer todas las variables.
