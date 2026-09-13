# Árbol de carpetas actual

**Fecha de actualización:** 12 de septiembre de 2026

```text
identipat-ia/
├── .github/
│   ├── pull_request_template.md
│   └── workflows/
│       └── ci.yml
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
│   │   │   │   │   ├── OpenApiConfig.java
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── SpaCsrfTokenRequestHandler.java
│   │   │   │   │   ├── StandardSessionProperties.java
│   │   │   │   │   └── TimeConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   ├── StandardSessionController.java
│   │   │   │   │   └── UserController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── LoginRequestDTO.java
│   │   │   │   │   ├── LoginResponseDTO.java
│   │   │   │   │   ├── DocumentRecognitionRequest.java
│   │   │   │   │   ├── DocumentRecognitionResponse.java
│   │   │   │   │   ├── StandardUserRegistrationRequest.java
│   │   │   │   │   ├── StandardUserRegistrationResponse.java
│   │   │   │   │   ├── UserDTO.java
│   │   │   │   │   ├── StandardSessionCreateRequest.java
│   │   │   │   │   ├── StandardSessionResponse.java
│   │   │   │   │   ├── ConsentRequest.java
│   │   │   │   │   ├── ConsentResponse.java
│   │   │   │   │   └── CsrfTokenResponse.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── InvalidUserDataException.java
│   │   │   │   │   └── UserNotFoundException.java
│   │   │   │   ├── mapper/
│   │   │   │   │   └── UserMapper.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── StandardSession.java
│   │   │   │   │   ├── ConsentEvent.java
│   │   │   │   │   └── enums de sesión/consentimiento
│   │   │   │   ├── repository/
│   │   │   │   │   ├── UserRepository.java
│   │   │   │   │   ├── StandardSessionRepository.java
│   │   │   │   │   └── ConsentEventRepository.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── JwtService.java
│   │   │   │   │   ├── UserService.java
│   │   │   │   │   └── servicios/resolver de sesión STANDARD
│   │   │   │   └── IdentipatIaApplication.java
│   │   │   └── resources/
│   │   │       ├── db/
│   │   │       │   └── migration/
│   │   │       │       ├── V1__baseline_schema.sql
│   │   │       │       └── V2__standard_sessions_and_consent.sql
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       ├── application-test.yml
│   │   │       └── application-prod.yml
│   │   └── test/
│   │       └── java/com/mnk/identipatia/
│   │           ├── config/
│   │           │   ├── ProductionConfigurationTest.java
│   │           │   └── SecurityConfigTest.java
│   │           ├── controller/
│   │           │   └── UserSecurityWebMvcTest.java
│   │           ├── dto/
│   │           │   └── SensitiveDtoLoggingTest.java
│   │           ├── mapper/
│   │           │   └── UserMapperTest.java
│   │           ├── service/
│   │           │   └── UserServiceTest.java
│   │           ├── IdentipatIaApplicationTests.java
│   │           └── StandardSessionIntegrationTest.java
│   └── pom.xml
├── codex-prompts/                    # Prompts locales; carpeta ignorada por Git
├── docs/
│   ├── arbol-de-carpetas.md
│   ├── architecture/
│   │   └── architecture-overview.md
│   ├── development/
│   │   ├── api-documentation.md
│   │   ├── continuous-integration.md
│   │   ├── configuration.md
│   │   ├── database-migrations.md
│   │   └── preprocessing-service.md
│   ├── design/
│   │   ├── analysis-contract.md       # Contrato conceptual F1.0, aún no implementado
│   │   └── analysis-domain.md         # Dominio y persistencia conceptual F1.0
│   ├── diagnostico-proyecto.md
│   ├── roadmap-funcional.md
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
├── preprocessing-service/
│   ├── src/preprocessing_service/
│   │   ├── api/                      # Ruta técnica /health
│   │   ├── core/                     # Configuración externa mínima
│   │   ├── schemas/                  # Schemas Pydantic
│   │   └── main.py                   # Bootstrap FastAPI
│   ├── tests/
│   │   └── test_health.py
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── pyproject.toml
│   └── README.md
├── .env                              # Variables locales; ignorado por Git
├── .env.example                      # Variables de ejemplo sin secretos reales
├── .gitignore
├── AGENTS.md
├── docker-compose.yml
└── README.md
```

> `.git/` se omite por ser metadato interno. También se omite el contenido de `frontend/node_modules/` y `frontend/.angular/cache/` por ser dependencias o artefactos generados. `docs/` se versiona; `.env`, `codex-prompts/`, `.vscode/`, los directorios `target/` y los artefactos generados de Angular permanecen ignorados.

## Servicio de preprocesamiento

`preprocessing-service/` es un bootstrap FastAPI con el único contrato `GET /health`. Aún no contiene
procesamiento de PDF/audio ni sus dependencias. Java conserva la orquestación y la integración de IA;
Angular nunca llama a Python directamente.
