from __future__ import annotations

import io
import math
import struct
import wave

from faster_whisper.audio import decode_audio


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
