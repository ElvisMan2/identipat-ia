# Configuración de .gitignore

Este proyecto utiliza múltiples archivos `.gitignore` organizados de la siguiente manera para mantener una configuración limpia y modular:

## Estructura

```
identipat-ia/
├── .gitignore           # Configuración general (IDE, OS, logs, etc)
├── backend/
│   └── .gitignore      # Configuración específica para Maven/Spring Boot
└── frontend/
    └── .gitignore      # Configuración específica para Angular/Node.js
```

## .gitignore Raíz

Contiene configuraciones generales que aplican a todo el proyecto:
- **IDE**: `.idea/`, `.vscode/`, etc.
- **OS**: `.DS_Store`, `Thumbs.db`
- **Logs**: `*.log`, `logs/`
- **Archivos temporales**: `*.tmp`, `*.bak`, `*.swp`
- **Variables de entorno**: `.env`, `.env.local`

## .gitignore Backend

Configuraciones específicas para el proyecto Maven/Spring Boot:
- **Artefactos Maven**: `target/`, `*.jar`, `*.war`
- **Configuración Maven**: `pom.xml.tag`, `release.properties`, etc.
- **Spring Boot**: `spring-boot-devtools.properties`
- **JaCoCo**: `jacoco.exec`

## .gitignore Frontend

Configuraciones específicas para Angular/Node.js:
- **Node.js**: `/node_modules`, `npm-debug.log`, `yarn-debug.log`
- **Angular**: `/.angular/cache`, `/dist`, `/tmp`, `/out-tsc`
- **Coverage**: `/coverage`
- **Otros**: `.sass-cache/`, `testem.log`, `/typings`

## Por qué esta estructura

Como backend y frontend son proyectos **completamente independientes**, cada uno tiene su propio `.gitignore` con las entradas específicas necesarias. Esto:

✅ Mantiene la configuración limpia y modular
✅ Evita entradas redundantes o innecesarias
✅ Permite trabajar en cada proyecto sin afectar al otro
✅ Facilita la reutilización si los proyectos se separan en repositorios diferentes

## Consideración importante

Cuando se hace git add/commit en la raíz del proyecto, Git verifica los `.gitignore` de cada nivel:
1. Primero revisa `.gitignore` raíz
2. Luego revisa `.gitignore` del backend
3. Luego revisa `.gitignore` del frontend

Esto asegura que todos los archivos innecesarios sean ignorados correctamente.

