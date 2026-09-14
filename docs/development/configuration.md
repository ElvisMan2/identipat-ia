# Configuración por ambientes

El backend requiere JDK 21 y usa los perfiles explícitos `dev`, `test` y `prod`. No hay un perfil activo global: selecciónalo con `SPRING_PROFILES_ACTIVE` desde el entorno, el IDE o el mecanismo de despliegue.

## Archivos y responsabilidades

- `application.yml`: nombre y contexto de la aplicación, driver/dialecto PostgreSQL, formato y zona horaria, recursos estáticos empaquetados y expiración JWT comunes.
- `application-dev.yml`: ejecución local en el puerto 8082, PostgreSQL en `localhost:5433`, ruta local opcional del build Angular, CORS para Angular local, credenciales/secreto conocidos exclusivamente de desarrollo, URL local del servicio de preprocesamiento y OpenAPI/Swagger habilitados.
- `application-test.yml`: configuración determinista de pruebas, secreto JWT exclusivo de test, URL dummy del servicio de preprocesamiento, políticas Flyway/JPA y OpenAPI/Swagger habilitados; el datasource lo aporta Testcontainers.
- `application-prod.yml`: conexión y secretos obligatorios desde el entorno, CORS explícito, URL obligatoria del servicio de preprocesamiento, `ddl-auto=validate` y OpenAPI/Swagger deshabilitados por defecto.

## Activación

