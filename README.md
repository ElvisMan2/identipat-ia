# IDENTIPAT-IA

Repositorio del sistema IDENTIPAT-IA, organizado como una aplicación web con backend Java/Spring Boot, frontend Angular y persistencia PostgreSQL.

## Componentes

- `backend/`: API principal, reglas de negocio y seguridad.
- `frontend/`: interfaz web Angular.
- `postman/`: colección de pruebas manuales de la API.
- `docs/`: diagnóstico y documentación técnica.
- `docker-compose.yml`: PostgreSQL 16 y pgAdmin para desarrollo local.
- `codex-prompts/`: instrucciones locales para Codex; su contenido no se versiona.

## Ejecución local

1. Configura las variables requeridas en un archivo `.env` local.
2. Inicia PostgreSQL y pgAdmin con `docker compose up -d` desde la raíz.
3. Ejecuta el backend desde `backend/` con `./mvnw spring-boot:run` en Linux/macOS o `.\mvnw.cmd spring-boot:run` en Windows.
4. Ejecuta el frontend desde `frontend/` con `npm start`.

La API se publica bajo `http://localhost:8082/identipat-ia` y el frontend de desarrollo utiliza su proxy local `/api`.

## Build y pruebas del backend

Windows:

```powershell
cd backend
.\mvnw.cmd clean package
.\mvnw.cmd test
```

Linux/macOS:

```bash
cd backend
./mvnw clean package
./mvnw test
```
