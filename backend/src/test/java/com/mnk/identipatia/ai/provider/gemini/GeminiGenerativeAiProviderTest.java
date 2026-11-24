package com.mnk.identipatia.ai.provider.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.AiMessage;
import com.mnk.identipatia.ai.model.AiMessageRole;
import com.mnk.identipatia.ai.model.AiAttachment;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.model.RenderedPrompt;
import com.mnk.identipatia.ai.model.StructuredOutputDefinition;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiGenerativeAiProviderTest {

    private static final String MODEL = "test-model";
    private static final String HASH = "0".repeat(64);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OutputSchemaRegistry schemas = new OutputSchemaRegistry(objectMapper);
    private final StructuredOutputValidator validator = new StructuredOutputValidator();
    private Client liveClient;
    private HttpServer server;

    @AfterEach
    void closeResources() {
        if (liveClient != null) {
            liveClient.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsMessagesStructuredSchemaOptionsAndProviderMetadata() {
        CapturingTransport transport = new CapturingTransport(successfulResponse(validSmokeOutput()));
        GenerativeAiResponse response = provider(transport)
                .generate(smokeRequest(new GenerationOptions(120, 0.2)));

        assertThat(transport.model).isEqualTo(MODEL);
        assertThat(transport.contents).hasSize(1);
        assertThat(transport.contents.get(0).role()).contains("user");
        assertThat(transport.contents.get(0).parts().orElseThrow().get(0).text())
                .contains("user content");
        assertThat(transport.config.systemInstruction().orElseThrow()
                .parts().orElseThrow().get(0).text()).contains("system content");
        assertThat(transport.config.responseMimeType()).contains("application/json");
        assertThat((JsonNode) objectMapper.valueToTree(
                transport.config.responseJsonSchema().orElseThrow()))
                .isEqualTo(smokeSchema().jsonSchema());
        assertThat(transport.config.maxOutputTokens()).contains(120);
        assertThat(transport.config.temperature()).contains(0.2f);
        assertThat(transport.config.tools()).isEmpty();

        assertThat(response.provider().provider()).isEqualTo("gemini");
        assertThat(response.provider().model()).isEqualTo("gemini-effective-version");
        assertThat(response.provider().providerRequestId()).isEqualTo("response-123");
        assertThat(response.structuredOutput().path("status").asText()).isEqualTo("OK");
        assertThat(response.rawProviderResponse().path("responseId").asText()).isEqualTo("response-123");
        assertThat(response.tokenUsage().inputTokens()).isEqualTo(10L);
        assertThat(response.tokenUsage().outputTokens()).isEqualTo(8L);
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(21L);
        assertThat(response.tokenUsage().providerDetails().path("thoughtsTokenCount").asInt()).isEqualTo(3);
        assertThat(response.finishReason()).isEqualTo("STOP");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mapsOriginalPdfAsInlineDataAfterUserInstructions() {
        byte[] pdf = "%PDF-original-table".getBytes(StandardCharsets.US_ASCII);
        CapturingTransport transport = new CapturingTransport(successfulResponse(validSmokeOutput()));

        GeminiGenerativeAiProvider provider = provider(transport);
        provider.generate(pdfRequest(new GenerationOptions(120, null), pdf));

        assertThat(provider.supportsAttachment("application/pdf")).isTrue();
        assertThat(transport.contents).singleElement().satisfies(content -> {
            assertThat(content.parts().orElseThrow()).hasSize(2);
            assertThat(content.parts().orElseThrow().get(0).text()).contains("user content");
            try {
                JsonNode binaryPart = objectMapper.readTree(
                        content.parts().orElseThrow().get(1).toJson());
                assertThat(binaryPart.path("inlineData").path("mimeType").asText())
                        .isEqualTo("application/pdf");
                assertThat(binaryPart.path("inlineData").path("data").asText())
                        .isEqualTo(Base64.getEncoder().encodeToString(pdf));
            } catch (JsonProcessingException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void omitsNullGenerationOptionsAndAcceptsPartialMetadata() {
        CapturingTransport transport = new CapturingTransport(responseWithoutOptionalMetadata(validSmokeOutput()));

        GenerativeAiResponse response = provider(transport).generate(smokeRequest(GenerationOptions.defaults()));

        assertThat(transport.config.maxOutputTokens()).isEmpty();
        assertThat(transport.config.temperature()).isEmpty();
        assertThat(response.provider().model()).isEqualTo(MODEL);
        assertThat(response.provider().providerRequestId()).isNull();
        assertThat(response.tokenUsage().inputTokens()).isNull();
        assertThat(response.tokenUsage().providerDetails()).isNull();
        assertThat(response.finishReason()).isEqualTo("FINISH_REASON_UNSPECIFIED");
    }

    @Test
    void canonicalV3ResponsePassesSchemaAndDomainReader() throws Exception {
        String output = validV3Output();
        StructuredOutputDefinition definition = schemas.load("analysis-result", "3.0");
        GenerativeAiRequest request = request(definition, GenerationOptions.defaults());

        GenerativeAiResponse response = provider(new CapturingTransport(successfulResponse(output)))
                .generate(request);

        validator.validate(definition, response.structuredOutput(), "gemini");
        assertThat(new AnalysisResultReader(objectMapper).readCurrent(response.structuredOutput()))
                .isInstanceOf(AnalysisResultV3.class);
    }

    @Test
    void rejectsMalformedSchemaInvalidAndDomainInvalidOutputs() throws Exception {
        assertInvalidResponse("{not-json", smokeRequest(GenerationOptions.defaults()));
        assertInvalidResponse("""
                {"schemaVersion":"provider-smoke-result/1.0","status":"OK","message":"ready","extra":true}
                """, smokeRequest(GenerationOptions.defaults()));

        JsonNode domainInvalid = objectMapper.readTree(validV3Output());
        ((com.fasterxml.jackson.databind.node.ObjectNode) domainInvalid.path("traditionalKnowledgeAccess"))
                .put("assessment", "INSUFFICIENT_INFORMATION");
        GenerativeAiResponse response = provider(new CapturingTransport(successfulResponse(domainInvalid.toString())))
                .generate(request(schemas.load("analysis-result", "3.0"), GenerationOptions.defaults()));
        assertThatThrownBy(() -> new AnalysisResultReader(objectMapper).readCurrent(response.structuredOutput()))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsMissingMultipleAmbiguousBlockedAndIncompleteCandidates() {
        assertInvalidResponse(response("{\"candidates\":[]}"));
        assertInvalidResponse(response("""
                {"candidates":[
                  {"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"},
                  {"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}
                ]}
                """.formatted(jsonEscaped(validSmokeOutput()), jsonEscaped(validSmokeOutput()))));
        assertInvalidResponse(response("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"%s"},{"text":"%s"}]},"finishReason":"STOP"}]}
                """.formatted(jsonEscaped(validSmokeOutput()), jsonEscaped(validSmokeOutput()))));
        assertInvalidResponse(response("""
                {"promptFeedback":{"blockReason":"SAFETY"},"candidates":[]}
                """));
        assertInvalidResponse(response("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},
                  "finishReason":"MAX_TOKENS"}]}
                """.formatted(jsonEscaped(validSmokeOutput()))));
        assertInvalidResponse(response("""
                {"candidates":[{"content":{"role":"model","parts":[]},"finishReason":"STOP"}]}
                """));
    }

    @Test
    void capturesSafeMetadataWithoutAcceptingMaxTokensResponse() {
        AtomicReference<GeminiSafeResponseMetadata> captured = new AtomicReference<>();
        GenerateContentResponse incomplete = response("""
                {
                  "candidates":[{
                    "content":{"role":"model","parts":[{"text":"partial output"}]},
                    "finishReason":"MAX_TOKENS",
                    "safetyRatings":[{"blocked":false}]
                  }],
                  "usageMetadata":{
                    "promptTokenCount":12,
                    "candidatesTokenCount":200,
                    "thoughtsTokenCount":190,
                    "totalTokenCount":212
                  }
                }
                """);
        GeminiGenerativeAiProvider provider = new GeminiGenerativeAiProvider(
                new CapturingTransport(incomplete),
                MODEL,
                objectMapper,
                validator,
                captured::set);

        assertThatThrownBy(() -> provider.generate(smokeRequest(new GenerationOptions(200, null))))
                .isInstanceOfSatisfying(GenerativeAiException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(GenerativeAiErrorType.INVALID_RESPONSE);
                    assertThat(exception.retryable()).isFalse();
                });

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().finishReasons()).containsExactly("MAX_TOKENS");
        assertThat(captured.get().blockReason()).isEqualTo("NONE");
        assertThat(captured.get().candidateCount()).isEqualTo(1);
        assertThat(captured.get().partCounts()).containsExactly(1);
        assertThat(captured.get().promptBlocked()).isFalse();
        assertThat(captured.get().candidateBlocked()).containsExactly(false);
        assertThat(captured.get().promptTokenCount()).isEqualTo(12);
        assertThat(captured.get().candidatesTokenCount()).isEqualTo(200);
        assertThat(captured.get().thoughtsTokenCount()).isEqualTo(190);
        assertThat(captured.get().totalTokenCount()).isEqualTo(212);
        assertThat(captured.get().toString()).doesNotContain("partial output", "user content", "system content");
    }

    @Test
    void allowsThoughtPartButRejectsUnexpectedNonTextPart() {
        GenerateContentResponse withThought = response("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"internal","thought":true},
                  {"text":"%s"}]},"finishReason":"STOP"}]}
                """.formatted(jsonEscaped(validSmokeOutput())));
        assertThat(provider(new CapturingTransport(withThought))
                .generate(smokeRequest(GenerationOptions.defaults())).structuredOutput().path("status").asText())
                .isEqualTo("OK");

        assertInvalidResponse(response("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"%s"},{"inlineData":{"mimeType":"text/plain","data":"eA=="}}]},
                  "finishReason":"STOP"}]}
                """.formatted(jsonEscaped(validSmokeOutput()))));
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_REQUEST, false",
            "401, AUTHENTICATION, false",
            "403, AUTHENTICATION, false",
            "404, INVALID_REQUEST, false",
            "408, TIMEOUT, true",
            "422, INVALID_REQUEST, false",
            "429, RATE_LIMITED, true",
            "500, PROVIDER_UNAVAILABLE, true",
            "503, PROVIDER_UNAVAILABLE, true"
    })
    void mapsHttpErrorsWithoutLeakingProviderOrPromptContent(
            int status,
            GenerativeAiErrorType expectedType,
            boolean retryable) {
        RuntimeException sdkError = status >= 500
                ? new ServerException(status, "TEST", "sensitive provider body")
                : new ClientException(status, "TEST", "sensitive provider body");
        assertMappedError(() -> {
            throw sdkError;
        }, expectedType, retryable);
    }

    @Test
    void mapsTimeoutConnectionAndUnknownSdkFailures() {
        assertMappedError(() -> {
            throw new GenAiIOException("secret", new SocketTimeoutException("secret timeout"));
        }, GenerativeAiErrorType.TIMEOUT, true);
        assertMappedError(() -> {
            throw new GenAiIOException("secret", new ConnectException("private host"));
        }, GenerativeAiErrorType.PROVIDER_UNAVAILABLE, true);
        assertMappedError(() -> {
            throw new IllegalStateException("sensitive SDK state");
        }, GenerativeAiErrorType.PROVIDER_ERROR, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void realSdkUsesVertexV1StructuredRequestAndMakesOneAttemptOnRetryableStatus(int status) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, status, """
                    {"error":{"code":%d,"status":"TEST_ERROR","message":"sensitive provider detail"}}
                    """.formatted(status));
        });
        server.start();

        GeminiProperties properties = new GeminiProperties(
                "test-project", "global", MODEL, "v1", Duration.ofSeconds(2));
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(
                "test-token-not-secret", Date.from(Instant.now().plusSeconds(3600))));
        liveClient = GeminiConfiguration.createClient(properties, credentials,
                "http://127.0.0.1:" + server.getAddress().getPort());
        GeminiGenerativeAiProvider sdkProvider = provider(new GoogleGenAiGeminiTransport(liveClient));

        byte[] pdf = "%PDF-sdk-original".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> sdkProvider.generate(pdfRequest(new GenerationOptions(120, 0.2), pdf)))
                .isInstanceOfSatisfying(GenerativeAiException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(status == 429
                            ? GenerativeAiErrorType.RATE_LIMITED
                            : GenerativeAiErrorType.PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("sensitive provider detail", "user content");
                });

        assertThat(requestCount).hasValue(1);
        assertThat(requestPath.get()).contains("/v1/projects/test-project/locations/global/")
                .endsWith("/publishers/google/models/test-model:generateContent");
        JsonNode sent = objectMapper.readTree(requestBody.get());
        assertThat(sent.path("systemInstruction").path("parts").get(0).path("text").asText())
                .isEqualTo("system content");
        assertThat(sent.path("contents").get(0).path("role").asText()).isEqualTo("user");
        assertThat(sent.path("contents").get(0).path("parts").get(0).path("text").asText())
                .isEqualTo("user content");
        assertThat(sent.path("contents").get(0).path("parts").get(1)
                .path("inlineData").path("mimeType").asText()).isEqualTo("application/pdf");
        assertThat(sent.path("contents").get(0).path("parts").get(1)
                .path("inlineData").path("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pdf));
        assertThat(sent.path("generationConfig").path("responseMimeType").asText())
                .isEqualTo("application/json");
        assertThat(sent.path("generationConfig").path("responseJsonSchema"))
                .isEqualTo(smokeSchema().jsonSchema());
    }

    @Test
    void realSdkMakesOneAttemptWhenTransportTimesOut() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            try {
                Thread.sleep(1000);
                reply(exchange, 200, successfulResponse(validSmokeOutput()).toJson());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client has already closed the timed-out exchange.
            }
        });
        server.start();

        GeminiProperties properties = new GeminiProperties(
                "test-project", "global", MODEL, "v1", Duration.ofMillis(200));
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(
                "test-token-not-secret", Date.from(Instant.now().plusSeconds(3600))));
        liveClient = GeminiConfiguration.createClient(properties, credentials,
                "http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> provider(new GoogleGenAiGeminiTransport(liveClient))
                .generate(smokeRequest(GenerationOptions.defaults())))
                .isInstanceOfSatisfying(GenerativeAiException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(GenerativeAiErrorType.TIMEOUT);
                    assertThat(exception.retryable()).isTrue();
                });
        assertThat(requestCount).hasValue(1);
    }

    private void assertMappedError(
            ThrowingTransport action,
            GenerativeAiErrorType expectedType,
            boolean retryable) {
        GeminiTransport transport = (model, contents, config) -> action.execute();
        assertThatThrownBy(() -> provider(transport).generate(smokeRequest(GenerationOptions.defaults())))
                .isExactlyInstanceOf(GenerativeAiException.class)
                .satisfies(error -> {
                    GenerativeAiException exception = (GenerativeAiException) error;
                    assertThat(exception.type()).isEqualTo(expectedType);
                    assertThat(exception.provider()).isEqualTo("gemini");
                    assertThat(exception.retryable()).isEqualTo(retryable);
                    assertThat(exception.getMessage())
                            .doesNotContain("sensitive", "private host", "user content", "system content");
                    assertThat(exception.getCause()).isNotNull();
                });
    }

    private void assertInvalidResponse(String output, GenerativeAiRequest request) {
        assertInvalidResponse(successfulResponse(output), request);
    }

    private void assertInvalidResponse(GenerateContentResponse response) {
        assertInvalidResponse(response, smokeRequest(GenerationOptions.defaults()));
    }

    private void assertInvalidResponse(GenerateContentResponse response, GenerativeAiRequest request) {
        assertThatThrownBy(() -> provider(new CapturingTransport(response)).generate(request))
                .isExactlyInstanceOf(GenerativeAiException.class)
                .satisfies(error -> {
                    GenerativeAiException exception = (GenerativeAiException) error;
                    assertThat(exception.type()).isEqualTo(GenerativeAiErrorType.INVALID_RESPONSE);
                    assertThat(exception.retryable()).isFalse();
                    assertThat(exception.getMessage()).doesNotContain("user content");
                });
    }

    private GeminiGenerativeAiProvider provider(GeminiTransport transport) {
        return new GeminiGenerativeAiProvider(transport, MODEL, objectMapper, validator);
    }

    private GenerativeAiRequest smokeRequest(GenerationOptions options) {
        return request(smokeSchema(), options);
    }

    private GenerativeAiRequest pdfRequest(GenerationOptions options, byte[] pdf) {
        GenerativeAiRequest textRequest = smokeRequest(options);
        return new GenerativeAiRequest(textRequest.prompt(), textRequest.output(), textRequest.options(),
                List.of(new AiAttachment("application/pdf", pdf)));
    }

    private GenerativeAiRequest request(StructuredOutputDefinition output, GenerationOptions options) {
        RenderedPrompt prompt = new RenderedPrompt(
                new PromptReference("provider-smoke-test", "1.0"),
                List.of(
                        new AiMessage(AiMessageRole.SYSTEM, "system content"),
                        new AiMessage(AiMessageRole.USER, "user content")),
                HASH,
                HASH,
                "[SYSTEM]\nsystem content\n\n[USER]\nuser content");
        return new GenerativeAiRequest(prompt, output, options);
    }

    private StructuredOutputDefinition smokeSchema() {
        return schemas.load("provider-smoke-result", "1.0");
    }

    private GenerateContentResponse successfulResponse(String output) {
        return response("""
                {
                  "responseId":"response-123",
                  "modelVersion":"gemini-effective-version",
                  "candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},
                    "finishReason":"STOP"}],
                  "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":8,
                    "thoughtsTokenCount":3,"totalTokenCount":21}
                }
                """.formatted(jsonEscaped(output)));
    }

    private GenerateContentResponse responseWithoutOptionalMetadata(String output) {
        return response("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]}}]}
                """.formatted(jsonEscaped(output)));
    }

    private static GenerateContentResponse response(String json) {
        return GenerateContentResponse.fromJson(json);
    }

    private String jsonEscaped(String value) {
        try {
            String quoted = objectMapper.writeValueAsString(value);
            return quoted.substring(1, quoted.length() - 1);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String validSmokeOutput() {
        return """
                {"schemaVersion":"provider-smoke-result/1.0","status":"OK","message":"ready"}
                """.trim();
    }

    private static String validV3Output() {
        return """
                {
                  "schemaVersion":"analysis-result/3.0",
                  "summary":"El software expresado podría protegerse por derecho de autor.",
                  "protectionOptions":[{
                    "type":"COPYRIGHT","applicability":"LIKELY",
                    "protectedSubjectMatter":"El código fuente y su expresión",
                    "rationale":"La descripción incluye un programa de ordenador.",
                    "legalBasis":[{"instrument":"DECISION_351","articles":["1","3","4"]}]
                  }],
                  "patentScreening":{
                    "applicable":true,
                    "article15":{"assessment":"POTENTIAL_MATCH","rationale":"Software como tal.",
                      "matches":[{"literal":"e","rationale":"La materia descrita es software."}]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"No hay indicios.","matches":[]}
                  },
                  "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No hay indicios.",
                    "missingInformation":[]},
                  "traditionalKnowledgeAccess":{"assessment":"NOT_INDICATED","rationale":"No hay indicios.",
                    "missingInformation":[]},
                  "observations":[],"warnings":[]
                }
                """;
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class CapturingTransport implements GeminiTransport {
        private final GenerateContentResponse response;
        private String model;
        private List<Content> contents;
        private GenerateContentConfig config;

        private CapturingTransport(GenerateContentResponse response) {
            this.response = response;
        }

        @Override
        public GenerateContentResponse generateContent(
                String model,
                List<Content> contents,
                GenerateContentConfig config) {
            this.model = model;
            this.contents = contents;
            this.config = config;
            return response;
        }
    }

    @FunctionalInterface
    private interface ThrowingTransport {
        GenerateContentResponse execute();
    }
}