Desarrollo en PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
cd backend
.\mvnw.cmd spring-boot:run
```

Desarrollo en Linux/macOS:

```bash
export SPRING_PROFILES_ACTIVE=dev
cd backend
./mvnw spring-boot:run
```

Las pruebas Spring declaran `@ActiveProfiles("test")`. Para una ejecución manual equivalente puede usarse `SPRING_PROFILES_ACTIVE=test`.

Producción requiere `SPRING_PROFILES_ACTIVE=prod` además de todas las variables obligatorias listadas abajo. No se activa producción automáticamente.

## Variables

| Variable | DEV | TEST | PROD | Propósito |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` explícito | `test` mediante pruebas | `prod` obligatorio | Selección de perfil |
| `DB_URL` | Default JDBC a `localhost:5433` | No aplica; Testcontainers aporta una URL dinámica | Obligatoria | URL JDBC PostgreSQL |
| `DB_NAME` | `identipatdb`, usada si no se define `DB_URL` | No aplica | No se usa como sustituto de `DB_URL` | Nombre de base local |
| `DB_USER` | `identipat_dev_user` | No aplica; generado para el contenedor efímero | Obligatoria | Usuario PostgreSQL |
| `DB_PASSWORD` | `dev_password_123` | No aplica; generado para el contenedor efímero | Obligatoria | Password PostgreSQL |
| `JWT_SECRET` | Secreto conocido solo DEV | No se usa; hay un secreto fijo exclusivo de test | Obligatoria | Firma JWT; mínimo práctico de 32 bytes para el algoritmo actual |
| `JWT_EXPIRATION_MS` | `3600000` | `3600000` | `3600000` si no se reemplaza | Vigencia del JWT en milisegundos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | `http://localhost:4200` | Obligatoria | Orígenes exactos separados por coma; `*` no está permitido con credenciales |
| `STANDARD_SESSION_PEPPER` | Base64 fijo no productivo | Base64 fijo de test | Obligatoria | Secreto independiente, mínimo 32 bytes decodificados, para HMAC-SHA-256 |
| `STANDARD_SESSION_INACTIVITY_TIMEOUT` | `30m` | `30m` | `30m` si no se reemplaza | Timeout renovable por actividad |
| `STANDARD_SESSION_ABSOLUTE_TIMEOUT` | `8h` | `8h` | `8h` si no se reemplaza | Límite absoluto no renovable |
| `STANDARD_SESSION_COOKIE_SECURE` | `false` | `false` | Forzado a `true` | Permite HTTP solo en desarrollo/pruebas |
| `CONSENT_CURRENT_VERSION` | `personal-data/1.0` de prueba | Igual | Obligatoria | Versión del documento legal vigente |
| `CONSENT_DOCUMENT_SHA256` | 64 ceros como placeholder | Igual | Obligatoria | SHA-256 hex lowercase del documento aprobado externamente |
| `SERVER_PORT` | `8082` | `0` (puerto aleatorio) | `8082` | Puerto HTTP |
| `APP_TIME_ZONE` | `America/Lima` | `America/Lima` | `America/Lima` | Zona de Jackson y JDBC/Hibernate |
| `STATIC_LOCATIONS` | Classpath y build Angular local | Solo classpath | Solo classpath | Ubicaciones de recursos, separadas por coma |
| `PREPROCESSING_SERVICE_BASE_URL` | `http://localhost:8090` si no se define | No aplica; se usa `http://preprocessing-service.test` | Obligatoria | URL base del servicio Python consumido internamente por Java |
| `IDENTIPAT_AI_ENABLED` | `false` | `false` | Obligatoria | Activa la creación del adapter LLM |
| `IDENTIPAT_AI_PROVIDER` | `openai` | `openai` | Obligatoria | Selecciona el provider; F1.2 admite `openai` |
| `OPENAI_API_KEY` | Sin default efectivo | Vacía con IA deshabilitada | Obligatoria al habilitar OpenAI | Secreto del SDK oficial |
| `OPENAI_MODEL` | Sin default efectivo | Vacío con IA deshabilitada | Obligatoria al habilitar OpenAI | Modelo Responses configurado externamente |
| `OPENAI_TIMEOUT` | `60s` | `60s` | Obligatoria al habilitar OpenAI | Timeout total positivo |
| `ANALYSIS_TEXT_MIN_LENGTH` | `20` | `20` | Configurable | Límite técnico mínimo aplicado al texto normalizado |
| `ANALYSIS_TEXT_MAX_LENGTH` | `20000` | `20000` | Configurable | Límite técnico máximo aplicado al texto normalizado |
| `ANALYSIS_WORKER_ENABLED` | `true` | `false` | Configurable | Activa el poller; los tests pueden invocar un ciclo manual |
| `ANALYSIS_POLL_INTERVAL` | `2s` | `2s` | Configurable | Intervalo positivo entre polls |
| `ANALYSIS_WORKER_THREADS` | `2` | `2` | Configurable | Threads del executor acotado |
| `ANALYSIS_WORKER_QUEUE_CAPACITY` | `2` | `2` | Configurable | Capacidad local máxima en espera |
| `ANALYSIS_LEASE_DURATION` | `120s` | `120s` | Configurable | Duración positiva del lease PostgreSQL |
| `ANALYSIS_MAX_ATTEMPTS` | `2` | `2` | Configurable | Máximo de invocaciones por análisis |
| `ANALYSIS_RETRY_DELAY` | `5s` | `5s` | Configurable | Backoff durable mediante `next_attempt_at` |
| `ANALYSIS_AI_MAX_OUTPUT_TOKENS` | `4000` | `4000` | Configurable | Límite enviado en `GenerationOptions`; evita truncar la salida estructurada validada |
| `PGADMIN_EMAIL` | Sin default en Compose | No aplica | No aplica | Cuenta local de pgAdmin |
| `PGADMIN_PASSWORD` | Sin default en Compose | No aplica | No aplica | Password local de pgAdmin |

Los valores DEV son conocidos, no productivos y pueden reemplazarse desde el entorno. TEST recibe la conexión a PostgreSQL mediante `@ServiceConnection`; no usa variables de base DEV. PROD no tiene fallback para URL, usuario o password de base de datos, secreto JWT, pepper, versión/hash de consentimiento, orígenes CORS ni configuración OpenAI; un placeholder obligatorio sin resolver impide crear los componentes que consumen esa configuración. El hash no es texto legal: debe calcularse sobre el documento aprobado externamente.

## Servicio de preprocesamiento

