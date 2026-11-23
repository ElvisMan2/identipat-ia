package com.mnk.identipatia.ai.provider.gemini;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

import java.util.List;

interface GeminiTransport {

    GenerateContentResponse generateContent(
            String model,
            List<Content> contents,
            GenerateContentConfig config);
}
