# Despliegue QA de Java/Angular y preprocessing

Este Compose despliega dos imágenes independientes: la aplicación integrada Java + Angular y
`preprocessing-service`. PostgreSQL (`db`) continúa administrado por el Compose existente y se
alcanza mediante la red externa `identipat-qa_internal`. Python también usa esa red, pero el puerto
8090 solo se expone entre contenedores y nunca se publica en el host.

El caché de modelos se conserva en el volumen `identipat-qa_preprocessing_models`. Las imágenes se
construyen y publican en CI; no se compilan en el host QA de 1 vCPU/2 GiB.

## Preparación y migración segura

1. Registrar los digests desplegados, comprobar el backup/restauración de PostgreSQL e inspeccionar
   `identipat-qa_internal`. La red debe contener el servicio `db` con ese alias.
2. Si ya existe otro contenedor Python con el alias `preprocessing`, identificar su Compose y su
   volumen antes de detener solo ese servicio. No detener ni recrear `db`.
3. Copiar `.env.example` a `.env` fuera del control de versiones y completar secretos y ambos
   digests:

   ```dotenv
   IDENTIPAT_IMAGE=ghcr.io/elvisman2/identipat-ia@sha256:...
   PREPROCESSING_IMAGE=ghcr.io/elvisman2/identipat-preprocessing@sha256:...
   ```

   Generar el secreto HMAC de idempotencia con un CSPRNG, por ejemplo:

   ```bash
   openssl rand -base64 48
   ```

   Guardarlo en `ANALYSIS_IDEMPOTENCY_HMAC_SECRET` sin imprimirlo en logs ni incorporarlo a Git. Debe
   ser idéntico en todas las réplicas y conservarse entre despliegues al menos durante toda la ventana
   `ANALYSIS_IDEMPOTENCY_RETENTION`; cambiarlo directamente impide encontrar replays aún vigentes.

4. Si los paquetes son privados, iniciar sesión en GHCR con un token `read:packages` y acceso a
   ambos paquetes:

   ```bash
   echo "$GHCR_TOKEN" | docker login ghcr.io -u USUARIO --password-stdin
   ```

5. Antes de descargar, verificar que no quede ningún `REPLACE_ME`, que las imágenes usen un digest
   `@sha256:...`, que `ANALYSIS_ADMISSION_ENABLED=true` y que se cumplan estrictamente estas relaciones:

   - `ANALYSIS_ADMISSION_RESERVATION_TTL` supera `PREPROCESSING_AUDIO_TIMEOUT` más
     `ANALYSIS_ADMISSION_RESERVATION_SAFETY_MARGIN`.
   - `ANALYSIS_IDEMPOTENCY_CLAIM_TTL` supera `ANALYSIS_ADMISSION_RESERVATION_TTL`.
   - `ANALYSIS_IDEMPOTENCY_RETENTION` supera el claim TTL y 24 horas.

   Para el corte que haga obligatorio el header, activar
   `ANALYSIS_IDEMPOTENCY_REQUIRED=true` solo después de confirmar que todos los clientes envían un
   UUID v4 distinto por intención. La colección Postman y Angular ya lo envían.

6. Validar y descargar sin construir en el host. `config` debe fallar si el secreto HMAC está vacío o
   ausente:

   ```bash
   docker compose --env-file .env -f compose.yml config
   docker compose --env-file .env -f compose.yml pull app preprocessing
   docker compose --env-file .env -f compose.yml up -d preprocessing
   docker compose --env-file .env -f compose.yml up -d app
   ```

7. Confirmar en los logs que `app` arranca con `SPRING_PROFILES_ACTIVE=prod`, Flyway llega a la última
   migración y no aparece ningún error de binding/validación temporal. No copiar a tickets ni chats la
   salida de `docker compose config`, porque renderiza secretos del `.env`.

`app` espera el health general de `preprocessing`, pero ese health no espera a que Whisper termine
de cargar. No ejecutar `docker compose down -v`, no borrar `identipat-qa_postgres_data`, no recrear
`db` y no eliminar `identipat-qa_preprocessing_models` durante un despliegue normal.

