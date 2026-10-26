# Árbol de carpetas actual

**Fecha de actualización:** 14 de septiembre de 2026

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
│   │   │   │   ├── analysis/
│   │   │   │   │   ├── config/      # Límites, poller y executor acotado
│   │   │   │   │   ├── controller/  # POST texto y GET estado/resultado
│   │   │   │   │   ├── dto/         # Contratos HTTP estables
│   │   │   │   │   ├── model/       # Analysis/Input/Invocation/resultado persistido
│   │   │   │   │   ├── repository/  # Persistencia del aggregate
│   │   │   │   │   ├── result/      # AnalysisResult 1.0/2.0, lector versionado y enums
│   │   │   │   │   ├── service/     # Creación, consulta y normalización
│   │   │   │   │   └── worker/      # Claim/lease, intentos, retry y recovery
│   │   │   │   ├── ai/
│   │   │   │   │   ├── GenerativeAiProvider.java
│   │   │   │   │   ├── config/      # Activación y selección de provider
│   │   │   │   │   ├── exception/   # Errores agnósticos y saneados
│   │   │   │   │   ├── model/       # Request, response, prompt, schema, usage
│   │   │   │   │   ├── prompt/      # Registry, renderer y hashes
│   │   │   │   │   ├── schema/      # Registry y validación JSON Schema
│   │   │   │   │   └── provider/openai/ # Único boundary del SDK OpenAI
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
│   │   │       │       ├── V2__standard_sessions_and_consent.sql
│   │   │       │       └── V3__analysis_domain.sql
│   │   │       ├── prompts/intellectual-property-analysis/v0.1/
│   │   │       │   ├── system.md
│   │   │       │   └── user.md
│   │   │       ├── prompts/intellectual-property-analysis/v0.2/
│   │   │       │   ├── system.md
│   │   │       │   └── user.md
│   │   │       ├── prompts/provider-smoke-test/v1.0/
│   │   │       │   ├── system.md
│   │   │       │   └── user.md
│   │   │       ├── ai-schemas/provider-smoke-result/v1.0/schema.json
│   │   │       ├── ai-schemas/analysis-result/v1.0/schema.json
│   │   │       ├── ai-schemas/analysis-result/v2.0/schema.json
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       ├── application-test.yml
│   │   │       └── application-prod.yml
│   │   └── test/
│   │       └── java/com/mnk/identipatia/
│   │           ├── config/
│   │           │   ├── ProductionConfigurationTest.java
│   │           │   └── SecurityConfigTest.java
│   │           ├── ai/               # Contratos, prompts, schemas, config y adapter OpenAI
│   │           ├── analysis/         # E2E, contratos, claim, retry, recovery y rechazo local
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
│   │   ├── generative-ai-integration.md
│   │   ├── text-analysis-flow.md
│   │   └── preprocessing-service.md
│   ├── design/
│   │   ├── analysis-contract.md       # Contratos canónicos versionados
│   │   ├── analysis-domain.md         # Dominio y persistencia de análisis
│   │   └── reporting-design.md        # Reporte F1.7 y disclaimer histórico
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
│   │   ├── api/                      # /health, PDF y audio privado
│   │   ├── core/                     # Configuración externa PDF/audio
│   │   ├── schemas/                  # Schemas Pydantic health/PDF/audio
│   │   ├── services/                 # pypdf y faster-whisper local
│   │   └── main.py                   # Bootstrap FastAPI
│   ├── tests/
│   │   ├── test_audio.py
│   │   ├── test_health.py
│   │   └── test_pdf.py
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

`preprocessing-service/` expone `GET /health`, `GET /v1/audio/health`, `POST /v1/pdf/extract` y
`POST /v1/audio/transcribe`. Extrae PDF con `pypdf` y transcribe audio localmente con
`faster-whisper`; no implementa OCR, negocio, persistencia ni IA generativa. Java
conserva la orquestación y la integración generativa; Angular nunca llama a Python directamente.
