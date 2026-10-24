from __future__ import annotations

from threading import Event
from typing import Any

import pytest
from fastapi.testclient import TestClient

from preprocessing_service.core.config import Settings
from preprocessing_service.main import create_app
from preprocessing_service.services.audio_transcriber import (
    AudioProcessingError,
    AudioTranscriptionService,
    DecodedAudio,
)


class FakeTranscriber:
    def __init__(self, text: str = "Descripción hablada suficientemente extensa") -> None:
        self.text = text
        self.calls = 0

    def transcribe(self, samples: Any) -> str:
        self.calls += 1
        return self.text


def _ready_service(
    *,
    settings: Settings | None = None,
    duration: float = 1.25,
    text: str = "Descripción hablada suficientemente extensa",
) -> tuple[AudioTranscriptionService, FakeTranscriber]:
    configured = settings or Settings()
    transcriber = FakeTranscriber(text)
    service = AudioTranscriptionService(
        configured,
        decoder=lambda _: DecodedAudio(samples=[0.0], duration_seconds=duration),
        transcriber_factory=lambda _: transcriber,
    )
    service.start_warmup()
    service.wait_for_warmup()
    return service, transcriber


def _client(
    *,
    settings: Settings | None = None,
    duration: float = 1.25,
    text: str = "Descripción hablada suficientemente extensa",
) -> tuple[TestClient, AudioTranscriptionService, FakeTranscriber]:
    configured = settings or Settings()
    service, transcriber = _ready_service(settings=configured, duration=duration, text=text)
    return TestClient(create_app(configured, service)), service, transcriber


@pytest.mark.parametrize(
    "media_type",
    [
        "audio/webm",
        "audio/mpeg",
        "audio/mp4",
        "video/mp4",
        "audio/x-m4a",
        "audio/3gpp",
        "video/3gpp",
        "audio/wav",
        "audio/x-wav",
        "audio/ogg",
        "audio/flac",
        "audio/webm;codecs=opus",
        "video/mp4;codecs=mp4a.40.2",
    ],
)
def test_transcribes_supported_audio_and_returns_metadata(media_type: str) -> None:
    client, _, _ = _client()

    response = client.post(
        "/v1/audio/transcribe",
        files={"file": ("ignored-sensitive-name.bin", b"fake-audio", media_type)},
    )

    assert response.status_code == 200
    assert response.json() == {
        "text": "Descripción hablada suficientemente extensa",
        "durationSeconds": 1.25,
        "characterCount": 43,
        "language": "es",
        "transcriptionMethod": "FASTER_WHISPER",
        "model": "base",
    }


def test_rejects_missing_empty_unsupported_and_oversized_audio() -> None:
    settings = Settings(audio_max_file_size_bytes=4)
    client, _, transcriber = _client(settings=settings)

    missing = client.post("/v1/audio/transcribe")
    empty = client.post(
        "/v1/audio/transcribe",
        files={"file": ("empty.wav", b"", "audio/wav")},
    )
    unsupported = client.post(
        "/v1/audio/transcribe",
        files={"file": ("renamed.wav", b"data", "text/plain")},
    )
    oversized = client.post(
        "/v1/audio/transcribe",
        files={"file": ("large.wav", b"12345", "audio/wav")},
    )

    assert (missing.status_code, missing.json()["code"]) == (422, "INVALID_AUDIO")
    assert (empty.status_code, empty.json()["code"]) == (422, "INVALID_AUDIO")
    assert (unsupported.status_code, unsupported.json()["code"]) == (
        415,
        "UNSUPPORTED_AUDIO_MEDIA_TYPE",
    )
    assert (oversized.status_code, oversized.json()["code"]) == (413, "AUDIO_TOO_LARGE")
    assert transcriber.calls == 0


@pytest.mark.parametrize("case", ["corrupt", "no-audio-track"])
def test_decoder_failures_are_sanitized_as_invalid_audio(case: str) -> None:
    del case

    def failing_decoder(_: bytes) -> DecodedAudio:
        raise AudioProcessingError("INVALID_AUDIO", "The uploaded file does not contain decodable audio")

    settings = Settings()
    service = AudioTranscriptionService(
        settings,
        decoder=failing_decoder,
        transcriber_factory=lambda _: FakeTranscriber(),
    )
    service.start_warmup()
    service.wait_for_warmup()
    client = TestClient(create_app(settings, service))

    response = client.post(
        "/v1/audio/transcribe",
        files={"file": ("private.wav", b"invalid", "audio/wav")},
    )

    assert response.status_code == 422
    assert response.json() == {
        "code": "INVALID_AUDIO",
        "message": "The uploaded file does not contain decodable audio",
    }
    assert "private.wav" not in response.text


