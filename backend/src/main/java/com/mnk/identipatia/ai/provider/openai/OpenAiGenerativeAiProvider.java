package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ChatModel;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseUsage;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OpenAiGenerativeAiProvider implements GenerativeAiProvider {

    public static final String PROVIDER_ID = "openai";

    private final OpenAIClient client;
    private final String configuredModel;
    private final ObjectMapper objectMapper;
    private final StructuredOutputValidator validator;

    public OpenAiGenerativeAiProvider(
            OpenAIClient client,
            String configuredModel,
            ObjectMapper objectMapper,
            StructuredOutputValidator validator) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        if (configuredModel == null || configuredModel.isBlank()) {
            throw new IllegalArgumentException("configuredModel must not be blank");
        }
        this.configuredModel = configuredModel;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public GenerativeAiResponse generate(GenerativeAiRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResponseCreateParams openAiRequest;
        try {
            openAiRequest = toOpenAiRequest(request);
        } catch (IllegalArgumentException exception) {
            throw new GenerativeAiException(
                    GenerativeAiErrorType.INVALID_REQUEST,
                    PROVIDER_ID,
                    false,
                    "The generative AI request is invalid",
                    exception);
        }

        long startedNanos = System.nanoTime();
        try {
            Response response = client.responses().create(openAiRequest);
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            return toGenerativeResponse(response, request, latencyMs);
        } catch (GenerativeAiException exception) {
            throw exception;
        } catch (OpenAIServiceException exception) {
            throw mapServiceException(exception);
        } catch (OpenAIIoException exception) {
            throw mapIoException(exception);
        } catch (OpenAIInvalidDataException exception) {
            throw invalidResponse(exception);
        } catch (OpenAIException exception) {
            throw providerError(exception);
        } catch (IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    private ResponseCreateParams toOpenAiRequest(GenerativeAiRequest request) {
        List<ResponseInputItem> input = request.prompt().messages().stream()
                .map(this::toInputItem)
                .toList();

        ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder =
                ResponseFormatTextJsonSchemaConfig.Schema.builder();
        request.output().jsonSchema().fields().forEachRemaining(entry ->
                schemaBuilder.putAdditionalProperty(entry.getKey(), JsonValue.fromJsonNode(entry.getValue())));

        ResponseFormatTextJsonSchemaConfig format = ResponseFormatTextJsonSchemaConfig.builder()
                .name(request.output().schemaId())
                .schema(schemaBuilder.build())
                .strict(request.output().strict())
                .build();

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(configuredModel)
                .inputOfResponse(input)
                .text(ResponseTextConfig.builder().format(format).build())
                .store(false);

        if (request.options().maxOutputTokens() != null) {
            builder.maxOutputTokens(request.options().maxOutputTokens().longValue());
        }
        if (request.options().temperature() != null) {
            builder.temperature(request.options().temperature());
        }
        return builder.build();
    }

    private ResponseInputItem toInputItem(AiMessage message) {
        EasyInputMessage.Role role = switch (message.role()) {
            case SYSTEM -> EasyInputMessage.Role.SYSTEM;
            case USER -> EasyInputMessage.Role.USER;
        };
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(message.content())
                .build());
    }

    private GenerativeAiResponse toGenerativeResponse(
            Response response,
            GenerativeAiRequest request,
            long latencyMs) {
        String outputText = extractOutputText(response);
        JsonNode structuredOutput;
        try {
            structuredOutput = objectMapper.readTree(outputText);
        } catch (JsonProcessingException exception) {
            throw invalidResponse(exception);
        }
        if (structuredOutput == null) {
            throw invalidResponse(null);
        }
        validator.validate(request.output(), structuredOutput, PROVIDER_ID);

        JsonNode rawResponse;
        try {
            rawResponse = objectMapper.valueToTree(response);
        } catch (IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }

        String responseModel = extractModelId(response);
        return new GenerativeAiResponse(
                new ProviderExecution(PROVIDER_ID, responseModel, response.id()),
                structuredOutput,
                rawResponse,
                tokenUsage(response),
                finishReason(response),
                latencyMs);
    }

    private String extractOutputText(Response response) {
        List<String> texts = new ArrayList<>();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            message.content().stream()
                    .filter(ResponseOutputMessage.Content::isOutputText)
                    .map(ResponseOutputMessage.Content::asOutputText)
                    .map(output -> output.text())
                    .forEach(texts::add);
        }
        if (texts.size() != 1) {
            throw invalidResponse(null);
        }
        return texts.get(0);
    }

    private String extractModelId(Response response) {
        String modelId = response.model().accept(new ResponsesModel.Visitor<>() {
            @Override
            public String visitString(String value) {
                return value;
            }

            @Override
            public String visitChat(ChatModel value) {
                return value.asString();
            }

            @Override
            public String visitOnly(ResponsesModel.ResponsesOnlyModel value) {
                return value.asString();
            }

            @Override
            public String unknown(JsonValue json) {
                return json == null ? null : json.asStringOrThrow();
            }
        });
        if (modelId == null || modelId.isBlank()) {
            throw invalidResponse(null);
        }
        return modelId;
    }

    private TokenUsage tokenUsage(Response response) {
        if (response.usage().isEmpty()) {
            return new TokenUsage(null, null, null, null);
        }
        ResponseUsage usage = response.usage().orElseThrow();
        return new TokenUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                objectMapper.valueToTree(usage));
    }

    private String finishReason(Response response) {
        if (response.incompleteDetails().isPresent()
                && response.incompleteDetails().orElseThrow().reason().isPresent()) {
            return "incomplete:" + response.incompleteDetails().orElseThrow()
                    .reason().orElseThrow().asString();
        }
        return response.status().map(status -> status.asString()).orElse("unknown");
    }

    private GenerativeAiException mapServiceException(OpenAIServiceException exception) {
        int status = exception.statusCode();
        if (status == 401 || status == 403) {
            return new GenerativeAiException(
                    GenerativeAiErrorType.AUTHENTICATION, PROVIDER_ID, false,
                    "The generative AI provider rejected authentication", exception);
        }
        if (status == 400 || status == 404 || status == 409 || status == 422) {
            return new GenerativeAiException(
                    GenerativeAiErrorType.INVALID_REQUEST, PROVIDER_ID, false,
                    "The generative AI provider rejected the request", exception);
        }
        if (status == 429) {
            return new GenerativeAiException(
                    GenerativeAiErrorType.RATE_LIMITED, PROVIDER_ID, true,
                    "The generative AI provider rate limit was reached", exception);
        }
        if (status >= 500) {
            return new GenerativeAiException(
                    GenerativeAiErrorType.PROVIDER_UNAVAILABLE, PROVIDER_ID, true,
                    "The generative AI provider is temporarily unavailable", exception);
        }
        return providerError(exception);
    }

    private GenerativeAiException mapIoException(OpenAIIoException exception) {
        boolean timeout = hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, InterruptedIOException.class);
        return new GenerativeAiException(
                timeout ? GenerativeAiErrorType.TIMEOUT : GenerativeAiErrorType.PROVIDER_UNAVAILABLE,
                PROVIDER_ID,
                true,
                timeout
                        ? "The generative AI provider request timed out"
                        : "The generative AI provider could not be reached",
                exception);
    }

    private GenerativeAiException invalidResponse(Throwable cause) {
        return new GenerativeAiException(
                GenerativeAiErrorType.INVALID_RESPONSE,
                PROVIDER_ID,
                false,
                "The generative AI provider returned an invalid response",
                cause);
    }

    private GenerativeAiException providerError(Throwable cause) {
        return new GenerativeAiException(
                GenerativeAiErrorType.PROVIDER_ERROR,
                PROVIDER_ID,
                false,
                "The generative AI provider request failed",
                cause);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
