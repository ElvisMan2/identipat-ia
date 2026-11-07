# Despliegue QA de la imagen integrada

Este Compose despliega únicamente la aplicación integrada Java + Angular. PostgreSQL (`db`) y el
servicio Python (`preprocessing`) continúan como servicios independientes en la red externa
`identipat-qa_internal`; no se publican sus puertos. La imagen se construye y publica en CI, nunca
en el host QA de 1 vCPU/2 GiB.

## Preparación y migración segura

1. Confirmar que el Compose existente está activo y que `docker network inspect
   identipat-qa_internal` contiene `db` y `preprocessing`.
2. Copiar `.env.example` a `.env` fuera del control de versiones y completar los secretos.
3. Iniciar sesión en GHCR si el paquete es privado: `echo "$GHCR_TOKEN" | docker login ghcr.io -u
   USUARIO --password-stdin`. El token requiere `read:packages`; además, el usuario debe tener
   acceso al repositorio/paquete privado.
4. Sustituir `IDENTIPAT_IMAGE` por el digest reportado por CI, con formato
   `ghcr.io/elvisman2/identipat-ia@sha256:...`, y ejecutar:

   ```bash
   docker compose --env-file .env -f compose.yml config
   docker compose --env-file .env -f compose.yml pull app
   docker compose --env-file .env -f compose.yml up -d app
   ```

No ejecutar `docker compose down -v`, no borrar `identipat-qa_postgres_data` y no recrear el
servicio `db`. Este proyecto usa la red existente y no declara el volumen PostgreSQL. Antes de la
migración conviene verificar backup/restauración y anotar el digest actualmente desplegado.

## Verificación

```bash
docker compose --env-file .env -f compose.yml ps
docker compose --env-file .env -f compose.yml logs --tail=200 app
curl --fail http://127.0.0.1:8082/identipat-ia/
docker stats --no-stream identipat-qa-app-app-1
```

En logs debe observarse que Flyway valida/aplica las migraciones y que Spring inicia sin errores.
El contenedor limita la aplicación a 768 MiB y 0.75 CPU; revisar uso sostenido, reinicios y OOM.
Python (en particular la transcripción de audio) y PostgreSQL comparten el host, por lo que una
prueba de carga sigue siendo necesaria antes de aumentar workers o límites.

## Rollback

Restaurar en `.env` el digest anterior (no solo un tag), ejecutar `pull app` y luego `up -d app`.
Las migraciones Flyway son hacia adelante: si una versión introduce una migración incompatible,
la reversión de imagen exige el procedimiento de base de datos aprobado y un backup verificado;
nunca se debe eliminar el volumen para forzar el rollback.
