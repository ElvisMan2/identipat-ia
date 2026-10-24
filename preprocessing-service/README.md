# preprocessing-service

Servicio técnico interno de IDENTIPAT-IA para extracción de texto desde PDF; audio queda pendiente.
No implementa reglas de negocio, autenticación, PostgreSQL ni integración LLM; Angular se comunica
exclusivamente con el backend Java.

## Requisitos y ejecución local

Se requiere Python 3.12.

```bash
python -m venv .venv
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
uvicorn preprocessing_service.main:app --host 127.0.0.1 --port 8090
```

`GET /health` devuelve el estado estable. `POST /v1/pdf/extract` recibe multipart `file`, valida
`application/pdf`, firma, 10 MiB y 50 páginas por defecto, y extrae la capa de texto con `pypdf`.
No incluye OCR, persistencia, autenticación ni llamadas externas. Los límites se sobrescriben con
`PREPROCESSING_PDF_MAX_FILE_SIZE_BYTES` y `PREPROCESSING_PDF_MAX_PAGES`. Consulta la guía del
repositorio en `../docs/development/preprocessing-service.md` para contratos, pruebas y Docker.

