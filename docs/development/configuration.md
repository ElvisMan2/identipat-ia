# Configuración por ambientes

El backend requiere JDK 21 y usa los perfiles explícitos `dev`, `test` y `prod`. No hay un perfil activo global: selecciónalo con `SPRING_PROFILES_ACTIVE` desde el entorno, el IDE o el mecanismo de despliegue.

## Archivos y responsabilidades

- `application.yml`: nombre y contexto de la aplicación, driver/dialecto PostgreSQL, formato y zona horaria, recursos estáticos empaquetados y expiración JWT comunes.
- `application-dev.yml`: ejecución local en el puerto 8082, PostgreSQL en `localhost:5433`, ruta local opcional del build Angular, CORS para Angular local y credenciales/secreto conocidos exclusivamente de desarrollo.
- `application-test.yml`: configuración determinista de pruebas, secreto JWT exclusivo de test y políticas Flyway/JPA; el datasource lo aporta Testcontainers.
- `application-prod.yml`: conexión y secretos obligatorios desde el entorno, CORS explícito y `ddl-auto=validate`.

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
| `SERVER_PORT` | `8082` | `0` (puerto aleatorio) | `8082` | Puerto HTTP |
| `APP_TIME_ZONE` | `America/Lima` | `America/Lima` | `America/Lima` | Zona de Jackson y JDBC/Hibernate |
| `STATIC_LOCATIONS` | Classpath y build Angular local | Solo classpath | Solo classpath | Ubicaciones de recursos, separadas por coma |
| `PGADMIN_EMAIL` | Sin default en Compose | No aplica | No aplica | Cuenta local de pgAdmin |
| `PGADMIN_PASSWORD` | Sin default en Compose | No aplica | No aplica | Password local de pgAdmin |

Los valores DEV son conocidos, no productivos y pueden reemplazarse desde el entorno. TEST recibe la conexión a PostgreSQL mediante `@ServiceConnection`; no usa variables de base DEV. PROD no tiene fallback para URL, usuario o password de base de datos, secreto JWT ni orígenes CORS; un placeholder obligatorio sin resolver impide crear los componentes que consumen esa configuración.

## PostgreSQL y esquema

Flyway es la fuente de verdad del esquema en DEV, TEST y PROD. En los tres perfiles `spring.flyway.enabled=true`, `baseline-on-migrate=false` y `spring.jpa.hibernate.ddl-auto=validate`: Flyway aplica las migraciones antes de que Hibernate valide, y Hibernate nunca crea ni evoluciona tablas.

DEV apunta por defecto a PostgreSQL local en `localhost:5433`. TEST usa un PostgreSQL 16 efímero con puerto dinámico mediante Testcontainers y no depende de PostgreSQL local. PROD aplica migraciones versionadas sin baseline automático antes de la validación de Hibernate. Consulta [database-migrations.md](database-migrations.md).

## Archivos `.env`

`.env` permanece ignorado por Git y `.env.example` contiene únicamente ejemplos locales. Docker Compose sí puede leer `.env` desde la raíz, pero Maven y Spring Boot no lo cargan automáticamente. Para el backend, exporta las variables en el proceso, configúralas en el IDE o proporciónalas mediante el contenedor/plataforma de despliegue. No se incorpora ninguna dependencia dotenv.
