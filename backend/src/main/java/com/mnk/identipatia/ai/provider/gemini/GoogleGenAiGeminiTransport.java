package com.mnk.identipatia.ai.provider.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

import java.util.List;
import java.util.Objects;

final class GoogleGenAiGeminiTransport implements GeminiTransport, AutoCloseable {

    private final Client client;

    GoogleGenAiGeminiTransport(Client client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public GenerateContentResponse generateContent(
            String model,
            List<Content> contents,
            GenerateContentConfig config) {
        return client.models.generateContent(model, contents, config);
    }

    @Override
    public void close() {
        client.close();
    }
}
