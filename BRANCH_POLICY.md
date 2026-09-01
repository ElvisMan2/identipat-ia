# Política de Ramas - Identipat IA

## Objetivo

Mantener un flujo de trabajo claro y seguro para el desarrollo del proyecto, separando la integración del código en ramas de trabajo, integración y liberación.

## Estructura de ramas

### `main`
- Rama estable.
- Solo debe contener código listo para liberar.
- Está protegida.
- No permite commits directos.
- Solo se actualiza mediante Pull Request.

### `develop`
- Rama de integración.
- Recibe el código validado desde las ramas `feature/*`.
- Está protegida.
- No permite commits directos.
- Solo se actualiza mediante Pull Request.

### `feature/*`
- Ramas de trabajo para nuevas funcionalidades.
- Ejemplos:
  - `feature/frontend-login`
  - `feature/backend-clientes`
- Sí permiten commits y push directos.
- Desde aquí se crean Pull Requests hacia `develop`.

### `fix/*`
- Ramas para correcciones menores o bugs.
- Se usan para arreglos puntuales antes de integrar a `develop`.

### `hotfix/*`
- Ramas para correcciones urgentes en producción.
- Se crean desde `main`.
- Luego se integran a `main` y también a `develop`.

## Flujo de trabajo

1. Crear una rama desde `develop`:
   - `feature/*` para nuevas funcionalidades
   - `fix/*` para correcciones
2. Hacer commits normalmente en la rama de trabajo.
3. Hacer push de la rama `feature/*` o `fix/*` al remoto.
4. Abrir un Pull Request hacia `develop`.
5. Revisar y aprobar el PR.
6. Hacer merge a `develop`.
7. Cuando `develop` esté lista para publicación, abrir un PR hacia `main`.
8. Hacer merge a `main` solo cuando la versión esté validada.

## Reglas de protección recomendadas

### Para `main`
- Require a pull request before merging
- Require approvals: 1 o más
- Require status checks before merging
- Require conversation resolution before merging
- Block direct pushes
- Block force pushes

### Para `develop`
- Require a pull request before merging
- Require approvals: 1
- Block direct pushes
- Block force pushes

## Convención recomendada de nombres

- `feature/<descripcion>`
- `fix/<descripcion>`
- `hotfix/<descripcion>`
- `chore/<descripcion>`
- `docs/<descripcion>`

Ejemplos:
- `feature/frontend-client-form`
- `feature/backend-client-api`
- `fix/validacion-moneda`
- `hotfix/error-login`

## Estrategia de merge recomendada

- Usar **Squash merge** para mantener el historial limpio.
- Evitar commits innecesarios en `main` y `develop`.
- Mantener el trabajo diario en ramas `feature/*`.

## Resumen rápido

- `feature/*` → trabajo diario y commits directos
- `develop` → integración mediante PR
- `main` → liberación mediante PR

