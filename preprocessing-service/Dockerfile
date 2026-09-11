FROM python:3.12-slim

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY pyproject.toml README.md ./
COPY src ./src
RUN python -m pip install --no-cache-dir .

USER app

EXPOSE 8090

CMD ["uvicorn", "preprocessing_service.main:app", "--host", "0.0.0.0", "--port", "8090"]

