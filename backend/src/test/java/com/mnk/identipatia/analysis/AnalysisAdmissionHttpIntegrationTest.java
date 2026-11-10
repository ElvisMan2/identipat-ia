package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.TestLegalDocumentFixture;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentHistoryRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentRepository;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.preprocessing.client.AudioTranscriptionResponse;
import com.mnk.identipatia.preprocessing.client.PdfExtractionResponse;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceClient;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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
        "app.analysis.admission.enabled=true",
        "app.analysis.admission.standard-max-per-hour=1",
        "app.analysis.admission.standard-max-per-24-hours=1",
        "app.analysis.admission.standard-audio-max-per-24-hours=1",
        "app.analysis.admission.global-max-active=2",
        "app.analysis.admission.audio-max-preprocessing=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AnalysisAdmissionHttpIntegrationTest {

    private static final byte[] PDF = "%PDF-test-content".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AUDIO = {1, 2, 3, 4};
    private static final String PDF_TEXT = "Descripción técnica extraída de un documento PDF válido.";
    private static final String AUDIO_TEXT = "Descripción técnica transcrita desde un audio válido.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_admission_http_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired AnalysisInputRepository inputRepository;
    @Autowired LegalDocumentRepository legalDocumentRepository;
    @Autowired LegalDocumentHistoryRepository legalHistoryRepository;
    @Autowired JwtService jwtService;

    @MockBean GenerativeAiProvider provider;
    @MockBean PreprocessingServiceClient preprocessingClient;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM analysis_admissions");
        jdbc.update("DELETE FROM analysis_inputs");
        jdbc.update("DELETE FROM analyses");
        jdbc.update("DELETE FROM user_terms_acceptances");
        jdbc.update("DELETE FROM consent_events");
        jdbc.update("DELETE FROM standard_sessions");
        jdbc.update("DELETE FROM users");
        TestLegalDocumentFixture.ensureCurrent(legalDocumentRepository, legalHistoryRepository);
        reset(provider, preprocessingClient);
        when(provider.providerId()).thenReturn("fake");
    }

    @Test
    void standardTextHttpFlowPersistsAcceptedAdmission() throws Exception {
        Browser browser = acceptedBrowser();

        MvcResult result = postText(browser,
                "Descripción técnica suficientemente extensa para una admisión STANDARD.")
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/identipat-ia/analyses/[0-9a-f-]+")))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();

        UUID analysisId = analysisId(result);
        assertThat(analysisRepository.findById(analysisId)).isPresent();
        assertThat(inputRepository.findById(analysisId)).isPresent();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM analysis_admissions
                WHERE analysis_id = ? AND user_id = ? AND input_type = 'TEXT'
                  AND actor_mode = 'STANDARD_SESSION' AND state = 'ACCEPTED'
                """, Integer.class, analysisId, browser.userId())).isEqualTo(1);
        verify(provider, never()).generate(any());
    }

    @Test
    void standardPdfAndAudioUsePythonDoublesAndPersistAcceptedAdmissions() throws Exception {
        Browser pdfBrowser = acceptedBrowser();
        Browser audioBrowser = acceptedBrowser();
        when(preprocessingClient.extractPdf(any())).thenReturn(new PdfExtractionResponse(
                PDF_TEXT, 1, PDF_TEXT.codePointCount(0, PDF_TEXT.length()), "TEXT_LAYER"));
        when(preprocessingClient.transcribeAudio(any(), anyString())).thenReturn(new AudioTranscriptionResponse(
                AUDIO_TEXT, 12.5, AUDIO_TEXT.codePointCount(0, AUDIO_TEXT.length()),
                "es", "FASTER_WHISPER", "base"));

        MvcResult pdf = mockMvc.perform(multipart("/analyses/pdf")
                        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", PDF))
                        .cookie(pdfBrowser.session(), pdfBrowser.csrf().cookie())
                        .header("X-XSRF-TOKEN", pdfBrowser.csrf().token()))
                .andExpect(status().isAccepted()).andReturn();
        MvcResult audio = mockMvc.perform(multipart("/analyses/audio")
                        .file(new MockMultipartFile("file", "test.wav", "audio/wav", AUDIO))
                        .cookie(audioBrowser.session(), audioBrowser.csrf().cookie())
                        .header("X-XSRF-TOKEN", audioBrowser.csrf().token()))
                .andExpect(status().isAccepted()).andReturn();

        verify(preprocessingClient).extractPdf(PDF);
        verify(preprocessingClient).transcribeAudio(AUDIO, "audio/wav");
        assertAcceptedType(analysisId(pdf), AnalysisInputType.PDF);
        assertAcceptedType(analysisId(audio), AnalysisInputType.AUDIO);
        verify(provider, never()).generate(any());
    }

    @Test
    void adminExceedsStandardPersonalQuotaButStillRespectsGlobalCapacity() throws Exception {
        User admin = user("ADMIN");
        String token = jwtService.generateToken(admin.getDoi());

        postAdminText(token, "Primera descripción administrativa suficientemente extensa.")
                .andExpect(status().isAccepted());
        postAdminText(token, "Segunda descripción administrativa suficientemente extensa.")
                .andExpect(status().isAccepted());
        postAdminText(token, "Tercera descripción administrativa suficientemente extensa.")
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"))
                .andExpect(jsonPath("$.code").value("ANALYSIS_CAPACITY_EXCEEDED"));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM analysis_admissions
                WHERE user_id = ? AND actor_mode = 'ADMIN_JWT' AND state = 'ACCEPTED'
                """, Integer.class, admin.getUserId())).isEqualTo(2);
    }

    @Test
    void adminAudioRequestsShareTheGlobalPreprocessingSlot() throws Exception {
        User admin = user("ADMIN");
        String token = jwtService.generateToken(admin.getDoi());
        CountDownLatch enteredPython = new CountDownLatch(1);
        CountDownLatch completePython = new CountDownLatch(1);
        when(preprocessingClient.transcribeAudio(any(), anyString())).thenAnswer(invocation -> {
            enteredPython.countDown();
            if (!completePython.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to complete synthetic transcription");
            }
            return new AudioTranscriptionResponse(
                    AUDIO_TEXT, 12.5, AUDIO_TEXT.codePointCount(0, AUDIO_TEXT.length()),
                    "es", "FASTER_WHISPER", "base");
        });

        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> mockMvc.perform(multipart("/analyses/audio")
                            .file(new MockMultipartFile("file", "first.wav", "audio/wav", AUDIO))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn());
            assertThat(enteredPython.await(10, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(multipart("/analyses/audio")
                            .file(new MockMultipartFile("file", "second.wav", "audio/wav", AUDIO))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"))
                    .andExpect(jsonPath("$.code").value("AUDIO_PREPROCESSING_BUSY"));

            completePython.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(202);
        } finally {
            completePython.countDown();
        }
        verify(preprocessingClient, times(1)).transcribeAudio(AUDIO, "audio/wav");
    }

    private Browser acceptedBrowser() throws Exception {
        User user = user("STANDARD");
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
        mockMvc.perform(post("/standard-session/consent")
                        .cookie(sessionCookie, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentHash\":\"" + TestLegalDocumentFixture.HASH
                                + "\",\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());
        return new Browser(sessionCookie, csrf, session.getSessionId(), user.getUserId());
    }

    private User user(String type) {
        User user = new User();
        user.setDoi("ADMISSION-HTTP-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType(type);
        user.setStatus("A");
        user.setPassword("unused-test-password");
        return userRepository.saveAndFlush(user);
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/standard-session/csrf"))
                .andExpect(status().isOk()).andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"),
                json(result).path("token").asText());
    }

    private org.springframework.test.web.servlet.ResultActions postText(Browser browser, String description)
            throws Exception {
        return mockMvc.perform(post("/analyses/text")
                .cookie(browser.session(), browser.csrf().cookie())
                .header("X-XSRF-TOKEN", browser.csrf().token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + description + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions postAdminText(String token, String description)
            throws Exception {
        return mockMvc.perform(post("/analyses/text")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + description + "\"}"));
    }

    private void assertAcceptedType(UUID analysisId, AnalysisInputType type) {
        assertThat(analysisRepository.findById(analysisId)).get()
                .extracting(com.mnk.identipatia.analysis.model.Analysis::getInputType).isEqualTo(type);
        assertThat(inputRepository.findById(analysisId)).isPresent();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM analysis_admissions
                WHERE analysis_id = ? AND input_type = ? AND state = 'ACCEPTED'
                """, Integer.class, analysisId, type.name())).isEqualTo(1);
    }

    private UUID analysisId(MvcResult result) throws Exception {
        return UUID.fromString(json(result).path("analysisId").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Csrf(Cookie cookie, String token) {
    }

    private record Browser(Cookie session, Csrf csrf, UUID sessionId, Long userId) {
    }
}
