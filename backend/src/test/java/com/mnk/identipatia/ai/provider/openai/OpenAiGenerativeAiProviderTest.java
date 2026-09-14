package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.AiMessage;
import com.mnk.identipatia.ai.model.AiMessageRole;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.model.RenderedPrompt;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiGenerativeAiProviderTest {

    private static final String MODEL = "test-model";
    private static final String HASH = "0".repeat(64);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<String> capturedRequest = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private HttpServer server;
    private OpenAIClient client;
    private ResponsePlan responsePlan;

    @BeforeEach
    void startServer() throws IOException {
        responsePlan = exchange -> reply(exchange, 200, successfulResponse(validOutput()));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestCount.incrementAndGet();
            capturedRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responsePlan.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsResponsesRequestWithMessagesSchemaStrictModelAndOptions() throws Exception {
        GenerativeAiResponse response = provider(Duration.ofSeconds(2))
                .generate(request(new GenerationOptions(120, 0.2)));
        JsonNode sent = objectMapper.readTree(capturedRequest.get());

        assertThat(requestCount).hasValue(1);
        assertThat(sent.path("model").asText()).isEqualTo(MODEL);
        assertThat(sent.path("store").asBoolean()).isFalse();
        assertThat(sent.path("max_output_tokens").asInt()).isEqualTo(120);
        assertThat(sent.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(sent.path("input")).hasSize(2);
        assertThat(sent.path("input").get(0).path("role").asText()).isEqualTo("system");
        assertThat(sent.path("input").get(0).path("content").asText()).isEqualTo("system content");
        assertThat(sent.path("input").get(1).path("role").asText()).isEqualTo("user");
        assertThat(sent.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(sent.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(sent.path("text").path("format").path("schema"))
                .isEqualTo(schemaDefinition().jsonSchema());

        assertThat(response.provider().provider()).isEqualTo("openai");
        assertThat(response.provider().model()).isEqualTo(MODEL);
        assertThat(response.provider().providerRequestId()).isEqualTo("resp_test_123");
        assertThat(response.structuredOutput().path("status").asText()).isEqualTo("OK");
        assertThat(response.rawProviderResponse().path("id").asText()).isEqualTo("resp_test_123");
        assertThat(response.tokenUsage().inputTokens()).isEqualTo(10L);
        assertThat(response.tokenUsage().outputTokens()).isEqualTo(8L);
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(18L);
        assertThat(response.tokenUsage().providerDetails()).isNotNull();
        assertThat(response.finishReason()).isEqualTo("completed");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void omitsNullGenerationOptions() throws Exception {
        provider(Duration.ofSeconds(2)).generate(request(GenerationOptions.defaults()));
        JsonNode sent = objectMapper.readTree(capturedRequest.get());

        assertThat(sent.has("max_output_tokens")).isFalse();
        assertThat(sent.has("temperature")).isFalse();
    }

    @Test
    void mapsIncompleteReasonFromResponsesApi() {
        String incomplete = successfulResponse(validOutput())
                .replace("\"status\":\"completed\"", "\"status\":\"incomplete\"")
                .replace("\"incomplete_details\":null",
                        "\"incomplete_details\":{\"reason\":\"max_output_tokens\"}");
        responsePlan = exchange -> reply(exchange, 200, incomplete);

        GenerativeAiResponse response = provider(Duration.ofSeconds(2))
                .generate(request(GenerationOptions.defaults()));

        assertThat(response.finishReason()).isEqualTo("incomplete:max_output_tokens");
    }

    @Test
    void mapsKnownSdkModelAndFindsOutputTextAfterReasoningItem() {
        String knownModel = "gpt-5-mini-2025-08-07";
        responsePlan = exchange -> reply(exchange, 200, successfulResponse(
                jsonString(knownModel),
                reasoningOutputItem() + "," + messageOutputItem(validOutput())));

        GenerativeAiResponse response = provider(Duration.ofSeconds(2))
                .generate(request(GenerationOptions.defaults()));

        assertThat(response.provider().model()).isEqualTo(knownModel);
        assertThat(response.structuredOutput().path("status").asText()).isEqualTo("OK");
    }

    @Test
    void preservesFutureModelRepresentedBySdkStringVariant() {
        String futureModel = "future-responses-model-2030-01-01";
        responsePlan = exchange -> reply(exchange, 200, successfulResponse(
                jsonString(futureModel), messageOutputItem(validOutput())));

        GenerativeAiResponse response = provider(Duration.ofSeconds(2))
                .generate(request(GenerationOptions.defaults()));

        assertThat(response.provider().model()).isEqualTo(futureModel);
    }

    @Test
    void rejectsUnreadableModelWithSanitizedInvalidResponse() {
        responsePlan = exchange -> reply(exchange, 200, successfulResponse(
                "{\"unexpected\":\"model-shape\"}", messageOutputItem(validOutput())));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(2))
                .generate(request(GenerationOptions.defaults())))
                .isExactlyInstanceOf(GenerativeAiException.class)
                .satisfies(error -> {
                    GenerativeAiException exception = (GenerativeAiException) error;
                    assertThat(exception.type()).isEqualTo(GenerativeAiErrorType.INVALID_RESPONSE);
                    assertThat(exception.getMessage())
                            .isEqualTo("The generative AI provider returned an invalid response");
                    assertThat(exception.getMessage()).doesNotContain("model-shape");
                });
    }

    @Test
    void rejectsResponseWithoutOutputText() {
        responsePlan = exchange -> reply(exchange, 200, successfulResponse(
                jsonString(MODEL), reasoningOutputItem()));

        assertInvalidResponse();
    }

    @Test
    void rejectsAmbiguousMultipleOutputTextBlocks() {
        responsePlan = exchange -> reply(exchange, 200, successfulResponse(
                jsonString(MODEL), messageWithTwoOutputTexts(validOutput(), validOutput())));

        assertInvalidResponse();
    }

    @ParameterizedTest
    @CsvSource({
            "401, AUTHENTICATION, false",
            "400, INVALID_REQUEST, false",
            "429, RATE_LIMITED, true",
            "503, PROVIDER_UNAVAILABLE, true"
    })
    void mapsHttpErrorsWithoutLeakingProviderMessages(
            int status,
            GenerativeAiErrorType expectedType,
            boolean retryable) {
        responsePlan = exchange -> reply(exchange, status, """
                {"error":{"message":"sensitive provider detail","type":"test_error","code":"test"}}
                """);

        assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).generate(request(GenerationOptions.defaults())))
                .isExactlyInstanceOf(GenerativeAiException.class)
                .satisfies(error -> {
                    GenerativeAiException exception = (GenerativeAiException) error;
                    assertThat(exception.type()).isEqualTo(expectedType);
                    assertThat(exception.provider()).isEqualTo("openai");
                    assertThat(exception.retryable()).isEqualTo(retryable);
                    assertThat(exception.getMessage()).doesNotContain("sensitive provider detail");
                    assertThat(exception.getCause()).isNotNull();
                });
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void mapsTimeoutAndPerformsOnlyOneHttpAttempt() {
        responsePlan = exchange -> {
            try {
                Thread.sleep(500);
                reply(exchange, 200, successfulResponse(validOutput()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        assertThatThrownBy(() -> provider(Duration.ofMillis(50)).generate(request(GenerationOptions.defaults())))
                .isExactlyInstanceOf(GenerativeAiException.class)
                .satisfies(error -> {
                    GenerativeAiException exception = (GenerativeAiException) error;
                    assertThat(exception.type()).isEqualTo(GenerativeAiErrorType.TIMEOUT);
                    assertThat(exception.retryable()).isTrue();
                });
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void rejectsMalformedOrSchemaInvalidStructuredOutput() {
        responsePlan = exchange -> reply(exchange, 200, successfulResponse("{not-json"));
        assertInvalidResponse();

        requestCount.set(0);
        responsePlan = exchange -> reply(exchange, 200, successfulResponse("""
                {"schemaVersion":"provider-smoke-result/1.0","status":"OK","message":"ready","extra":true}
                """));
        assertInvalidResponse();
    }

    private void assertInvalidResponse() {
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).generate(request(GenerationOptions.defaults())))
                .isExactlyInstanceOf(GenerativeAiException.class)
                .satisfies(error -> assertThat(((GenerativeAiException) error).type())
                        .isEqualTo(GenerativeAiErrorType.INVALID_RESPONSE));
        assertThat(requestCount).hasValue(1);
    }

    private OpenAiGenerativeAiProvider provider(Duration timeout) {
        if (client != null) {
            client.close();
        }
        client = OpenAIOkHttpClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .apiKey("test-key-not-secret")
                .timeout(timeout)
                .maxRetries(0)
                .build();
        return new OpenAiGenerativeAiProvider(
                client, MODEL, objectMapper, new StructuredOutputValidator());
    }

    private GenerativeAiRequest request(GenerationOptions options) {
        RenderedPrompt prompt = new RenderedPrompt(
                new PromptReference("provider-smoke-test", "1.0"),
                List.of(
                        new AiMessage(AiMessageRole.SYSTEM, "system content"),
                        new AiMessage(AiMessageRole.USER, "user content")),
                HASH,
                HASH,
                "[SYSTEM]\nsystem content\n\n[USER]\nuser content");
        return new GenerativeAiRequest(prompt, schemaDefinition(), options);
    }

    private StructuredOutputDefinition schemaDefinition() {
        return new OutputSchemaRegistry(objectMapper).load("provider-smoke-result", "1.0");
    }

    private String validOutput() {
        return """
                {"schemaVersion":"provider-smoke-result/1.0","status":"OK","message":"ready"}
                """.trim();
    }

    private String successfulResponse(String outputText) {
        return successfulResponse(jsonString(MODEL), messageOutputItem(outputText));
    }

    private String successfulResponse(String modelJson, String outputItems) {
        return """
                    {
                      "id":"resp_test_123",
                      "object":"response",
                      "created_at":1741476542,
                      "status":"completed",
                      "error":null,
                      "incomplete_details":null,
                      "instructions":null,
                      "max_output_tokens":120,
                      "model":%s,
                      "output":[%s],
                      "parallel_tool_calls":true,
                      "previous_response_id":null,
                      "store":false,
                      "temperature":0.2,
                      "tool_choice":"auto",
                      "tools":[],
                      "top_p":1.0,
                      "truncation":"disabled",
                      "usage":{
                        "input_tokens":10,
                        "input_tokens_details":{"cached_tokens":0},
                        "output_tokens":8,
                        "output_tokens_details":{"reasoning_tokens":0},
                        "total_tokens":18
                      }
                    }
                    """.formatted(modelJson, outputItems);
    }

    private String messageOutputItem(String outputText) {
        return """
                {
                  "type":"message",
                  "id":"msg_test_123",
                  "status":"completed",
                  "role":"assistant",
                  "content":[{"type":"output_text","annotations":[],"text":%s}]
                }
                """.formatted(jsonString(outputText)).trim();
    }

    private String messageWithTwoOutputTexts(String first, String second) {
        return """
                {
                  "type":"message",
                  "id":"msg_test_123",
                  "status":"completed",
                  "role":"assistant",
                  "content":[
                    {"type":"output_text","annotations":[],"text":%s},
                    {"type":"output_text","annotations":[],"text":%s}
                  ]
                }
                """.formatted(jsonString(first), jsonString(second)).trim();
    }

    private String reasoningOutputItem() {
        return """
                {
                  "type":"reasoning",
                  "id":"rs_test_123",
                  "summary":[]
                }
                """.trim();
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ResponsePlan {
        void handle(HttpExchange exchange) throws IOException;
    }
}