## Health general y carga inicial de Whisper

Como 8090 no está publicado, las verificaciones se ejecutan dentro del contenedor. `/health`
confirma que FastAPI atiende PDF y el contrato general:

```bash
docker compose --env-file .env -f compose.yml exec -T preprocessing python - <<'PY'
import json
import urllib.request

with urllib.request.urlopen("http://127.0.0.1:8090/health", timeout=3) as response:
    print(response.status, json.load(response))
PY
```

`/v1/audio/health` devuelve `503 AUDIO_MODEL_NOT_READY` mientras el modelo se descarga o carga y
`200` cuando queda listo. Repetir esta consulta durante la puesta en marcha:

```bash
docker compose --env-file .env -f compose.yml exec -T preprocessing python - <<'PY'
import urllib.error
import urllib.request

request = urllib.request.Request("http://127.0.0.1:8090/v1/audio/health")
try:
    with urllib.request.urlopen(request, timeout=5) as response:
        print(response.status, response.read().decode())
except urllib.error.HTTPError as error:
    print(error.code, error.read().decode())
    raise SystemExit(1)
PY
```

En un volumen vacío, el primer arranque necesita salida de red hacia el repositorio del modelo. El
modelo queda bajo `/models`; reinicios posteriores reutilizan el volumen. Seguir la carga y revisar
el tamaño del caché con:

```bash
docker compose --env-file .env -f compose.yml logs -f preprocessing
docker compose --env-file .env -f compose.yml exec -T preprocessing du -sh /models
```

Que `/health` esté verde no demuestra que haya memoria suficiente para cargar Whisper o transcribir
audio. La disponibilidad de audio solo la confirma `/v1/audio/health`, y aun así debe ejecutarse una
prueba controlada de transcripción antes de habilitar carga real.

## Recursos y observabilidad

Los defaults del laboratorio son 768 MiB/0.75 CPU para Java y 640 MiB/0.50 CPU para Python. Son
límites iniciales ajustables mediante `APP_MEMORY_LIMIT`, `APP_CPU_LIMIT`,
`PREPROCESSING_MEMORY_LIMIT` y `PREPROCESSING_CPU_LIMIT`; no constituyen una capacidad garantizada
para el modelo `base` ni para audio de 180 segundos. PostgreSQL y el sistema operativo también
compiten por los 2 GiB del host.

```bash
preprocessing_id="$(docker compose --env-file .env -f compose.yml ps -q preprocessing)"
app_id="$(docker compose --env-file .env -f compose.yml ps -q app)"
docker stats --no-stream "$preprocessing_id" "$app_id"
docker inspect --format 'OOMKilled={{.State.OOMKilled}} restarts={{.RestartCount}}' "$preprocessing_id"
docker compose --env-file .env -f compose.yml ps
docker compose --env-file .env -f compose.yml logs --tail=200 app preprocessing
```

Revisar memoria durante el warm-up y durante una transcripción representativa. Si hay OOM, reinicios
o latencia inaceptable, detener las pruebas de audio y ajustar capacidad/límites de forma explícita;
no aumentar workers en este host por defecto.

En los logs de Java debe observarse que Flyway valida/aplica migraciones y que Spring inicia sin
errores. La interfaz se verifica desde el host con:

```bash
curl --fail http://127.0.0.1:8082/identipat-ia/
```

## Rollback

Restaurar en `.env` los digests anteriores de `IDENTIPAT_IMAGE` y/o `PREPROCESSING_IMAGE`, ejecutar
`pull` y luego `up -d` para los servicios afectados. El caché de modelos puede conservarse entre
versiones; no eliminarlo como mecanismo de rollback.

Las migraciones Flyway son hacia adelante: si una versión Java introduce una migración
incompatible, la reversión de imagen exige el procedimiento de base de datos aprobado y un backup
verificado. Nunca se debe eliminar el volumen PostgreSQL para forzar el rollback.
