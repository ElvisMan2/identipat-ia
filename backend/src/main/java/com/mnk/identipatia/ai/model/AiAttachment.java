package com.mnk.identipatia.ai.model;

import java.util.Arrays;
import java.util.Objects;

/** Provider-neutral immutable binary input. */
public final class AiAttachment {
    private final String mediaType;
    private final byte[] content;

    public AiAttachment(String mediaType, byte[] content) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
        Objects.requireNonNull(content, "content must not be null");
        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        this.mediaType = mediaType;
        this.content = Arrays.copyOf(content, content.length);
    }

    public String mediaType() {
        return mediaType;
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    public int sizeBytes() {
        return content.length;
    }
}
