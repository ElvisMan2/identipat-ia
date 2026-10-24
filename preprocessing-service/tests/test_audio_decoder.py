from __future__ import annotations

import io
import math
import struct
import wave

import av
import numpy as np
import pytest
from fastapi.testclient import TestClient
from faster_whisper.audio import decode_audio

from preprocessing_service.core.config import Settings
from preprocessing_service.main import create_app
from preprocessing_service.services.audio_transcriber import (
    AudioTranscriptionService,
    inspect_audio_container,
)


class CountingTranscriber:
    def __init__(self) -> None:
        self.calls = 0

    def transcribe(self, samples: object) -> str:
        del samples
        self.calls += 1
        return "Descripción hablada suficientemente extensa"


def _audio_only_mp4() -> bytes:
    output = io.BytesIO()
    with av.open(output, mode="w", format="mp4") as container:
        stream = container.add_stream("aac", rate=16_000)
        stream.layout = "mono"
        samples = np.zeros((1, 4_000), dtype=np.int16)
        frame = av.AudioFrame.from_ndarray(samples, format="s16", layout="mono")
        frame.sample_rate = 16_000
        for packet in stream.encode(frame):
            container.mux(packet)
        for packet in stream.encode():
            container.mux(packet)
    return output.getvalue()


def _video_only_mp4() -> bytes:
    output = io.BytesIO()
    with av.open(output, mode="w", format="mp4") as container:
        stream = container.add_stream("mpeg4", rate=1)
        stream.width = 16
        stream.height = 16
        stream.pix_fmt = "yuv420p"
        frame = av.VideoFrame(width=16, height=16, format="yuv420p")
        for packet in stream.encode(frame):
            container.mux(packet)
        for packet in stream.encode():
            container.mux(packet)
    return output.getvalue()


def test_faster_whisper_decodes_pcm_wav_from_memory() -> None:
    sample_rate = 16_000
    frame_count = sample_rate // 4
    frames = b"".join(
        struct.pack("<h", round(8_000 * math.sin(2 * math.pi * 440 * index / sample_rate)))
        for index in range(frame_count)
    )
    wav_bytes = io.BytesIO()
    with wave.open(wav_bytes, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(frames)

    decoded = decode_audio(io.BytesIO(wav_bytes.getvalue()), sampling_rate=sample_rate)

    assert decoded.dtype.name == "float32"
    assert decoded.size > 0


def test_inspects_audio_only_mp4_container() -> None:
    inspection = inspect_audio_container(_audio_only_mp4())

    assert inspection.audio_stream_count >= 1
    assert inspection.video_stream_count == 0


@pytest.mark.parametrize("media_type", ["video/mp4", "video/3gpp"])
def test_rejects_real_video_container_before_transcription(media_type: str) -> None:
    settings = Settings()
    transcriber = CountingTranscriber()
    service = AudioTranscriptionService(
        settings,
        transcriber_factory=lambda _: transcriber,
    )
    service.start_warmup()
    service.wait_for_warmup()

    response = TestClient(create_app(settings, service)).post(
        "/v1/audio/transcribe",
        files={"file": ("not-an-audio.mp4", _video_only_mp4(), media_type)},
    )

    assert response.status_code == 422
    assert response.json() == {
        "code": "INVALID_AUDIO",
        "message": "The uploaded file does not contain decodable audio",
    }
    assert transcriber.calls == 0
