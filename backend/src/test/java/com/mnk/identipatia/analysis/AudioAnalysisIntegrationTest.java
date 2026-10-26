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
import com.mnk.identipatia.preprocessing.client.AudioTranscriptionResponse;
import com.mnk.identipatia.preprocessing.client.PreprocessingAudioException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
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
        "app.analysis.audio.max-file-size-bytes=16",
        "app.analysis.audio.max-duration-seconds=180"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AudioAnalysisIntegrationTest {
    private static final byte[] VALID_AUDIO = "audio-data".getBytes(StandardCharsets.US_ASCII);
    private static final String TRANSCRIPT =
            "  Sistema de riego inteligente con sensores y válvulas independientes.  ";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_audio_analysis_test");

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
        when(preprocessingClient.transcribeAudio(any(byte[].class), anyString()))
                .thenReturn(transcription(TRANSCRIPT, 42.31));
    }

    @Test
    void standardAudioPersistsSafeMetadataAndUsesExistingWorkerPipeline() throws Exception {
        Browser browser = browser(true);

        MvcResult created = postAudio(browser, audio(VALID_AUDIO, "video/mp4;codecs=mp4a.40.2"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/identipat-ia/analyses/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();
        UUID analysisId = id(created);

        verify(preprocessingClient).transcribeAudio(VALID_AUDIO, "video/mp4");
        Analysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        AnalysisInput input = inputRepository.findById(analysisId).orElseThrow();
        assertThat(analysis.getInputType()).isEqualTo(AnalysisInputType.AUDIO);
        assertThat(input.getOriginalText()).isEqualTo(TRANSCRIPT);
        assertThat(input.getProcessedText())
                .isEqualTo("Sistema de riego inteligente con sensores y válvulas independientes.");
        JsonNode metadata = input.getSourceMetadata();
        assertThat(metadata.path("mediaType").asText()).isEqualTo("video/mp4");
        assertThat(metadata.path("sizeBytes").asLong()).isEqualTo(VALID_AUDIO.length);
        assertThat(metadata.path("durationSeconds").asDouble()).isEqualTo(42.31);
        assertThat(metadata.path("characterCount").asInt())
                .isEqualTo(TRANSCRIPT.codePointCount(0, TRANSCRIPT.length()));
        assertThat(metadata.path("language").asText()).isEqualTo("es");
        assertThat(metadata.path("transcriptionMethod").asText()).isEqualTo("FASTER_WHISPER");
        assertThat(metadata.path("model").asText()).isEqualTo("base");
        assertThat(metadata.path("sha256").asText()).isEqualTo(sha256(VALID_AUDIO));
        assertThat(metadata.has("filename")).isFalse();
        assertThat(metadata.toString()).doesNotContain("audio-data");

        mockMvc.perform(get("/analyses/{analysisId}", analysisId).cookie(browser.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputType").value("AUDIO"));

        when(provider.generate(any())).thenReturn(successResponse());
        assertThat(worker.processOneSynchronously()).isTrue();
        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo(AnalysisStatus.COMPLETED);
        verify(provider).generate(any());
    }

    @Test
    void standardRequiresSessionConsentAndCsrfBeforeTranscription() throws Exception {
        Browser accepted = browser(true);
        mockMvc.perform(multipart("/analyses/audio")
                        .file(audio(VALID_AUDIO, "audio/wav"))
                        .cookie(accepted.session()))
                .andExpect(status().isForbidden());

        Browser withoutConsent = browser(false);
        postAudio(withoutConsent, audio(VALID_AUDIO, "audio/wav"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        Csrf csrf = csrf();
        mockMvc.perform(multipart("/analyses/audio")
                        .file(audio(VALID_AUDIO, "audio/wav"))
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
        verify(preprocessingClient, never()).transcribeAudio(any(), anyString());
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void adminUsesBearerWithoutCsrfAndInvalidBearerNeverFallsBack() throws Exception {
        User admin = user("ADMIN", "A");
        String token = jwtService.generateToken(admin.getDoi());

        MvcResult created = mockMvc.perform(multipart("/analyses/audio")
                        .file(audio(VALID_AUDIO, "audio/mpeg"))
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
                .andExpect(jsonPath("$.inputType").value("AUDIO"));

        Browser standard = browser(true);
        mockMvc.perform(multipart("/analyses/audio")
                        .file(audio(VALID_AUDIO, "audio/mpeg"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .cookie(standard.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_AUTHENTICATION_REQUIRED"));
        assertThat(analysisRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "audio/webm",
            "audio/mpeg",
            "audio/mp4",
            "video/mp4",
            "audio/x-m4a",
            "audio/3gpp",
            "video/3gpp",
            "audio/wav",
            "audio/x-wav",
            "audio/ogg",
            "audio/flac"
    })
    void acceptsSupportedMediaTypes(String mediaType) throws Exception {
        Browser browser = browser(true);

        postAudio(browser, audio(VALID_AUDIO, mediaType))
                .andExpect(status().isAccepted());

        verify(preprocessingClient).transcribeAudio(VALID_AUDIO, mediaType);
    }

    @Test
    void validatesMimeEmptyAndSizeBeforeTranscription() throws Exception {
        Browser browser = browser(true);
        postAudio(browser, audio(VALID_AUDIO, MediaType.TEXT_PLAIN_VALUE))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_AUDIO_MEDIA_TYPE"));
        postAudio(browser, audio(VALID_AUDIO, "video/avi"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_AUDIO_MEDIA_TYPE"));
        postAudio(browser, audio(new byte[0], "audio/wav"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_AUDIO"));
        postAudio(browser, audio("x".repeat(17).getBytes(StandardCharsets.US_ASCII), "audio/wav"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AUDIO_TOO_LARGE"));
        verify(preprocessingClient, never()).transcribeAudio(any(), anyString());
        assertThat(analysisRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_AUDIO,INVALID_AUDIO,422",
            "AUDIO_DURATION_LIMIT_EXCEEDED,AUDIO_DURATION_LIMIT_EXCEEDED,422",
            "AUDIO_TRANSCRIPTION_EMPTY,AUDIO_TRANSCRIPTION_EMPTY,422",
            "AUDIO_TOO_LARGE,AUDIO_TOO_LARGE,413",
            "UNSUPPORTED_AUDIO_MEDIA_TYPE,UNSUPPORTED_AUDIO_MEDIA_TYPE,415",
            "AUDIO_MODEL_NOT_READY,AUDIO_TRANSCRIPTION_UNAVAILABLE,503",
            "AUDIO_TRANSCRIPTION_ERROR,AUDIO_TRANSCRIPTION_UNAVAILABLE,503"
    })
    void mapsControlledTranscriptionErrorsWithoutPersisting(
            String internalCode, String publicCode, int statusCode) throws Exception {
        Browser browser = browser(true);
        when(preprocessingClient.transcribeAudio(any(), anyString()))
                .thenThrow(new PreprocessingAudioException(internalCode));

        postAudio(browser, audio(VALID_AUDIO, "audio/ogg"))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(publicCode));
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void validatesDurationAndTranscriptLengthWithoutPersisting() throws Exception {
        Browser browser = browser(true);
        when(preprocessingClient.transcribeAudio(any(), anyString()))
                .thenReturn(transcription(TRANSCRIPT, 180.01));
        postAudio(browser, audio(VALID_AUDIO, "audio/wav"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUDIO_DURATION_LIMIT_EXCEEDED"));

        when(preprocessingClient.transcribeAudio(any(), anyString()))
                .thenReturn(transcription("muy corto", 1));
        postAudio(browser, audio(VALID_AUDIO, "audio/wav"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUDIO_TRANSCRIPTION_EMPTY"));

        String tooLong = "x".repeat(20_001);
        when(preprocessingClient.transcribeAudio(any(), anyString()))
                .thenReturn(transcription(tooLong, 1));
        postAudio(browser, audio(VALID_AUDIO, "audio/wav"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUDIO_TEXT_TOO_LONG"));
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void transcriptionOrAiUnavailableReturns503AndDoesNotPersist() throws Exception {
        Browser browser = browser(true);
        when(preprocessingClient.transcribeAudio(any(), anyString()))
                .thenThrow(new PreprocessingServiceUnavailableException("timeout or 5xx"));
        postAudio(browser, audio(VALID_AUDIO, "audio/flac"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUDIO_TRANSCRIPTION_UNAVAILABLE"));

        doReturn(transcription(TRANSCRIPT, 3)).when(preprocessingClient)
                .transcribeAudio(any(), anyString());
        when(aiProperties.enabled()).thenReturn(false);
        postAudio(browser, audio(VALID_AUDIO, "audio/flac"))
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
        user.setDoi("F16-" + UUID.randomUUID());
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

    private org.springframework.test.web.servlet.ResultActions postAudio(
            Browser browser, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/analyses/audio").file(file)
                .cookie(browser.session(), browser.csrf().cookie())
                .header("X-XSRF-TOKEN", browser.csrf().token()));
    }

    private MockMultipartFile audio(byte[] content, String mediaType) {
        return new MockMultipartFile("file", "sensitive-voice-name.bin", mediaType, content);
    }

    private AudioTranscriptionResponse transcription(String text, double duration) {
        return new AudioTranscriptionResponse(
                text,
                duration,
                text.codePointCount(0, text.length()),
                "es",
                "FASTER_WHISPER",
                "base");
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
                new ProviderExecution("fake", "fake-model", "response-audio"),
                structured, objectMapper.readTree("{\"response\":\"raw\"}"),
                new TokenUsage(10L, 5L, 15L, objectMapper.createObjectNode()),
                "completed", 25L);
    }

    private record Csrf(Cookie cookie, String token) {
    }

    private record Browser(Cookie session, Csrf csrf, UUID sessionId) {
    }
}
