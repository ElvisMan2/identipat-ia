from fastapi.testclient import TestClient

from preprocessing_service.core.config import Settings
from preprocessing_service.main import app, create_app


def test_app_imports_correctly() -> None:
    assert app is not None


def test_health_returns_stable_non_sensitive_response() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "preprocessing-service",
        "version": "0.1.0",
    }
    assert not {"hostname", "environment", "path", "secret"}.intersection(response.json())


def test_default_configuration_loads() -> None:
    settings = Settings()

    assert settings.environment == "dev"
    assert settings.host == "127.0.0.1"
    assert settings.port == 8090
    assert settings.log_level == "INFO"


def test_production_hides_interactive_documentation() -> None:
    production_app = create_app(Settings(environment="prod"))
    client = TestClient(production_app)

    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
