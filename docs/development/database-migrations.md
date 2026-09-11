# Migraciones de base de datos

## Política del esquema

Flyway es la única fuente de verdad para crear y evolucionar el esquema PostgreSQL de IDENTIPAT-IA. La migración `V1__baseline_schema.sql` es la primera migración ejecutable sobre una base vacía; no es un baseline administrativo ni adopta un esquema creado anteriormente por Hibernate.

DEV, TEST y PROD tienen Flyway habilitado, `baseline-on-migrate=false` y `spring.jpa.hibernate.ddl-auto=validate`. Al iniciar el backend, Flyway aplica las migraciones pendientes y después Hibernate valida que el esquema corresponda al modelo JPA. Hibernate no crea ni modifica tablas.

## Convención y evolución

Las migraciones residen en `backend/src/main/resources/db/migration/` y siguen la convención:

```text
V{n}__descripcion.sql
```

Por ejemplo, un cambio posterior se incorpora como `V2__descripcion_del_cambio.sql`, luego `V3__otra_evolucion.sql`. Una migración aplicada nunca se edita ni se reemplaza: cualquier modificación del esquema requiere una migración nueva. No se insertan secretos ni datos personales en migraciones estructurales.

Flyway registra versiones, checksums, tiempo de ejecución y resultado en `flyway_schema_history`. Esta tabla permite verificar qué migraciones se aplicaron en cada base.

## Inicializar una base nueva

1. Crea una base PostgreSQL vacía y un usuario con permisos para crear objetos en su esquema.
2. Configura el perfil y datasource correspondientes.
3. Inicia el backend.
4. Verifica que `flyway_schema_history` contenga V1 con `success=true` y que Hibernate complete su validación.

No crees tablas manualmente, no uses Hibernate para generarlas y no habilites baseline automático.

## Reset de una base DEV descartable

Un reset es válido únicamente para la base local de desarrollo del proyecto cuando se haya confirmado que no es compartida, remota ni productiva y que sus datos son descartables. El destino local previsto por Compose es:

```text
host: localhost
port: 5433
database: identipatdb
service: db
volume: identipat-ia_postgres_data
```

Después de confirmar esos datos, puede recrearse la base o el volumen del servicio PostgreSQL. Al volver a iniciar el backend con perfil `dev`, la base debe permanecer sin tablas de aplicación hasta que Flyway ejecute V1. Nunca se usa este procedimiento en PROD ni sobre una base compartida o remota.

## Pruebas de integración

Los tests que requieren persistencia levantan un único PostgreSQL 16 efímero por clase mediante Testcontainers y `@ServiceConnection`. El contenedor comienza con una base vacía, asigna un puerto dinámico y entrega el datasource a Spring Boot. Flyway aplica V1 y Hibernate valida el resultado antes de iniciar el contexto.

Docker Engine debe estar disponible para ejecutar:

```powershell
cd backend
.\mvnw.cmd clean test
```

No es necesario iniciar PostgreSQL DEV ni configurar variables exclusivas para Testcontainers.

## Producción

PROD usa las mismas migraciones versionadas y nunca activa `baseline-on-migrate`. Antes de desplegar una versión debe revisarse la migración nueva, respaldarse la base según el procedimiento operativo y verificarse el resultado de Flyway. No se ejecutan pruebas contra producción.
