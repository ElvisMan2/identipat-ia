# IDENTIPAT-IA

## Repository architecture

- `frontend/`: Angular application.
- `backend/`: Java/Spring Boot main backend.
- `ai-service/`: future Python AI/NLP service.
- `docs/`: project and architecture documentation.
- `postman/`: API testing collections.

## Responsibilities

- Angular owns presentation and user interaction.
- Java owns application workflow, business rules, security and persistence.
- Python will own specialized AI/NLP processing when that service is introduced.
- PostgreSQL is the system of record.
- Angular must communicate with Java, not directly with Python or AI providers.

## Development rules

- Do not introduce unrelated refactors when implementing a scoped task.
- Preserve existing behavior unless the task explicitly requires changing it.
- Add or update tests when behavior changes.
- Never commit secrets or local environment files.