Java se comunica internamente con `preprocessing-service` mediante `app.preprocessing.base-url` y un
cliente `RestClient`, con timeout de conexión y lectura de cinco segundos. DEV usa
`http://localhost:8090` y permite reemplazarlo con `PREPROCESSING_SERVICE_BASE_URL`; TEST tiene una
URL dummy y sus pruebas usan HTTP simulado; PROD exige esa variable sin fallback. El backend no
consulta el health del servicio al iniciar. Consulta [preprocessing-service.md](preprocessing-service.md).

## IA generativa y OpenAI

`IDENTIPAT_AI_ENABLED` activa la capa LLM y `IDENTIPAT_AI_PROVIDER` selecciona su adapter. DEV y
TEST usan `false` y `openai` como defaults; por ello el backend arranca y ejecuta la suite normal sin
credenciales ni tráfico externo. PROD exige ambos valores explícitos.

Cuando `IDENTIPAT_AI_ENABLED=true` y `IDENTIPAT_AI_PROVIDER=openai`, son obligatorios:

| Variable | Uso |
| --- | --- |
| `OPENAI_API_KEY` | Secreto de autenticación; nunca se registra ni se versiona. |
| `OPENAI_MODEL` | Modelo Responses compatible; no existe valor por defecto. |
| `OPENAI_TIMEOUT` | Timeout total de la llamada. DEV/TEST usan `60s`; PROD no tiene fallback. |

OpenAI no se contacta durante el arranque ni desde health checks. Los retries internos están
deshabilitados. Consulta [generative-ai-integration.md](generative-ai-integration.md) para el diseño y
el smoke test manual.

## Análisis de texto y worker

Los valores `20`/`20000` son defaults técnicos DEV/TEST y no deben presentarse como una regla
institucional. La creación de análisis exige `IDENTIPAT_AI_ENABLED=true`; si está deshabilitada,
responde `503` y no persiste la consulta. PostgreSQL conserva la cola durable y el executor Java solo
procesa claims ya confirmados. `ANALYSIS_WORKER_ENABLED=false` detiene el scheduler sin eliminar los
métodos internos de un ciclo usados por pruebas.

`ANALYSIS_LEASE_DURATION` debe ser mayor que el timeout efectivo del proveedor, con margen operativo,
porque F1.3 no implementa heartbeat. Duraciones y enteros deben ser positivos; la capacidad de cola
puede ser cero. Consulta [text-analysis-flow.md](text-analysis-flow.md).

## PostgreSQL y esquema

Flyway es la fuente de verdad del esquema en DEV, TEST y PROD. En los tres perfiles `spring.flyway.enabled=true`, `baseline-on-migrate=false` y `spring.jpa.hibernate.ddl-auto=validate`: Flyway aplica las migraciones antes de que Hibernate valide, y Hibernate nunca crea ni evoluciona tablas.

DEV apunta por defecto a PostgreSQL local en `localhost:5433`. TEST usa un PostgreSQL 16 efímero con puerto dinámico mediante Testcontainers y no depende de PostgreSQL local. PROD aplica migraciones versionadas sin baseline automático antes de la validación de Hibernate. Consulta [database-migrations.md](database-migrations.md).

## OpenAPI y Swagger

Los perfiles `dev` y `test` habilitan `springdoc.api-docs` y `springdoc.swagger-ui`; `prod` los deshabilita explícitamente. No hay una variable de entorno que los active en producción por accidente. Las rutas de documentación se publican bajo el contexto de la aplicación: `/identipat-ia/v3/api-docs` y `/identipat-ia/swagger-ui.html` en desarrollo local. Consulta [api-documentation.md](api-documentation.md).

## Archivos `.env`

`.env` permanece ignorado por Git y `.env.example` contiene únicamente ejemplos locales. Docker Compose sí puede leer `.env` desde la raíz, pero Maven y Spring Boot no lo cargan automáticamente. Para el backend, exporta las variables en el proceso, configúralas en el IDE o proporciónalas mediante el contenedor/plataforma de despliegue. No se incorpora ninguna dependencia dotenv.
