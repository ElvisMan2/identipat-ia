# Servicio de preprocesamiento

## Propósito y frontera

`preprocessing-service/` es un servicio FastAPI técnico y privado que extrae la capa de texto de
documentos PDF; el audio permanece pendiente. El flujo autorizado es Angular → Java/Spring Boot → Python. Angular nunca llama
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
servicio mediante `docker compose up -d --build db preprocessing`, espera hasta 60 segundos a que
`GET http://localhost:8090/health` confirme `status=UP` y `service=preprocessing-service`, y luego
inicia Spring Boot localmente. No levanta pgAdmin. Java conserva
`PREPROCESSING_SERVICE_BASE_URL=http://localhost:8090` porque se ejecuta fuera de Docker.

Si Python no queda listo, el launcher muestra el estado Compose y los últimos logs del servicio,
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
usuario no-root. `pypdf` es Python puro y no requiere paquetes del sistema. La imagen no instala
ffmpeg, Poppler, Tesseract ni OCRmyPDF.

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

Las dependencias nuevas son `pypdf` (BSD-3-Clause, extracción Python pura) y `python-multipart`
(parseo del campo multipart requerido por FastAPI). No se agregaron librerías de IA ni OCR.

## Cliente Java

El backend usa `PreprocessingServiceClient`, un cliente interno basado en Spring `RestClient`.
No existe ni se debe agregar un endpoint Java público proxy de health. Su método `health()` consulta
`GET {app.preprocessing.base-url}/health`. `extractPdf(...)` envía el documento como multipart a
`/v1/pdf/extract`, usa un filename interno neutro y convierte los errores controlados en códigos
públicos saneados. Fallos de red, timeout, 5xx o contratos inválidos se convierten en
`PREPROCESSING_SERVICE_UNAVAILABLE`.

`app.preprocessing.base-url` tiene estos valores:

| Perfil | Valor |
| --- | --- |
| DEV | `http://localhost:8090`, reemplazable con `PREPROCESSING_SERVICE_BASE_URL` |
| TEST | `http://preprocessing-service.test`; las pruebas usan HTTP simulado |
| PROD | `PREPROCESSING_SERVICE_BASE_URL` obligatorio, sin fallback |

El timeout de conexión y lectura es configurable con `PREPROCESSING_SERVICE_TIMEOUT` y vale 15
segundos por defecto. El backend no llama a `/health` antes de extraer ni realiza reintentos
infinitos. La llamada ocurre sin una transacción PostgreSQL abierta.

## Trabajo futuro

OCR solo se evaluará si la aceptación funcional lo requiere. Audio se define en F1.6; antivirus,
DLP, hardening adicional y la política productiva de retención pertenecen a fases posteriores.
