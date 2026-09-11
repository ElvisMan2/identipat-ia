# preprocessing-service

Servicio técnico interno de IDENTIPAT-IA para futuros preprocesamientos de PDF y audio.
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

El endpoint `GET /health` devuelve el estado estable del servicio. Consulta la guía del repositorio
en `../docs/development/preprocessing-service.md` para configuración, pruebas y Docker.

