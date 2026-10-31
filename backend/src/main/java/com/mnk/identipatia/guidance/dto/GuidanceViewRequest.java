package com.mnk.identipatia.guidance.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.IOException;

public record GuidanceViewRequest(
        @NotNull @Positive
        @JsonDeserialize(using = GuideVersionDeserializer.class)
        Long guideVersion) {

    public static final class GuideVersionDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            }
            return parser.getLongValue();
        }
    }
}

