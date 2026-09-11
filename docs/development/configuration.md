# Configuración por ambientes

El backend requiere JDK 21 y usa los perfiles explícitos `dev`, `test` y `prod`. No hay un perfil activo global: selecciónalo con `SPRING_PROFILES_ACTIVE` desde el entorno, el IDE o el mecanismo de despliegue.

## Archivos y responsabilidades

- `application.yml`: nombre y contexto de la aplicación, driver/dialecto PostgreSQL, formato y zona horaria, recursos estáticos empaquetados y expiración JWT comunes.
- `application-dev.yml`: ejecución local en el puerto 8082, PostgreSQL en `localhost:5433`, ruta local opcional del build Angular, CORS para Angular local y credenciales/secreto conocidos exclusivamente de desarrollo.
- `application-test.yml`: configuración determinista de pruebas, secreto JWT exclusivo de test y dependencia temporal de PostgreSQL local.
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
| `DB_URL` | Default JDBC a `localhost:5433` | Mismo default local temporal | Obligatoria | URL JDBC PostgreSQL |
| `DB_NAME` | `identipatdb`, usada si no se define `DB_URL` | `identipatdb`, usada si no se define `DB_URL` | No se usa como sustituto de `DB_URL` | Nombre de base local |
| `DB_USER` | `identipat_dev_user` | `identipat_dev_user` temporal | Obligatoria | Usuario PostgreSQL |
| `DB_PASSWORD` | `dev_password_123` | `dev_password_123` temporal | Obligatoria | Password PostgreSQL |
| `JWT_SECRET` | Secreto conocido solo DEV | No se usa; hay un secreto fijo exclusivo de test | Obligatoria | Firma JWT; mínimo práctico de 32 bytes para el algoritmo actual |
| `JWT_EXPIRATION_MS` | `3600000` | `3600000` | `3600000` si no se reemplaza | Vigencia del JWT en milisegundos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | `http://localhost:4200` | Obligatoria | Orígenes exactos separados por coma; `*` no está permitido con credenciales |
| `SERVER_PORT` | `8082` | `0` (puerto aleatorio) | `8082` | Puerto HTTP |
| `APP_TIME_ZONE` | `America/Lima` | `America/Lima` | `America/Lima` | Zona de Jackson y JDBC/Hibernate |
| `STATIC_LOCATIONS` | Classpath y build Angular local | Solo classpath | Solo classpath | Ubicaciones de recursos, separadas por coma |
| `PGADMIN_EMAIL` | Sin default en Compose | No aplica | No aplica | Cuenta local de pgAdmin |
| `PGADMIN_PASSWORD` | Sin default en Compose | No aplica | No aplica | Password local de pgAdmin |

Los valores DEV/TEST son conocidos, no productivos y pueden reemplazarse desde el entorno. PROD no tiene fallback para URL, usuario o password de base de datos, secreto JWT ni orígenes CORS; un placeholder obligatorio sin resolver impide crear los componentes que consumen esa configuración.

## PostgreSQL y esquema

DEV y TEST usan temporalmente `spring.jpa.hibernate.ddl-auto=update`, que no elimina tablas ni datos. TEST aún espera PostgreSQL disponible en `localhost:5433` y, por compatibilidad con las pruebas existentes, puede compartir las credenciales/base local indicadas. Esta limitación termina en F0.5 con Flyway y Testcontainers.

PROD usa `ddl-auto=validate`: Hibernate no debe crear ni modificar el esquema. Después de F0.5, el esquema productivo deberá haber sido aplicado mediante Flyway antes del arranque.

## Archivos `.env`

`.env` permanece ignorado por Git y `.env.example` contiene únicamente ejemplos locales. Docker Compose sí puede leer `.env` desde la raíz, pero Maven y Spring Boot no lo cargan automáticamente. Para el backend, exporta las variables en el proceso, configúralas en el IDE o proporciónalas mediante el contenedor/plataforma de despliegue. No se incorpora ninguna dependencia dotenv.
