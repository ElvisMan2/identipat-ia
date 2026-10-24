# Servicio de preprocesamiento

## Propósito y frontera

`preprocessing-service/` es un servicio FastAPI técnico y privado que extrae la capa de texto de
PDF y transcribe descripciones de audio localmente. El flujo autorizado es Angular → Java/Spring Boot → Python. Angular nunca llama
Python directamente; Java conserva la orquestación, reglas de negocio, seguridad, persistencia e
integración de IA generativa.

Python no accede a
PostgreSQL, no autentica usuarios, no maneja JWT y no integra LLM, Gemini ni prompts.

## Requisitos y ejecución local

El arranque normal del stack DEV se realiza desde la raíz con:

```powershell
.\scripts\run-backend-dev-ps51.ps1
```

El launcher valida y carga `.env`, comprueba Docker, compila el frontend, levanta PostgreSQL y este
servicio mediante `docker compose up -d --build db preprocessing`, espera el health general y luego
hasta `PREPROCESSING_AUDIO_READY_TIMEOUT_SECONDS` (300 por defecto) a que `GET /v1/audio/health`
confirme el modelo listo, y recién entonces
inicia Spring Boot localmente. No levanta pgAdmin. Java conserva
`PREPROCESSING_SERVICE_BASE_URL=http://localhost:8090` porque se ejecuta fuera de Docker.

Si Python o el modelo no quedan listos, el launcher muestra el estado Compose y logs saneados,
aborta antes de Spring Boot y devuelve un código distinto de cero. No finaliza procesos que ocupen
el puerto 8090; un conflicto de publicación se reporta para que se resuelva explícitamente.

Para desarrollar o depurar Python directamente sin Compose se requiere Python 3.12. Esta modalidad
manual es opcional y no es necesaria para ejecutar el stack normal con el launcher:

```bash
cd preprocessing-service
python -m venv .venv
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
python -m pytest
ruff check .
uvicorn preprocessing_service.main:app --host 127.0.0.1 --port 8090
```

`GET /health` conserva su contrato:

```json
{
  "status": "UP",
  "service": "preprocessing-service",
  "version": "0.1.0"
}
```

No se devuelven hostname, variables, rutas, secretos ni detalles del sistema. No se configura CORS:
el consumidor HTTP es Java, no Angular. En `PREPROCESSING_ENV=prod`, `/docs`, `/redoc` y
`/openapi.json` permanecen deshabilitados. El logging se limita a nivel, tiempo, logger y mensaje;
no registra cuerpos de solicitud, documentos, audio, textos ni headers sensibles.

## Variables Python

| Variable | Default | Uso |
| --- | --- | --- |
| `PREPROCESSING_ENV` | `dev` | Ambiente; `prod` oculta documentación interactiva. |
| `PREPROCESSING_HOST` | `127.0.0.1` | Host recomendado para el arranque local. |
| `PREPROCESSING_PORT` | `8090` | Puerto lógico del servicio. |
| `PREPROCESSING_LOG_LEVEL` | `INFO` | Nivel de logging. |
| `PREPROCESSING_PDF_MAX_FILE_SIZE_BYTES` | `10485760` | Máximo de bytes leídos antes de parsear. |
| `PREPROCESSING_PDF_MAX_PAGES` | `50` | Máximo de páginas extraídas. |
| `PREPROCESSING_AUDIO_MAX_FILE_SIZE_BYTES` | `20971520` | Máximo leído antes de decodificar. |
| `PREPROCESSING_AUDIO_MAX_DURATION_SECONDS` | `180` | Duración decodificada máxima antes de inferencia. |
| `PREPROCESSING_AUDIO_MODEL` | `base` | Modelo multilingüe provisional. |
| `PREPROCESSING_AUDIO_LANGUAGE` | `es` | Idioma fijo; no se acepta desde el request. |
| `PREPROCESSING_AUDIO_DEVICE` | `cpu` | Dispositivo de inferencia. |
| `PREPROCESSING_AUDIO_COMPUTE_TYPE` | `int8` | Tipo de cómputo CTranslate2. |
| `PREPROCESSING_AUDIO_MODEL_CACHE_DIR` | `/models` | Cache persistente del modelo. |
| `PREPROCESSING_HF_HOME` | `/models/.huggingface` | Cache auxiliar descargable dentro del volumen escribible. |

El comando Uvicorn determina host/puerto al iniciar; dentro de Docker se usa `0.0.0.0:8090`.

## Docker

Compose construye el servicio `preprocessing` desde `preprocessing-service/Dockerfile`, publica
`8090:8090` y verifica `/health` con Python estándar (`urllib.request`), sin depender de `curl` ni
`wget`. El contenedor escucha en `0.0.0.0:8090`, se ejecuta como usuario no-root y no depende de
PostgreSQL.

Para construir y ejecutar solamente la imagen, desde `preprocessing-service/`:

```bash
docker build -t identipat-preprocessing:local .
docker run --rm -p 8090:8090 identipat-preprocessing:local
```

La imagen se basa en `python:3.12-slim`, instala solo las dependencias runtime y se ejecuta con un
usuario no-root con escritura en `/models`. El volumen `preprocessing_models` reutiliza el modelo
entre reinicios; `HF_HOME` también queda bajo ese volumen y Compose deshabilita Xet para usar la
descarga HTTP estándar en DEV. PyAV, instalado transitivamente por `faster-whisper`, decodifica sin FFmpeg del
sistema; la imagen tampoco instala Poppler, Tesseract ni OCRmyPDF.

