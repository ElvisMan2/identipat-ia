# Configuración de Gemini / Vertex AI en QA

## Alcance

Esta guía prepara la configuración versionada de QA para ejecutar la app Java con Gemini mediante
Vertex AI y ADC. No despliega ni reinicia servicios, y no autoriza IAM, región, tratamiento de datos
ni retención. El cambio se limita a `deploy/qa/compose.yml`, un override opcional para ADC en archivo
y esta documentación. No modifica el Compose separado de PostgreSQL, Caddy, sus volúmenes, puertos
ni redes.

La ubicación `global` es un default técnico. La institución debe aprobar la región y verificar
disponibilidad, residencia y políticas de datos antes de usar Vertex AI.

## Variables de QA

Compose lee interpolaciones desde el entorno del proceso o desde `--env-file`; declarar una
variable en `.env` solo permite interpolarla. Solo las variables mapeadas en `environment:` se
entregan al contenedor. Por eso, guardar valores Gemini en `.env` no bastaba para pasarlos a Java.

Variables Gemini y PDF de `app`:

| Variable | Default QA | Requisito |
| --- | --- | --- |
| `GOOGLE_CLOUD_PROJECT` | vacío | Obligatorio y no vacío con `IDENTIPAT_AI_PROVIDER=gemini`; no inventar ni confirmar el ID del proyecto. |
| `GOOGLE_CLOUD_LOCATION` | `global` | Default técnico sujeto a aprobación institucional. |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Modelo objetivo de QA. |
| `GEMINI_API_VERSION` | `v1` | Versión admitida por el adapter. |
| `GEMINI_TIMEOUT` | `60s` | Debe ser menor que el lease. |
| `ANALYSIS_LEASE_DURATION` | `120s` | Debe ser estrictamente mayor que el timeout efectivo de Gemini. Si se cambia uno, validar ambos. |
| `ANALYSIS_PDF_RETENTION` | `7d` | Retención temporal de QA, no política definitiva. |
| `ANALYSIS_PDF_CLEANUP_INTERVAL` | `1h` | Intervalo de limpieza en QA. |

Se mantienen las variables de proveedor OpenAI, AI, PostgreSQL, idempotencia y preprocesamiento. El
Compose base no establece `GOOGLE_APPLICATION_CREDENTIALS` ni monta credenciales: puede resolver el
modo OpenAI sin proyecto GCP ni archivo ADC. No cambiar `IDENTIPAT_IMAGE` ni
`PREPROCESSING_IMAGE`: ambos deben seguir apuntando a sus digests inmutables GHCR existentes, nunca
a `latest`.

Ejemplo de valores no secretos para `.env` en el host QA (completar según la autorización interna;
no copiar este ejemplo al repositorio):

```dotenv
IDENTIPAT_AI_ENABLED=true
IDENTIPAT_AI_PROVIDER=gemini
GOOGLE_CLOUD_PROJECT=<PROJECT_ID_AUTORIZADO>
GOOGLE_CLOUD_LOCATION=global
GEMINI_MODEL=gemini-3.8-flash
GEMINI_API_VERSION=v1
GEMINI_TIMEOUT=60s
ANALYSIS_LEASE_DURATION=120s
ANALYSIS_PDF_RETENTION=7d
ANALYSIS_PDF_CLEANUP_INTERVAL=1h
VERTEX_CREDENTIALS_HOST_PATH=<RUTA_ABSOLUTA_PRIVADA_AL_ARCHIVO_ADC>
```

`.env` debe permanecer fuera de Git y sus permisos deben restringirse a los operadores autorizados.
No pegar su contenido ni el JSON ADC en tickets, logs o reportes.

## ADC para una cuenta autorizada

El override `deploy/qa/compose.gemini.yml` está pensado únicamente para un archivo ADC autorizado.
En Ubuntu, guárdese bajo una ruta absoluta fuera del checkout, protegida por cifrado de disco y
controles de acceso. El archivo debe preexistir, ser un archivo regular y ser legible por el usuario
del contenedor, UID `10001`. El montaje en `/run/secrets/vertex-qa.json` es de solo lectura. Aplicar
permisos restrictivos compatibles con el propietario/grupo del host y UID 10001; nunca resolver un
problema de lectura con `chmod 644` o `chmod 777`. La cuenta y sus permisos IAM deben seguir mínimo
privilegio, con autorización para el proyecto y Vertex AI correspondiente.

Antes de iniciar contenedores, el operador debe verificar localmente que la ruta configurada sea
absoluta y canónica, que exista y sea archivo regular, que los permisos no sean amplios, y que UID
10001 pueda leerla. Este preflight no debe imprimir el contenido del archivo ni tokens:

