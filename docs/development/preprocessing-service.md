# Servicio de preprocesamiento

## Propósito y frontera

`preprocessing-service/` es un servicio FastAPI técnico y privado para el futuro preprocesamiento de
entradas PDF y audio. El flujo autorizado es Angular → Java/Spring Boot → Python. Angular nunca llama
Python directamente; Java conserva la orquestación, reglas de negocio, seguridad, persistencia e
integración de IA generativa.

El bootstrap actual no procesa PDF ni audio y no selecciona librerías para ello. Python no accede a
PostgreSQL, no autentica usuarios, no maneja JWT y no integra LLM, Gemini ni prompts.

## Requisitos y ejecución local

Se requiere Python 3.12.

```bash
cd preprocessing-service
python -m venv .venv
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
python -m pytest
ruff check .
uvicorn preprocessing_service.main:app --host 127.0.0.1 --port 8090
```

El único endpoint actual es `GET /health`:

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

El comando Uvicorn determina host/puerto al iniciar; dentro de Docker se usa `0.0.0.0:8090`.

## Docker

Desde `preprocessing-service/`:

```bash
docker build -t identipat-preprocessing:local .
docker run --rm -p 8090:8090 identipat-preprocessing:local
```

La imagen se basa en `python:3.12-slim`, instala solo las dependencias runtime y se ejecuta con un
usuario no-root. No instala ffmpeg, poppler, tesseract ni paquetes de PDF/audio.

## Cliente Java

El backend usa `PreprocessingServiceClient`, un cliente interno basado en Spring `RestClient`.
No existe ni se debe agregar un endpoint Java público proxy de health. Su método `health()` consulta
`GET {app.preprocessing.base-url}/health`, deserializa `PreprocessingHealthResponse` y transforma
fallos de conexión, respuestas 5xx o contratos inválidos en una excepción controlada.

`app.preprocessing.base-url` tiene estos valores:

| Perfil | Valor |
| --- | --- |
| DEV | `http://localhost:8090`, reemplazable con `PREPROCESSING_SERVICE_BASE_URL` |
| TEST | `http://preprocessing-service.test`; las pruebas usan HTTP simulado |
| PROD | `PREPROCESSING_SERVICE_BASE_URL` obligatorio, sin fallback |

El timeout de conexión y lectura es de cinco segundos. El backend no llama automáticamente a
`/health` durante el startup ni realiza reintentos infinitos.

## Trabajo futuro

La definición de contratos y la implementación real de PDF/audio se harán cuando se diseñe esa fase.
No se deben añadir endpoints ficticios mientras tanto.
