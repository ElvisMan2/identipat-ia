# Guía de Desarrollo - Identipat IA

Este proyecto está compuesto por dos módulos **independientes** que se desarrollan por separado:

## Backend (Spring Boot)

### Compilación y ejecución

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

El backend estará disponible en: `http://localhost:8080`

### Pruebas

```bash
cd backend
mvn test
```

### Generar JAR ejecutable

```bash
cd backend
mvn clean package
# El JAR se genera en: backend/target/backend-0.0.1-SNAPSHOT.jar
```

---

## Frontend (Angular)

### Instalación de dependencias

```bash
cd frontend
npm install
```

### Desarrollo

```bash
cd frontend
npm start
# o
ng serve
```

El frontend estará disponible en: `http://localhost:4200`

### Compilación para producción

```bash
cd frontend
npm run build:prod
# o
ng build --configuration production
```

Los archivos compilados estarán en: `frontend/dist/`

### Pruebas

```bash
cd frontend
npm test
```

---

## Notas importantes

- ⚠️ **Backend y Frontend son proyectos independientes**
- No hay dependencia Maven entre ellos
- El backend usa Maven y se compila con Java
- El frontend usa npm/Angular CLI y se compila con Node.js
- Cada uno tiene su propio ciclo de vida de build
- El backend expone APIs REST que el frontend consume

## Flujo de ramas

Para trabajar en el proyecto usa la política definida en `BRANCH_POLICY.md`:

- `feature/*` para desarrollo diario
- `develop` para integración por Pull Request
- `main` para liberaciones estables

---

## Arquitectura

```
identipat-ia/
├── backend/          # Aplicación Spring Boot (Java)
│   ├── pom.xml      # Configuración Maven
│   ├── src/
│   │   ├── main/    # Código fuente Java
│   │   └── test/    # Tests unitarios
│   └── target/      # Artefactos compilados
│
├── frontend/         # Aplicación Angular (TypeScript)
│   ├── package.json  # Configuración npm
│   ├── src/          # Código fuente Angular
│   └── dist/         # Archivos compilados
│
└── pom.xml          # POM raíz (solo para backend)
```