## Extracción PDF

El contrato privado es:

```http
POST /v1/pdf/extract
Content-Type: multipart/form-data
file: <application/pdf>
```

Una extracción correcta devuelve `text`, `pageCount`, `characterCount` y
`extractionMethod: TEXT_LAYER`. El servicio valida MIME, tamaño y firma `%PDF-`, abre con
`pypdf.PdfReader`, rechaza cifrado, cuenta páginas y concatena el texto en orden. Los errores
controlados usan `{code,message}` con `INVALID_PDF`, `PDF_ENCRYPTED`,
`PDF_PAGE_LIMIT_EXCEEDED`, `PDF_TEXT_NOT_EXTRACTABLE`, `PDF_TOO_LARGE` o
`UNSUPPORTED_PDF_MEDIA_TYPE`.

F1.5 no implementa OCR. Un PDF escaneado sin capa de texto suficiente se rechaza; un PDF previamente
OCRizado funciona si `pypdf` puede extraer su capa de texto. El servicio no escribe archivos, no
persiste el binario y no envía contenido a terceros.

La extracción usa `pypdf`; el multipart usa `python-multipart`; y audio usa `faster-whisper`
(CTranslate2 + PyAV). No se agregaron Torch, OpenAI Whisper, servicios STT cloud ni OCR.

## Transcripción de audio

`POST /v1/audio/transcribe` recibe solo el campo multipart `file`. Acepta WebM, MPEG/MP3, MP4/M4A,
3GP, WAV, OGG y FLAC, normalizando parámetros MIME. Los MIME `video/mp4` y `video/3gpp` se admiten
solo como aliases de transporte usados por algunos clientes para audio MP4/M4A/3GP. Antes de
decodificar, PyAV inspecciona el contenedor: debe existir al menos una pista de audio y ninguna pista
de video; por tanto, este endpoint no extrae audio de videos reales. Lee como máximo el límite + 1,
decodifica en memoria a mono 16 kHz mediante `faster_whisper.audio.decode_audio`, calcula duración
desde las muestras y rechaza audio inválido o mayor de 180 segundos antes de inferencia. Luego
materializa todos los segmentos de `faster-whisper` con `task=transcribe`, `language=es` y
`vad_filter=true`.

La respuesta contiene `text`, `durationSeconds`, `characterCount`, `language`,
`transcriptionMethod=FASTER_WHISPER` y `model`. Los errores `{code,message}` son
`AUDIO_TOO_LARGE`, `UNSUPPORTED_AUDIO_MEDIA_TYPE`, `INVALID_AUDIO`,
`AUDIO_DURATION_LIMIT_EXCEEDED`, `AUDIO_TRANSCRIPTION_EMPTY`, `AUDIO_MODEL_NOT_READY` o
`AUDIO_TRANSCRIPTION_ERROR`; no exponen detalles de PyAV, CTranslate2, host o cache.

El modelo `base` multilingüe se carga una vez por proceso en background. `/health` permanece estable
para PDF mientras `GET /v1/audio/health` responde `503 AUDIO_MODEL_NOT_READY`; cuando está listo
responde `{status:"UP",service:"audio-transcription",model:"base"}`. La primera ejecución DEV
requiere acceso al repositorio del modelo. En PROD no se asume Internet en runtime: F2.3 definirá si
el modelo se hornea en la imagen o se provisiona previamente. F2.0 evaluará calidad/modelo.

## Cliente Java

El backend usa `PreprocessingServiceClient`, un cliente interno basado en Spring `RestClient`.
No existe ni se debe agregar un endpoint Java público proxy de health. Su método `health()` consulta
`GET {app.preprocessing.base-url}/health`. `extractPdf(...)` envía el documento como multipart a
`/v1/pdf/extract`, usa un filename interno neutro y convierte los errores controlados en códigos
públicos saneados. Fallos de red, timeout, 5xx o contratos inválidos se convierten en
`PREPROCESSING_SERVICE_UNAVAILABLE`. `transcribeAudio(...)` usa un cliente separado con timeout
largo y mapea red, timeout, 5xx o contrato inválido a `AUDIO_TRANSCRIPTION_UNAVAILABLE`.

`app.preprocessing.base-url` tiene estos valores:

| Perfil | Valor |
| --- | --- |
| DEV | `http://localhost:8090`, reemplazable con `PREPROCESSING_SERVICE_BASE_URL` |
| TEST | `http://preprocessing-service.test`; las pruebas usan HTTP simulado |
| PROD | `PREPROCESSING_SERVICE_BASE_URL` obligatorio, sin fallback |

Los timeouts son `PREPROCESSING_SERVICE_CONNECT_TIMEOUT=5s`, `PREPROCESSING_PDF_TIMEOUT=15s` y
`PREPROCESSING_AUDIO_TIMEOUT=300s`. El backend no llama a `/health` antes de procesar ni realiza reintentos
infinitos. La llamada ocurre sin una transacción PostgreSQL abierta.

## Trabajo futuro

OCR solo se evaluará si la aceptación funcional lo requiere. Audio no incluye streaming, realtime,
diarización, traducción ni STT cloud. Antivirus, DLP, hardening adicional y la política productiva
de retención pertenecen a fases posteriores.