```bash
credential_path="$(realpath -e -- "$VERTEX_CREDENTIALS_HOST_PATH")"
test -f "$credential_path" && test -r "$credential_path"
stat -c 'type=%F mode=%a owner=%u group=%g' -- "$credential_path"
docker run --rm --user 10001:10001 \
  --mount "type=bind,src=$credential_path,dst=/run/secrets/vertex-qa.json,readonly" \
  --entrypoint sh "$IDENTIPAT_IMAGE" -c 'test -f /run/secrets/vertex-qa.json && test -r /run/secrets/vertex-qa.json'
```

La última comprobación solo prueba lectura dentro del contenedor; no inicia la app ni llama a
Vertex AI. No publicar el valor de la ruta si es sensible. `bind.create_host_path: false` evita crear
un directorio en versiones compatibles de Compose, pero no reemplaza este preflight: un archivo
ausente, un directorio o permisos insuficientes puede hacer fallar el montaje o el arranque.

Si el host usa identidad administrada o Workload Identity Federation para ADC sin JSON, se usa el
Compose base sin override únicamente después de demostrar que el SDK Google Gen AI obtiene y usa
esa identidad desde el contenedor. La identidad debe estar disponible al proceso UID 10001; no se
debe asumir que la identidad del host se propaga automáticamente.

La rotación requiere instalar un nuevo archivo autorizado con propietario y permisos adecuados,
actualizar de forma controlada la ruta si cambia, validar el preflight y reiniciar la app durante
una ventana autorizada. Revocar la credencial anterior conforme al proceso IAM. No versionar,
adjuntar ni imprimir el JSON, access tokens o salida completa de `docker compose config`.

## Verificación y despliegue futuro

Todos los comandos de Compose deben conservar ambos archivos y el mismo `--env-file` cuando se
utilice ADC por archivo. `config --quiet` verifica la resolución sin imprimir el modelo resultante:

```bash
docker compose --env-file .env -f compose.yml -f compose.gemini.yml config --quiet
```

El operador debe revisar de forma protegida que proyecto y región estén autorizados; Vertex AI esté
habilitado; IAM aplique mínimo privilegio; exista salida HTTPS; haya cuota y presupuesto; y la
política institucional permita enviar los datos. Verificar especialmente el costo y la residencia
de datos. No tratar `global` como región aprobada.

El despliegue futuro sigue este orden, fuera del alcance de esta tarea:

1. Respaldar la base conforme al procedimiento aprobado y capturar los digests actuales de app y
   preprocessing.
2. Completar preflight de ADC y Compose, incluida la verificación de configuración sin exponerla.
3. Actualizar `preprocessing` antes de Java porque la versión nueva expone `/v1/pdf/inspect`.
4. Iniciar Java y confirmar Flyway V15 mediante observabilidad autorizada.
5. Probar con un PDF sintético autorizado, sin datos personales.
6. Confirmar estado `COMPLETED` y verificar internamente Gemini, prompt `0.3.2`, schema `3.0`,
   tokens y latencia. No exponer metadata técnica de invocación a usuarios STANDARD.

En un cambio aprobado, repetir los mismos archivos en cada operación (los ejemplos se muestran
solo como referencia; esta tarea no los ejecuta):

```bash
docker compose --env-file .env -f compose.yml -f compose.gemini.yml pull preprocessing app
docker compose --env-file .env -f compose.yml -f compose.gemini.yml up -d preprocessing
docker compose --env-file .env -f compose.yml -f compose.gemini.yml up -d app
docker compose --env-file .env -f compose.yml -f compose.gemini.yml ps
docker compose --env-file .env -f compose.yml -f compose.gemini.yml logs --tail=100 app preprocessing
```

No incluir salidas de logs que puedan contener datos; la aplicación debe seguir excluyendo inputs,
prompts, respuestas y resultados completos de los logs. Para el modo OpenAI, utilizar únicamente el
Compose base y `IDENTIPAT_AI_PROVIDER=openai`.

## Retención PDF y rollback

Los PDF se almacenan como `BYTEA` en PostgreSQL. `7d` es solo un default de QA; quedan pendientes
la aprobación de privacidad, cifrado en reposo, alcance de backups, retención de backups y el
proceso de eliminación. La tarea no cambia la base ni sus copias.

El rollback futuro restaura los digests de imágenes capturados y la versión anterior de la
configuración, retirando el override solo si corresponde al modo de identidad elegido. Preservar
V15 y seguir el procedimiento DBA aprobado. No ejecutar `down -v`, `flyway clean`, `DROP TABLE` ni
una restauración improvisada. No revertir manualmente migraciones ni editar
`flyway_schema_history`.
