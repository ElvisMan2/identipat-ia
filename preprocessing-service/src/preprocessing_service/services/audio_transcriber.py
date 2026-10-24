"""Local audio decoding and replaceable faster-whisper transcription."""

import logging
from collections.abc import Callable
from dataclasses import dataclass
from io import BytesIO
from threading import RLock, Thread
from typing import Any, Protocol

from preprocessing_service.core.config import Settings
from preprocessing_service.schemas.audio import AudioTranscriptionResponse

AUDIO_SAMPLE_RATE = 16_000
LOGGER = logging.getLogger(__name__)


class AudioProcessingError(Exception):
    """Controlled processing failure safe to expose through the private API."""

    def __init__(self, code: str, message: str, status_code: int = 422) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


@dataclass(frozen=True)
class DecodedAudio:
    samples: Any
    duration_seconds: float


@dataclass(frozen=True)
class AudioContainerInspection:
    audio_stream_count: int
    video_stream_count: int


class AudioDecoder(Protocol):
    def __call__(self, data: bytes) -> DecodedAudio: ...


class AudioTranscriber(Protocol):
    def transcribe(self, samples: Any) -> str: ...


def inspect_audio_container(data: bytes) -> AudioContainerInspection:
    """Require an audio stream and reject containers carrying any video stream."""
    try:
        import av

        with av.open(BytesIO(data), mode="r") as container:
            inspection = AudioContainerInspection(
                audio_stream_count=sum(stream.type == "audio" for stream in container.streams),
                video_stream_count=sum(stream.type == "video" for stream in container.streams),
            )
    except AudioProcessingError:
        raise
    except Exception as exception:
        raise AudioProcessingError(
            "INVALID_AUDIO",
            "The uploaded file does not contain decodable audio",
        ) from exception

    if inspection.audio_stream_count == 0 or inspection.video_stream_count > 0:
        raise AudioProcessingError(
            "INVALID_AUDIO",
            "The uploaded file does not contain decodable audio",
        )
    return inspection


def decode_audio_bytes(data: bytes) -> DecodedAudio:
    """Decode untrusted media to mono 16 kHz samples without using a temporary file."""
    try:
        from faster_whisper.audio import decode_audio

        inspect_audio_container(data)
        samples = decode_audio(BytesIO(data), sampling_rate=AUDIO_SAMPLE_RATE)
        sample_count = len(samples)
        if sample_count == 0:
            raise ValueError("decoded audio is empty")
        return DecodedAudio(samples=samples, duration_seconds=sample_count / AUDIO_SAMPLE_RATE)
    except AudioProcessingError:
        raise
    except Exception as exception:
        raise AudioProcessingError(
            "INVALID_AUDIO",
            "The uploaded file does not contain decodable audio",
        ) from exception


class FasterWhisperAudioTranscriber:
    """Single-model adapter. Model construction is intentionally isolated for tests."""

    def __init__(self, settings: Settings) -> None:
        from faster_whisper import WhisperModel

        self._language = settings.audio_language
        self._model = WhisperModel(
            settings.audio_model,
            device=settings.audio_device,
            compute_type=settings.audio_compute_type,
            download_root=settings.audio_model_cache_dir,
        )

    def transcribe(self, samples: Any) -> str:
        segments, _ = self._model.transcribe(
            samples,
            language=self._language,
            task="transcribe",
            vad_filter=True,
        )
        materialized = list(segments)
        return " ".join(segment.text.strip() for segment in materialized if segment.text.strip()).strip()


class AudioTranscriptionService:
    """Own the process-wide model lifecycle and enforce decode-before-inference limits."""

    def __init__(
        self,
        settings: Settings,
        *,
        decoder: AudioDecoder = decode_audio_bytes,
        transcriber_factory: Callable[[Settings], AudioTranscriber] = FasterWhisperAudioTranscriber,
    ) -> None:
        self._settings = settings
        self._decoder = decoder
        self._transcriber_factory = transcriber_factory
        self._lock = RLock()
        self._transcriber: AudioTranscriber | None = None
        self._loading = False
        self._load_failed = False
        self._warmup_thread: Thread | None = None

    def start_warmup(self) -> None:
        """Start at most one non-blocking model load for this process."""
        with self._lock:
            if self._transcriber is not None or self._loading:
                return
            self._loading = True
            self._load_failed = False
            self._warmup_thread = Thread(
                target=self._complete_warmup,
                name="audio-model-warmup",
                daemon=True,
            )
            self._warmup_thread.start()

    def _complete_warmup(self) -> None:
        try:
            transcriber = self._transcriber_factory(self._settings)
        except Exception as exception:  # noqa: BLE001 - isolate native/model loading failures.
            LOGGER.error("Audio model warm-up failed (%s)", type(exception).__name__)
            with self._lock:
                self._load_failed = True
                self._loading = False
            return
        with self._lock:
            self._transcriber = transcriber
            self._loading = False

    def wait_for_warmup(self, timeout: float = 5) -> None:
        thread = self._warmup_thread
        if thread is not None:
            thread.join(timeout)

    def is_ready(self) -> bool:
        with self._lock:
            return self._transcriber is not None

    def transcribe(self, data: bytes) -> AudioTranscriptionResponse:
        decoded = self._decoder(data)
        if decoded.duration_seconds <= 0:
            raise AudioProcessingError(
                "INVALID_AUDIO",
                "The uploaded file does not contain decodable audio",
            )
        if decoded.duration_seconds > self._settings.audio_max_duration_seconds:
            raise AudioProcessingError(
                "AUDIO_DURATION_LIMIT_EXCEEDED",
                "The audio exceeds the configured duration limit",
            )

        with self._lock:
            transcriber = self._transcriber
        if transcriber is None:
            raise AudioProcessingError(
                "AUDIO_MODEL_NOT_READY",
                "The audio transcription model is not ready",
                status_code=503,
            )

        try:
            text = transcriber.transcribe(decoded.samples).strip()
        except AudioProcessingError:
            raise
        except Exception as exception:
            raise AudioProcessingError(
                "AUDIO_TRANSCRIPTION_ERROR",
                "Audio transcription failed",
                status_code=503,
            ) from exception
        if not text:
            raise AudioProcessingError(
                "AUDIO_TRANSCRIPTION_EMPTY",
                "The audio did not produce a transcription",
            )
        return AudioTranscriptionResponse(
            text=text,
            duration_seconds=round(decoded.duration_seconds, 3),
            character_count=len(text),
            language=self._settings.audio_language,
            model=self._settings.audio_model,
        )