def test_rejects_duration_before_running_transcription() -> None:
    settings = Settings(audio_max_duration_seconds=2)
    client, _, transcriber = _client(settings=settings, duration=2.001)

    response = client.post(
        "/v1/audio/transcribe",
        files={"file": ("long.wav", b"audio", "audio/wav")},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "AUDIO_DURATION_LIMIT_EXCEEDED"
    assert transcriber.calls == 0


def test_accepts_audio_at_duration_limit() -> None:
    settings = Settings(audio_max_duration_seconds=2)
    client, _, transcriber = _client(settings=settings, duration=2)

    response = client.post(
        "/v1/audio/transcribe",
        files={"file": ("limit.wav", b"audio", "audio/wav")},
    )

    assert response.status_code == 200
    assert transcriber.calls == 1


def test_empty_and_failed_transcriptions_use_stable_sanitized_errors() -> None:
    empty_client, _, _ = _client(text="   ")
    empty = empty_client.post(
        "/v1/audio/transcribe",
        files={"file": ("empty.wav", b"audio", "audio/wav")},
    )

    class FailingTranscriber(FakeTranscriber):
        def transcribe(self, samples: Any) -> str:
            raise RuntimeError("secret model and host detail")

    settings = Settings()
    failing = AudioTranscriptionService(
        settings,
        decoder=lambda _: DecodedAudio(samples=[0.0], duration_seconds=1),
        transcriber_factory=lambda _: FailingTranscriber(),
    )
    failing.start_warmup()
    failing.wait_for_warmup()
    failure = TestClient(create_app(settings, failing)).post(
        "/v1/audio/transcribe",
        files={"file": ("voice.wav", b"audio", "audio/wav")},
    )

    assert (empty.status_code, empty.json()["code"]) == (422, "AUDIO_TRANSCRIPTION_EMPTY")
    assert failure.status_code == 503
    assert failure.json() == {
        "code": "AUDIO_TRANSCRIPTION_ERROR",
        "message": "Audio transcription failed",
    }
    assert "secret" not in failure.text


def test_audio_readiness_does_not_change_general_health_contract() -> None:
    settings = Settings()
    service = AudioTranscriptionService(
        settings,
        decoder=lambda _: DecodedAudio(samples=[0.0], duration_seconds=1),
        transcriber_factory=lambda _: FakeTranscriber(),
    )
    client = TestClient(create_app(settings, service))

    general = client.get("/health")
    not_ready = client.get("/v1/audio/health")
    service.start_warmup()
    service.wait_for_warmup()
    ready = client.get("/v1/audio/health")

    assert general.json() == {
        "status": "UP",
        "service": "preprocessing-service",
        "version": "0.1.0",
    }
    assert (not_ready.status_code, not_ready.json()["code"]) == (
        503,
        "AUDIO_MODEL_NOT_READY",
    )
    assert ready.status_code == 200
    assert ready.json() == {
        "status": "UP",
        "service": "audio-transcription",
        "model": "base",
    }


def test_warmup_is_singleton_while_model_is_loading() -> None:
    release = Event()
    started = Event()
    creations = 0

    def factory(_: Settings) -> FakeTranscriber:
        nonlocal creations
        creations += 1
        started.set()
        release.wait(timeout=2)
        return FakeTranscriber()

    service = AudioTranscriptionService(Settings(), transcriber_factory=factory)
    service.start_warmup()
    assert started.wait(timeout=1)
    service.start_warmup()
    release.set()
    service.wait_for_warmup()

    assert creations == 1
    assert service.is_ready()


def test_model_load_failure_keeps_audio_readiness_down_without_details() -> None:
    def fail(_: Settings) -> FakeTranscriber:
        raise RuntimeError("private cache path and repository detail")

    settings = Settings()
    service = AudioTranscriptionService(settings, transcriber_factory=fail)
    service.start_warmup()
    service.wait_for_warmup()

    response = TestClient(create_app(settings, service)).get("/v1/audio/health")

    assert response.status_code == 503
    assert response.json()["code"] == "AUDIO_MODEL_NOT_READY"
    assert "cache" not in response.text
