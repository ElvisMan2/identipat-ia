# Build integrado Angular + Spring Boot

La imagen de ejecución se construye desde el `Dockerfile` raíz y contiene un único proceso Java
21. Node.js y Maven existen solo en etapas de build. PostgreSQL 16 y `preprocessing-service`
permanecen separados.

## Flujo reproducible

1. `npm ci` instala exactamente `frontend/package-lock.json`.
2. `npm run build:prod` genera un sitio estático en `frontend/dist/identipat-ia/browser/` con
   `baseHref=/identipat-ia/`. Angular 20.3 admite `outputMode: static`; el inlining remoto de Google
   Fonts está desactivado para que compilar no dependa de ese endpoint.
3. Solo el contenido de `browser/` se copia a `backend/src/main/resources/static/` dentro de la
   etapa de build.
4. Maven Wrapper ejecuta `./mvnw -B -DskipTests package`. El build falla si `index.html` o bundles
   JS/CSS no aparecen bajo `BOOT-INF/classes/static/`.
5. La etapa final usa JRE 21, usuario UID/GID 10001 y no incluye Node.js ni Maven.

Build local:

```bash
docker build -t identipat-integrated:local .
bash scripts/smoke-integrated-image.sh identipat-integrated:local
```

El controlador SPA sirve `index.html` solo para `/` y `/terminos-y-privacidad`, las dos rutas
propiedad del frontend actual. No existe fallback global: rutas desconocidas de API no reciben
HTML. Los controles JWT ADMIN y cookie/CSRF STANDARD no se modifican.

## Release y trazabilidad

GitHub Actions valida frontend, backend y Python; construye la imagen integrada y ejecuta el smoke
test con PostgreSQL efímero. En eventos `push` publica en GHCR el tag inmutable por convención
`sha-<commit completo>` y escribe el digest en el resumen del job. El digest es la fuente de verdad
para QA/PREPROD; el tag sirve para localizarlo, no para fijar el despliegue.

El mismo evento publica de forma independiente
`ghcr.io/elvisman2/identipat-preprocessing:sha-<commit completo>` después de pytest, Ruff, build y
healthcheck. Esa imagen reutiliza `preprocessing-service/Dockerfile`; nunca se copia Python ni el
modelo Whisper dentro del contenedor Spring Boot. QA/PREPROD fijan ambas imágenes por sus digests
SHA-256.

Si GHCR es privado, el host necesita autenticación con `read:packages`. El workflow usa
`GITHUB_TOKEN` y requiere permiso `packages: write`; la configuración de la organización debe
permitir que Actions cree/escriba el paquete.

El procedimiento operativo, límites y rollback por digest están en `deploy/qa/README.md`.
