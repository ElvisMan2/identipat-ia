package com.mnk.identipatia.ai.provider.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.mnk.identipatia.ai.model.GenerationOptions;
import com.mnk.identipatia.ai.model.AiAttachment;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.prompt.PromptRegistry;
import com.mnk.identipatia.ai.prompt.PromptRenderer;
import com.mnk.identipatia.ai.schema.OutputSchemaRegistry;
import com.mnk.identipatia.ai.schema.StructuredOutputValidator;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.v3.AnalysisResultV3;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GeminiLiveSmokeIT {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveSmokeIT.class);
    private static final int TECHNICAL_MAX_OUTPUT_TOKENS = 200;
    private static final int LEGAL_MAX_OUTPUT_TOKENS = 6000;

    @Test
    void callsVertexAiWithSyntheticTechnicalSchema() {
        SmokeContext context = context();
        try (Client client = GeminiConfiguration.createClient(context.properties(), null, null)) {
            GeminiGenerativeAiProvider provider = provider(
                    client, context.objectMapper(), TECHNICAL_MAX_OUTPUT_TOKENS);
            GenerativeAiResponse response = provider.generate(new GenerativeAiRequest(
                    context.renderer().render(
                            new PromptReference("provider-smoke-test", "1.0"),
                            Map.of("TEST_LABEL", "gemini-manual-live-smoke")),
                    context.schemas().load("provider-smoke-result", "1.0"),
                    new GenerationOptions(TECHNICAL_MAX_OUTPUT_TOKENS, null)));

            assertThat(response.structuredOutput().path("schemaVersion").asText())
                    .isEqualTo("provider-smoke-result/1.0");
            assertThat(response.structuredOutput().path("status").asText()).isEqualTo("OK");
            assertThat(response.provider().provider()).isEqualTo("gemini");
            assertThat(response.provider().model()).isNotBlank();
        }
    }

    @Test
    void callsVertexAiWithSyntheticLegalPromptAndV3Schema() throws Exception {
        SmokeContext context = context();
        try (Client client = GeminiConfiguration.createClient(context.properties(), null, null)) {
            GeminiGenerativeAiProvider provider = provider(
                    client, context.objectMapper(), LEGAL_MAX_OUTPUT_TOKENS);
            GenerativeAiResponse response = provider.generate(new GenerativeAiRequest(
                    context.renderer().render(
                            new PromptReference("intellectual-property-analysis", "0.3.1"),
                            Map.of("USER_DESCRIPTION", "Aplicación sintética que organiza notas locales sin datos personales.")),
                    context.schemas().load("analysis-result", "3.0"),
                    new GenerationOptions(LEGAL_MAX_OUTPUT_TOKENS, null)));

            assertThat(new AnalysisResultReader(context.objectMapper())
                    .readCurrent(response.structuredOutput())).isInstanceOf(AnalysisResultV3.class);
            assertThat(response.provider().provider()).isEqualTo("gemini");
        }
    }

    @Test
    void callsVertexAiWithSyntheticPdfAndV3Schema() throws Exception {
        SmokeContext context = context();
        byte[] pdf = syntheticPdf();
        try (Client client = GeminiConfiguration.createClient(context.properties(), null, null)) {
            GeminiGenerativeAiProvider provider = provider(
                    client, context.objectMapper(), LEGAL_MAX_OUTPUT_TOKENS);
            GenerativeAiResponse response = provider.generate(new GenerativeAiRequest(
                    context.renderer().render(
                            new PromptReference("intellectual-property-analysis", "0.3.2"), Map.of()),
                    context.schemas().load("analysis-result", "3.0"),
                    new GenerationOptions(LEGAL_MAX_OUTPUT_TOKENS, null),
                    List.of(new AiAttachment("application/pdf", pdf))));

            assertThat(new AnalysisResultReader(context.objectMapper())
                    .readCurrent(response.structuredOutput())).isInstanceOf(AnalysisResultV3.class);
            assertThat(response.provider().provider()).isEqualTo("gemini");
            assertThat(response.tokenUsage().totalTokens()).isPositive();
        }
    }

    private static byte[] syntheticPdf() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(72, 720);
                content.showText("Prototipo sintetico: sensor | alerta | panel local sin datos personales.");
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private SmokeContext context() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_GEMINI_LIVE_SMOKE")),
                "RUN_GEMINI_LIVE_SMOKE=true is required for this paid, manual test");
        String project = required("GOOGLE_CLOUD_PROJECT");
        String location = required("GOOGLE_CLOUD_LOCATION");
        String model = required("GEMINI_MODEL");
        String apiVersion = valueOrDefault("GEMINI_API_VERSION", "v1");
        Duration timeout = DurationStyle.detectAndParse(valueOrDefault("GEMINI_TIMEOUT", "60s"));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new SmokeContext(
                new GeminiProperties(project, location, model, apiVersion, timeout),
                objectMapper,
                new PromptRenderer(new PromptRegistry()),
                new OutputSchemaRegistry(objectMapper));
    }

    private GeminiGenerativeAiProvider provider(
            Client client,
            ObjectMapper objectMapper,
            int configuredMaxOutputTokens) {
        GeminiResponseDiagnostics diagnostics = "true".equalsIgnoreCase(
                System.getenv("GEMINI_SAFE_DIAGNOSTICS"))
                ? metadata -> logSafeDiagnostics(configuredMaxOutputTokens, metadata)
                : GeminiResponseDiagnostics.disabled();
        return new GeminiGenerativeAiProvider(
                new GoogleGenAiGeminiTransport(client),
                required("GEMINI_MODEL"),
                objectMapper,
                new StructuredOutputValidator(),
                diagnostics);
    }

    private static void logSafeDiagnostics(
            int configuredMaxOutputTokens,
            GeminiSafeResponseMetadata metadata) {
        log.info(
                "Gemini safe diagnostics configuredMaxOutputTokens={} finishReasons={} "
                        + "blockReason={} candidateCount={} "
                        + "partCounts={} promptBlocked={} candidateBlocked={} promptTokens={} "
                        + "candidateTokens={} thoughtTokens={} totalTokens={}",
                configuredMaxOutputTokens,
                metadata.finishReasons(),
                metadata.blockReason(),
                metadata.candidateCount(),
                metadata.partCounts(),
                metadata.promptBlocked(),
                metadata.candidateBlocked(),
                metadata.promptTokenCount(),
                metadata.candidatesTokenCount(),
                metadata.thoughtsTokenCount(),
                metadata.totalTokenCount());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), name + " is required for the manual live smoke test");
        return value;
    }

    private static String valueOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SmokeContext(
            GeminiProperties properties,
            ObjectMapper objectMapper,
            PromptRenderer renderer,
            OutputSchemaRegistry schemas) {
    }
}
