# Integración continua

GitHub Actions valida la base técnica en cada `push` a `develop` o `main`, en cada Pull Request
hacia esas ramas y mediante ejecución manual (`workflow_dispatch`). El workflow no usa filtros de
rutas: ambos componentes se validan juntos para no omitir regresiones en configuración o contratos
compartidos.

La ejecución nueva del mismo Pull Request o rama cancela la anterior mediante `concurrency`.
El workflow usa `contents: read`; el job de imagen solicita además `packages: write`. No despliega
entornos. En eventos `push` publica en GHCR una imagen integrada identificada por SHA; en Pull
Requests construye y prueba la imagen sin publicarla.

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
- **Frontend CI** (`ubuntu-latest`, máximo 20 minutos): usa Node.js 24.11.0, ejecuta `npm ci`, los
  tests ChromeHeadless y el build estático de producción. Verifica que exista `browser/index.html`
  y que no se genere un servidor SSR de runtime.
- **Integrated image** (`ubuntu-latest`, máximo 30 minutos): después de los tres stacks, construye
  el Dockerfile raíz, inicia PostgreSQL efímero y valida raíz, bundle, ruta cliente, 404 de API,
  protección ADMIN y ausencia de Node/Maven en runtime. En `push` publica
  `ghcr.io/<owner>/<repo>:sha-<commit>` y reporta su digest.

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

Para frontend e imagen integrada, desde la raíz:

```bash
cd frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadlessNoGpu
npm run build:prod
cd ..
docker build -t identipat-integrated:local .
bash scripts/smoke-integrated-image.sh identipat-integrated:local
```

## Protección de ramas recomendada

Configurar manualmente GitHub Branch Protection para exigir Pull Request y los checks **Backend
CI**, **Frontend CI**, **Preprocessing CI** e **Integrated image** en `main`. En `develop`, exigirlos
o recomendarlos según la política del equipo. Esta configuración no se gestiona desde el workflow.
