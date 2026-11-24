package com.mnk.identipatia.ai;

import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;

public interface GenerativeAiProvider {

    String providerId();

    default boolean supportsAttachment(String mediaType) {
        return false;
    }

    GenerativeAiResponse generate(GenerativeAiRequest request);
}
