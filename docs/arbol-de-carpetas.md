# Árbol de carpetas actual

**Fecha de generación:** 10 de septiembre de 2026

```text
identipat-ia/
├── .github/
│   └── pull_request_template.md
├── .vscode/
│   └── settings.json
├── backend/
│   ├── .mvn/                         # Soporte de Maven Wrapper
│   ├── mvnw                          # Maven Wrapper para Linux/macOS
│   ├── mvnw.cmd                      # Maven Wrapper para Windows
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mnk/identipatia/
│   │   │   │   ├── advice/
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── JacksonConfig.java
│   │   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   │   └── SecurityConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   └── UserController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── LoginRequestDTO.java
│   │   │   │   │   ├── LoginResponseDTO.java
│   │   │   │   │   ├── DocumentRecognitionRequest.java
│   │   │   │   │   ├── DocumentRecognitionResponse.java
│   │   │   │   │   ├── StandardUserRegistrationRequest.java
│   │   │   │   │   ├── StandardUserRegistrationResponse.java
│   │   │   │   │   └── UserDTO.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── InvalidUserDataException.java
│   │   │   │   │   └── UserNotFoundException.java
│   │   │   │   ├── mapper/
│   │   │   │   │   └── UserMapper.java
│   │   │   │   ├── model/
│   │   │   │   │   └── User.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── UserRepository.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── JwtService.java
│   │   │   │   │   └── UserService.java
│   │   │   │   └── IdentipatIaApplication.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/
│   │       └── java/com/mnk/identipatia/
│   │           ├── config/
│   │           │   └── SecurityConfigTest.java
│   │           ├── controller/
│   │           │   └── UserSecurityWebMvcTest.java
│   │           ├── mapper/
│   │           │   └── UserMapperTest.java
│   │           ├── service/
│   │           │   └── UserServiceTest.java
│   │           └── IdentipatIaApplicationTests.java
│   └── pom.xml
├── codex-prompts/                    # Prompts locales; carpeta ignorada por Git
├── docs/
│   ├── arbol-de-carpetas.md
│   ├── architecture/
│   │   └── architecture-overview.md
│   ├── diagnostico-proyecto.md
│   └── security/
│       └── authorization-matrix.md
├── frontend/
│   ├── .angular/
│   │   └── cache/                    # Caché generada por Angular
│   ├── node_modules/                 # Dependencias instaladas; contenido omitido
│   ├── public/
│   │   └── favicon.ico
│   ├── src/
│   │   ├── app/
│   │   │   ├── models/
│   │   │   │   └── user.model.ts
│   │   │   ├── services/
│   │   │   │   └── user.service.ts
│   │   │   ├── app.config.server.ts
│   │   │   ├── app.config.ts
│   │   │   ├── app.css
│   │   │   ├── app.html
│   │   │   ├── app.routes.server.ts
│   │   │   ├── app.routes.ts
│   │   │   ├── app.spec.ts
│   │   │   └── app.ts
│   │   ├── environments/
│   │   │   ├── environment.prod.ts
│   │   │   └── environment.ts
│   │   ├── index.html
│   │   ├── main.server.ts
│   │   ├── main.ts
│   │   ├── server.ts
│   │   └── styles.css
│   ├── .editorconfig
│   ├── .gitignore
│   ├── angular.json
│   ├── package-lock.json
│   ├── package.json
│   ├── proxy.conf.json
│   ├── README.md
│   ├── tsconfig.app.json
│   ├── tsconfig.json
│   └── tsconfig.spec.json
├── postman/
│   └── identipat-api.collection.json
├── .env                              # Variables locales; ignorado por Git
├── .gitignore
├── AGENTS.md
├── docker-compose.yml
└── README.md
```

> `.git/` se omite por ser metadato interno. También se omite el contenido de `frontend/node_modules/` y `frontend/.angular/cache/` por ser dependencias o artefactos generados. `docs/` se versiona; `.env`, `codex-prompts/`, `.vscode/`, los directorios `target/` y los artefactos generados de Angular permanecen ignorados.

## Estructura objetivo futura

La estructura actual todavía no incluye un servicio Python. Cuando una fase posterior lo incorpore, su denominación objetivo será `preprocessing-service/`; no debe interpretarse como una carpeta ya creada. Será un servicio especializado para preprocesamiento técnico de PDF y audio, mientras que Java conservará la integración e inferencia con IA generativa.
