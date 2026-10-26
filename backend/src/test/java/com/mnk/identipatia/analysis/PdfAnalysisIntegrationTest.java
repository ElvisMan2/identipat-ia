package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.config.AiProperties;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.ProviderExecution;
import com.mnk.identipatia.ai.model.TokenUsage;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInput;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.repository.AiInvocationRepository;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.worker.AnalysisWorker;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.preprocessing.client.PdfExtractionResponse;
import com.mnk.identipatia.preprocessing.client.PreprocessingPdfException;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceClient;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceUnavailableException;
import com.mnk.identipatia.repository.ConsentEventRepository;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
import com.mnk.identipatia.reporting.repository.AnalysisReportContextRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.enabled=true",
        "app.ai.provider=openai",
        "app.ai.openai.api-key=test-key-never-sent",
        "app.ai.openai.model=test-model",
        "app.analysis.worker-enabled=false",
        "app.analysis.pdf.max-file-size-bytes=64",
        "app.analysis.pdf.max-pages=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PdfAnalysisIntegrationTest {
    private static final byte[] VALID_PDF = "%PDF-valid".getBytes(StandardCharsets.US_ASCII);
    private static final String EXTRACTED_TEXT =
            "  Descripción técnica extraída del documento PDF para el análisis.  ";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_pdf_analysis_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired ConsentEventRepository consentRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired AnalysisInputRepository inputRepository;
    @Autowired AiInvocationRepository invocationRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired AnalysisReportContextRepository reportContextRepository;
    @Autowired AnalysisWorker worker;
    @Autowired JwtService jwtService;

    @MockBean GenerativeAiProvider provider;
    @MockBean PreprocessingServiceClient preprocessingClient;
    @MockBean AiProperties aiProperties;

    @BeforeEach
    void resetState() {
        reportContextRepository.deleteAll();
        resultRepository.deleteAll();
        invocationRepository.deleteAll();
        inputRepository.deleteAll();
        analysisRepository.deleteAll();
        consentRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        reset(provider, preprocessingClient, aiProperties);
        when(provider.providerId()).thenReturn("fake");
        when(aiProperties.enabled()).thenReturn(true);
        when(preprocessingClient.extractPdf(any(byte[].class))).thenReturn(extraction(EXTRACTED_TEXT, 1));
    }

    @Test
    void standardPdfPersistsMetadataAndUsesExistingWorkerPipeline() throws Exception {
        Browser browser = browser(true);

        MvcResult created = postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/identipat-ia/analyses/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();
        UUID analysisId = id(created);

        Analysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        AnalysisInput input = inputRepository.findById(analysisId).orElseThrow();
        assertThat(analysis.getInputType()).isEqualTo(AnalysisInputType.PDF);
        assertThat(input.getOriginalText()).isEqualTo(EXTRACTED_TEXT);
        assertThat(input.getProcessedText())
                .isEqualTo("Descripción técnica extraída del documento PDF para el análisis.");
        JsonNode metadata = input.getSourceMetadata();
        assertThat(metadata.path("mediaType").asText()).isEqualTo("application/pdf");
        assertThat(metadata.path("sizeBytes").asLong()).isEqualTo(VALID_PDF.length);
        assertThat(metadata.path("pageCount").asInt()).isEqualTo(1);
        assertThat(metadata.path("characterCount").asInt())
                .isEqualTo(EXTRACTED_TEXT.codePointCount(0, EXTRACTED_TEXT.length()));
        assertThat(metadata.path("extractionMethod").asText()).isEqualTo("TEXT_LAYER");
        assertThat(metadata.path("sha256").asText()).isEqualTo(sha256(VALID_PDF));
        assertThat(metadata.has("filename")).isFalse();
        assertThat(metadata.toString()).doesNotContain("%PDF-");

        getPdf(browser, analysisId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputType").value("PDF"));

        when(provider.generate(any())).thenReturn(successResponse());
        assertThat(worker.processOneSynchronously()).isTrue();
        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo(AnalysisStatus.COMPLETED);
        verify(provider).generate(any());
    }

    @Test
    void standardRequiresSessionConsentAndCsrfBeforePreprocessing() throws Exception {
        Browser accepted = browser(true);
        mockMvc.perform(multipart("/analyses/pdf")
                        .file(pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                        .cookie(accepted.session()))
                .andExpect(status().isForbidden());

        Browser withoutConsent = browser(false);
        postPdf(withoutConsent, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        Csrf csrf = csrf();
        mockMvc.perform(multipart("/analyses/pdf")
                        .file(pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
        verify(preprocessingClient, never()).extractPdf(any());
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void adminUsesBearerWithoutCsrfAndInvalidBearerNeverFallsBack() throws Exception {
        User admin = user("ADMIN", "A");
        String token = jwtService.generateToken(admin.getDoi());

        MvcResult created = mockMvc.perform(multipart("/analyses/pdf")
                        .file(pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID analysisId = id(created);
        Analysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(analysis.getUserId()).isEqualTo(admin.getUserId());
        assertThat(analysis.getSessionId()).isNull();
        assertThat(analysis.getConsentEventId()).isNull();
        mockMvc.perform(get("/analyses/{analysisId}", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputType").value("PDF"));

        Browser standard = browser(true);
        mockMvc.perform(multipart("/analyses/pdf")
                        .file(pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .cookie(standard.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_AUTHENTICATION_REQUIRED"));
        assertThat(analysisRepository.count()).isEqualTo(1);
    }

    @Test
    void validatesMimeSignatureAndSizeBeforePreprocessing() throws Exception {
        Browser browser = browser(true);
        postPdf(browser, pdf(VALID_PDF, MediaType.TEXT_PLAIN_VALUE))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PDF_MEDIA_TYPE"));
        postPdf(browser, pdf("renamed".getBytes(StandardCharsets.US_ASCII),
                MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PDF"));
        postPdf(browser, pdf(("%PDF-" + "x".repeat(60)).getBytes(StandardCharsets.US_ASCII),
                MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PDF_TOO_LARGE"));
        verify(preprocessingClient, never()).extractPdf(any());
        assertThat(analysisRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_PDF,INVALID_PDF",
            "PDF_ENCRYPTED,PDF_ENCRYPTED_NOT_SUPPORTED",
            "PDF_PAGE_LIMIT_EXCEEDED,PDF_PAGE_LIMIT_EXCEEDED",
            "PDF_TEXT_NOT_EXTRACTABLE,PDF_TEXT_NOT_EXTRACTABLE"
    })
    void mapsControlledPreprocessingErrorsAndDoesNotPersist(String internalCode, String publicCode)
            throws Exception {
        Browser browser = browser(true);
        when(preprocessingClient.extractPdf(any(byte[].class)))
                .thenThrow(new PreprocessingPdfException(internalCode));

        postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(publicCode));
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void validatesPageAndTextLimitsFromExtractionResult() throws Exception {
        Browser browser = browser(true);
        when(preprocessingClient.extractPdf(any(byte[].class))).thenReturn(extraction(EXTRACTED_TEXT, 3));
        postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PDF_PAGE_LIMIT_EXCEEDED"));

        when(preprocessingClient.extractPdf(any(byte[].class))).thenReturn(extraction("muy corto", 1));
        postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PDF_TEXT_NOT_EXTRACTABLE"));

        String tooLong = "x".repeat(20_001);
        when(preprocessingClient.extractPdf(any(byte[].class))).thenReturn(extraction(tooLong, 1));
        postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PDF_TEXT_TOO_LONG"));
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void serviceOrAiUnavailableReturns503AndDoesNotPersist() throws Exception {
        Browser browser = browser(true);
        when(preprocessingClient.extractPdf(any(byte[].class)))
                .thenThrow(new PreprocessingServiceUnavailableException("timeout"));
        postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PREPROCESSING_SERVICE_UNAVAILABLE"));

        when(preprocessingClient.extractPdf(any(byte[].class))).thenReturn(extraction(EXTRACTED_TEXT, 1));
        when(aiProperties.enabled()).thenReturn(false);
        postPdf(browser, pdf(VALID_PDF, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYSIS_AI_UNAVAILABLE"));
        assertThat(analysisRepository.count()).isZero();
    }

    private Browser browser(boolean acceptConsent) throws Exception {
        User user = user("STANDARD", "A");
        Csrf csrf = csrf();
        MvcResult sessionResult = mockMvc.perform(post("/standard-sessions")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"" + user.getDoi() + "\",\"doiType\":\"DNI\"}"))
                .andExpect(status().isCreated()).andReturn();
        Cookie sessionCookie = sessionResult.getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        StandardSession session = sessionRepository.findAll().stream()
                .filter(value -> value.getUser().getUserId().equals(user.getUserId()))
                .findFirst().orElseThrow();
        if (acceptConsent) {
            mockMvc.perform(post("/standard-session/consent").cookie(sessionCookie, csrf.cookie())
                            .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"consentVersion\":\"personal-data/1.0\",\"decision\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk());
        }
        return new Browser(sessionCookie, csrf, session.getSessionId());
    }

    private User user(String type, String status) {
        User user = new User();
        user.setDoi("F15-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType(type);
        user.setStatus(status);
        user.setPassword("unused-for-direct-jwt-test");
        return userRepository.saveAndFlush(user);
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/standard-session/csrf"))
                .andExpect(status().isOk()).andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"), json(result).path("token").asText());
    }

    private org.springframework.test.web.servlet.ResultActions postPdf(Browser browser, MockMultipartFile file)
            throws Exception {
        return mockMvc.perform(multipart("/analyses/pdf").file(file)
                .cookie(browser.session(), browser.csrf().cookie())
                .header("X-XSRF-TOKEN", browser.csrf().token()));
    }

    private org.springframework.test.web.servlet.ResultActions getPdf(Browser browser, UUID analysisId)
            throws Exception {
        return mockMvc.perform(get("/analyses/{analysisId}", analysisId).cookie(browser.session()));
    }

    private MockMultipartFile pdf(byte[] content, String mediaType) {
        return new MockMultipartFile("file", "sensitive-name.pdf", mediaType, content);
    }

    private PdfExtractionResponse extraction(String text, int pages) {
        return new PdfExtractionResponse(text, pages, text.codePointCount(0, text.length()), "TEXT_LAYER");
    }

    private UUID id(MvcResult result) throws Exception {
        return UUID.fromString(json(result).path("analysisId").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private GenerativeAiResponse successResponse() throws Exception {
        JsonNode structured = objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/2.0",
                  "summary":"La descripción presenta una posible solución tecnológica.",
                  "protectionOptions":[{
                    "type":"INVENTION_PATENT",
                    "applicability":"POSSIBLE",
                    "protectedSubjectMatter":"El mecanismo descrito",
                    "rationale":"La descripción plantea características técnicas.",
                    "legalBasis":[{"instrument":"DECISION_486","articles":["14"]}]
                  }],
                  "patentScreening":{
                    "applicable":true,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}
                  },
                  "geneticResourceAccess":{
                    "assessment":"NOT_INDICATED",
                    "rationale":"No se mencionan recursos genéticos.",
                    "missingInformation":[]
                  },
                  "observations":[],
                  "warnings":["No se realizó una búsqueda de antecedentes."]
                }
                """);
        return new GenerativeAiResponse(
                new ProviderExecution("fake", "fake-model", "response-pdf"),
                structured, objectMapper.readTree("{\"response\":\"raw\"}"),
                new TokenUsage(10L, 5L, 15L, objectMapper.createObjectNode()),
                "completed", 25L);
    }

    private record Csrf(Cookie cookie, String token) {
    }

    private record Browser(Cookie session, Csrf csrf, UUID sessionId) {
    }
}
