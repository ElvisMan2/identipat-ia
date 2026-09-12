# Integración continua

GitHub Actions valida la base técnica en cada `push` a `develop` o `main`, en cada Pull Request
hacia esas ramas y mediante ejecución manual (`workflow_dispatch`). El workflow no usa filtros de
rutas: ambos componentes se validan juntos para no omitir regresiones en configuración o contratos
compartidos.

La ejecución nueva del mismo Pull Request o rama cancela la anterior mediante `concurrency`.
El workflow usa solo el permiso `contents: read`; no requiere secretos, no publica artefactos ni
imágenes y no realiza despliegues.

## Checks

- **Backend CI** (`ubuntu-latest`, máximo 20 minutos): configura Temurin JDK 21, restaura el caché
  Maven, ejecuta `./mvnw clean test` y `./mvnw clean package -DskipTests` desde `backend/`.
  Los tests levantan PostgreSQL efímero con Testcontainers; no se configura un servicio PostgreSQL
  manual. En ese flujo Flyway aplica las migraciones y Hibernate valida el esquema.
- **Preprocessing CI** (`ubuntu-latest`, máximo 15 minutos): configura Python 3.12, restaura el
  caché de pip basado en `preprocessing-service/pyproject.toml`, instala
  `python -m pip install -e ".[dev]"`, ejecuta `python -m pytest` y
  `python -m ruff check .`, construye la imagen Docker y verifica `GET /health` del contenedor.
  El contenedor temporal se elimina incluso si la comprobación falla.

Un check verde confirma que esas validaciones terminaron correctamente. Uno rojo bloquea la
validación técnica de ese componente y debe revisarse antes de integrar cambios. CI valida; no
despliega a DEV, QA ni producción.

## Reproducción local

En Windows, para el backend:

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd clean package -DskipTests
```

Para el servicio Python con Python 3.12 y Docker disponibles:

```bash
cd preprocessing-service
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
python -m pytest
python -m ruff check .
docker build -t identipat-preprocessing:local .
```

## Protección de ramas recomendada

Configurar manualmente GitHub Branch Protection para exigir Pull Request y el check **Backend CI**
y **Preprocessing CI** en `main`. En `develop`, exigir o al menos recomendar ambos checks según la
política del equipo. Esta configuración no se gestiona desde el workflow.
