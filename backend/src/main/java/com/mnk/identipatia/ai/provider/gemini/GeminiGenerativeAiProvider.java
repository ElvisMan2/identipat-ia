package com.mnk.identipatia.ai.provider.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.AiMessage;
import com.mnk.identipatia.ai.model.AiMessageRole;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.ProviderExecution;
import com.mnk.identipatia.ai.model.TokenUsage;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GeminiGenerativeAiProvider implements GenerativeAiProvider {

    public static final String PROVIDER_ID = "gemini";
    private static final String JSON_MIME_TYPE = "application/json";

    private final GeminiTransport transport;
    private final String configuredModel;
    private final ObjectMapper objectMapper;
    private final StructuredOutputValidator validator;
    private final GeminiResponseDiagnostics diagnostics;

    GeminiGenerativeAiProvider(
            GeminiTransport transport,
            String configuredModel,
            ObjectMapper objectMapper,
            StructuredOutputValidator validator) {
        this(transport, configuredModel, objectMapper, validator, GeminiResponseDiagnostics.disabled());
    }

    GeminiGenerativeAiProvider(
            GeminiTransport transport,
            String configuredModel,
            ObjectMapper objectMapper,
            StructuredOutputValidator validator,
            GeminiResponseDiagnostics diagnostics) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        if (configuredModel == null || configuredModel.isBlank()) {
            throw new IllegalArgumentException("configuredModel must not be blank");
        }
        this.configuredModel = configuredModel;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public GenerativeAiResponse generate(GenerativeAiRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        GeminiCall call;
        try {
            call = toGeminiCall(request);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest(exception);
        }

        long startedNanos = System.nanoTime();
        try {
            GenerateContentResponse response = transport.generateContent(
                    configuredModel, call.contents(), call.config());
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            return toGenerativeResponse(response, request, latencyMs);
        } catch (GenerativeAiException exception) {
            throw exception;
        } catch (ApiException exception) {
            throw mapApiException(exception);
        } catch (GenAiIOException exception) {
            throw mapIoException(exception);
        } catch (RuntimeException exception) {
            throw providerError(exception);
        }
    }

    private GeminiCall toGeminiCall(GenerativeAiRequest request) {
        List<AiMessage> messages = request.prompt().messages();
        AiMessage system = null;
        List<Content> contents = new ArrayList<>();
        boolean userSeen = false;
        for (AiMessage message : messages) {
            if (message.role() == AiMessageRole.SYSTEM) {
                if (system != null || userSeen) {
                    throw new IllegalArgumentException("Gemini requires one leading system message");
                }
                system = message;
            } else if (message.role() == AiMessageRole.USER) {
                userSeen = true;
                contents.add(Content.builder()
                        .role("user")
                        .parts(Part.fromText(message.content()))
                        .build());
            } else {
                throw new IllegalArgumentException("Unsupported Gemini message role");
            }
        }
        if (system == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Gemini requires system and user content");
        }

        GenerateContentConfig.Builder config = GenerateContentConfig.builder()
                .systemInstruction(Content.builder().parts(Part.fromText(system.content())).build())
                .candidateCount(1)
                .responseMimeType(JSON_MIME_TYPE)
                .responseJsonSchema(request.output().jsonSchema());
        if (request.options().maxOutputTokens() != null) {
            config.maxOutputTokens(request.options().maxOutputTokens());
        }
        if (request.options().temperature() != null) {
            float temperature = request.options().temperature().floatValue();
            if (!Float.isFinite(temperature)) {
                throw new IllegalArgumentException("temperature is outside the Gemini supported range");
            }
            config.temperature(temperature);
        }
        return new GeminiCall(List.copyOf(contents), config.build());
    }

    private GenerativeAiResponse toGenerativeResponse(
            GenerateContentResponse response,
            GenerativeAiRequest request,
            long latencyMs) {
        if (response == null) {
            throw invalidResponse(null);
        }
        recordSafeDiagnostics(response);
        try {
            rejectBlockedPrompt(response);
            Candidate candidate = singleCandidate(response);
            String finishReason = finishReason(candidate);
            rejectIncompleteOrBlockedCandidate(candidate, finishReason);
            String text = singleTextPart(candidate);
            JsonNode structured = objectMapper.readTree(text);
            if (structured == null || !structured.isObject()) {
                throw invalidResponse(null);
            }
            validator.validate(request.output(), structured, PROVIDER_ID);

            JsonNode raw = objectMapper.readTree(response.toJson());
            TokenUsage usage = tokenUsage(response.usageMetadata());
            String model = response.modelVersion().filter(value -> !value.isBlank())
                    .orElse(configuredModel);
            String requestId = response.responseId().filter(value -> !value.isBlank()).orElse(null);
            return new GenerativeAiResponse(
                    new ProviderExecution(PROVIDER_ID, model, requestId),
                    structured,
                    raw,
                    usage,
                    finishReason,
                    latencyMs);
        } catch (GenerativeAiException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    private void recordSafeDiagnostics(GenerateContentResponse response) {
        try {
            List<Candidate> candidates = response.candidates().orElse(List.of());
            String blockReason = response.promptFeedback()
                    .flatMap(feedback -> feedback.blockReason())
                    .map(Object::toString)
                    .orElse("NONE");
            boolean promptBlocked = response.promptFeedback()
                    .flatMap(feedback -> feedback.blockReason())
                    .isPresent();
            List<String> finishReasons = candidates.stream()
                    .map(GeminiGenerativeAiProvider::finishReason)
                    .toList();
            List<Integer> partCounts = candidates.stream()
                    .map(candidate -> candidate.content()
                            .flatMap(Content::parts)
                            .map(List::size)
                            .orElse(0))
                    .toList();
            List<Boolean> candidateBlocked = candidates.stream()
                    .map(candidate -> candidate.safetyRatings().orElse(List.of()).stream()
                            .anyMatch(rating -> rating.blocked().orElse(false)))
                    .toList();
            GenerateContentResponseUsageMetadata usage = response.usageMetadata().orElse(null);
            diagnostics.record(new GeminiSafeResponseMetadata(
                    finishReasons,
                    blockReason,
                    candidates.size(),
                    partCounts,
                    promptBlocked,
                    candidateBlocked,
                    usage == null ? null : usage.promptTokenCount().orElse(null),
                    usage == null ? null : usage.candidatesTokenCount().orElse(null),
                    usage == null ? null : usage.thoughtsTokenCount().orElse(null),
                    usage == null ? null : usage.totalTokenCount().orElse(null)));
        } catch (RuntimeException ignored) {
            // Optional diagnostics must never alter provider behavior.
        }
    }

    private static void rejectBlockedPrompt(GenerateContentResponse response) {
        if (response.promptFeedback()
                .flatMap(feedback -> feedback.blockReason())
                .isPresent()) {
            throw invalidResponse(null);
        }
    }

    private static Candidate singleCandidate(GenerateContentResponse response) {
        List<Candidate> candidates = response.candidates().orElse(List.of());
        if (candidates.size() != 1) {
            throw invalidResponse(null);
        }
        return candidates.get(0);
    }

    private static String finishReason(Candidate candidate) {
        return candidate.finishReason().map(FinishReason::toString)
                .filter(value -> !value.isBlank())
                .orElse("FINISH_REASON_UNSPECIFIED");
    }

    private static void rejectIncompleteOrBlockedCandidate(Candidate candidate, String finishReason) {
        if (!("STOP".equals(finishReason) || "FINISH_REASON_UNSPECIFIED".equals(finishReason))) {
            throw invalidResponse(null);
        }
        boolean blocked = candidate.safetyRatings().orElse(List.of()).stream()
                .anyMatch(rating -> rating.blocked().orElse(false));
        if (blocked) {
            throw invalidResponse(null);
        }
    }

    private String singleTextPart(Candidate candidate) throws JsonProcessingException {
        Content content = candidate.content().orElseThrow(() -> invalidResponse(null));
        List<Part> parts = content.parts().orElse(List.of());
        List<String> outputTexts = new ArrayList<>();
        for (Part part : parts) {
            JsonNode semanticPart = objectMapper.readTree(part.toJson());
            boolean thought = part.thought().orElse(false);
            if (hasIncompatiblePartFields(semanticPart)) {
                throw invalidResponse(null);
            }
            if (!thought && part.text().isPresent()) {
                outputTexts.add(part.text().orElseThrow());
            }
        }
        if (outputTexts.size() != 1 || outputTexts.get(0).isBlank()) {
            throw invalidResponse(null);
        }
        return outputTexts.get(0);
    }

    private static boolean hasIncompatiblePartFields(JsonNode part) {
        if (!part.isObject()) {
            return true;
        }
        var names = part.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!("text".equals(name) || "thought".equals(name) || "thoughtSignature".equals(name))) {
                return true;
            }
        }
        return false;
    }

    private TokenUsage tokenUsage(Optional<GenerateContentResponseUsageMetadata> metadata) {
        if (metadata.isEmpty()) {
            return new TokenUsage(null, null, null, null);
        }
        GenerateContentResponseUsageMetadata usage = metadata.orElseThrow();
        JsonNode details;
        try {
            details = objectMapper.readTree(usage.toJson());
        } catch (JsonProcessingException exception) {
            throw invalidResponse(exception);
        }
        return new TokenUsage(
                asLong(usage.promptTokenCount()),
                asLong(usage.candidatesTokenCount()),
                asLong(usage.totalTokenCount()),
                details);
    }

    private static Long asLong(Optional<Integer> value) {
        return value.map(Integer::longValue).orElse(null);
    }

    private static GenerativeAiException mapApiException(ApiException exception) {
        return switch (exception.code()) {
            case 401, 403 -> mapped(GenerativeAiErrorType.AUTHENTICATION, false,
                    "Gemini authentication or authorization failed", exception);
            case 400, 404, 422 -> mapped(GenerativeAiErrorType.INVALID_REQUEST, false,
                    "Gemini rejected the generative AI request", exception);
            case 408 -> mapped(GenerativeAiErrorType.TIMEOUT, true,
                    "Gemini did not respond within the configured timeout", exception);
            case 429 -> mapped(GenerativeAiErrorType.RATE_LIMITED, true,
                    "Gemini rate limited the generative AI request", exception);
            default -> exception.code() >= 500 && exception.code() <= 599
                    ? mapped(GenerativeAiErrorType.PROVIDER_UNAVAILABLE, true,
                            "Gemini is temporarily unavailable", exception)
                    : mapped(GenerativeAiErrorType.PROVIDER_ERROR, false,
                            "Gemini could not complete the generative AI request", exception);
        };
    }

    private static GenerativeAiException mapIoException(GenAiIOException exception) {
        if (hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, InterruptedIOException.class)) {
            return mapped(GenerativeAiErrorType.TIMEOUT, true,
                    "Gemini did not respond within the configured timeout", exception);
        }
        if (hasCause(exception, ConnectException.class)) {
            return mapped(GenerativeAiErrorType.PROVIDER_UNAVAILABLE, true,
                    "Gemini is temporarily unavailable", exception);
        }
        return mapped(GenerativeAiErrorType.PROVIDER_UNAVAILABLE, true,
                "Gemini transport is temporarily unavailable", exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static GenerativeAiException invalidRequest(Throwable cause) {
        return mapped(GenerativeAiErrorType.INVALID_REQUEST, false,
                "The generative AI request is invalid", cause);
    }

    private static GenerativeAiException invalidResponse(Throwable cause) {
        return mapped(GenerativeAiErrorType.INVALID_RESPONSE, false,
                "The generative AI provider returned an invalid response", cause);
    }

    private static GenerativeAiException providerError(Throwable cause) {
        return mapped(GenerativeAiErrorType.PROVIDER_ERROR, false,
                "Gemini could not complete the generative AI request", cause);
    }

    private static GenerativeAiException mapped(
            GenerativeAiErrorType type,
            boolean retryable,
            String message,
            Throwable cause) {
        return new GenerativeAiException(type, PROVIDER_ID, retryable, message, cause);
    }

    private record GeminiCall(List<Content> contents, GenerateContentConfig config) {
    }
}
