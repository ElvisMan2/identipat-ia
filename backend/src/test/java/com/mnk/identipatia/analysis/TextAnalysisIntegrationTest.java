package com.mnk.identipatia.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.exception.GenerativeAiErrorType;
import com.mnk.identipatia.ai.exception.GenerativeAiException;
import com.mnk.identipatia.ai.model.GenerativeAiRequest;
import com.mnk.identipatia.ai.model.GenerativeAiResponse;
import com.mnk.identipatia.ai.model.ProviderExecution;
import com.mnk.identipatia.ai.model.TokenUsage;
import com.mnk.identipatia.analysis.model.AiInvocation;
import com.mnk.identipatia.analysis.model.AiInvocationStatus;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.repository.AiInvocationRepository;
import com.mnk.identipatia.analysis.repository.AnalysisInputRepository;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.worker.AnalysisAttemptService;
import com.mnk.identipatia.analysis.worker.AnalysisClaimService;
import com.mnk.identipatia.analysis.worker.AnalysisWorker;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.ConsentEventRepository;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "app.analysis.retry-delay=1ms",
        "app.analysis.lease-duration=30s"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TextAnalysisIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_text_analysis_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired ConsentEventRepository consentRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired AnalysisInputRepository inputRepository;
    @Autowired AiInvocationRepository invocationRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired AnalysisWorker worker;
    @Autowired AnalysisClaimService claimService;
    @Autowired AnalysisAttemptService attemptService;
    @Autowired @Qualifier("analysisWorkerId") String workerId;

    @MockBean GenerativeAiProvider provider;

    @BeforeEach
    void resetState() {
        resultRepository.deleteAll();
        invocationRepository.deleteAll();
        inputRepository.deleteAll();
        analysisRepository.deleteAll();
        consentRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        reset(provider);
        when(provider.providerId()).thenReturn("fake");
    }

    @Test
    void completeHttpFlowIsAsyncDurableAndDoesNotExposeInvocationMetadata() throws Exception {
        Browser browser = acceptedBrowser();
        String original = "  Mecanismo ele\u0301ctrico\r\nmodular para controlar riego automáticamente.  ";

        MvcResult post = postAnalysis(browser, original)
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/identipat-ia/analyses/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();
        UUID analysisId = UUID.fromString(json(post).path("analysisId").asText());

        verify(provider, never()).generate(any());
        Analysis received = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(received.getStatus()).isEqualTo(AnalysisStatus.RECEIVED);
        assertThat(received.getConsentEventId()).isNotNull();
        assertThat(inputRepository.findById(analysisId).orElseThrow())
                .satisfies(input -> {
                    assertThat(input.getOriginalText()).isEqualTo(original);
                    assertThat(input.getProcessedText())
                            .isEqualTo("Mecanismo eléctrico\nmodular para controlar riego automáticamente.");
                    assertThat(input.getSourceMetadata().isObject()).isTrue();
                    assertThat(input.getSourceMetadata()).isEmpty();
                    assertThat(input.getProcessedAt()).isNotNull();
                });

        getAnalysis(browser, analysisId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.inputType").value("TEXT"))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.failure").isEmpty());

        when(provider.generate(any())).thenReturn(successResponse());
        assertThat(worker.processOneSynchronously()).isTrue();

        Analysis completed = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.getLeaseOwner()).isNull();
        assertThat(resultRepository.findById(analysisId)).isPresent();
        AiInvocation invocation = invocationRepository.findAllByAnalysisIdOrderByAttemptNumber(analysisId).getFirst();
        assertThat(invocation.getStatus()).isEqualTo(AiInvocationStatus.SUCCEEDED);
        assertThat(invocation.getAttemptNumber()).isEqualTo(1);
        assertThat(invocation.getPromptId()).isEqualTo("intellectual-property-analysis");
        assertThat(invocation.getPromptVersion()).isEqualTo("0.1");
        assertThat(invocation.getOutputSchemaId()).isEqualTo("analysis-result");
        assertThat(invocation.getOutputSchemaVersion()).isEqualTo("1.0");
        assertThat(invocation.getRenderedPromptSnapshot()).contains("Mecanismo eléctrico");
        assertThat(invocation.getRequestParameters().path("maxOutputTokens").asInt()).isEqualTo(4000);
        assertThat(invocation.getRawProviderResponse().path("response").asText()).isEqualTo("raw");
        assertThat(invocation.getTotalTokens()).isEqualTo(15);

        verify(provider).generate(any(GenerativeAiRequest.class));
        getAnalysis(browser, analysisId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.schemaVersion").value("analysis-result/1.0"))
                .andExpect(jsonPath("$.result.patentabilityAssessment.outcome")
                        .value("POTENTIALLY_PATENTABLE"))
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.invocations").doesNotExist());
    }

    @Test
    void postRequiresSessionCsrfConsentAndConfiguredLength() throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/analyses/text").cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa\"}"))
                .andExpect(status().isUnauthorized());

        Browser withoutConsent = browser(false);
        postAnalysis(withoutConsent, "Descripción suficientemente extensa para analizar.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mockMvc.perform(post("/analyses/text").cookie(withoutConsent.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa para analizar.\"}"))
                .andExpect(status().isForbidden());
        postAnalysis(withoutConsent, " corto ")
                .andExpect(status().isConflict());

        Browser accepted = acceptedBrowser();
        postAnalysis(accepted, " corto ")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ANALYSIS_DESCRIPTION"));
        postAnalysis(accepted, "x".repeat(20_001))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/analyses/text").cookie(accepted.session(), accepted.csrf().cookie())
                        .header("X-XSRF-TOKEN", accepted.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUsesCurrentSessionOnlyNeedsNoCsrfAndExpiredSessionIsUnauthorized() throws Exception {
        Browser owner = acceptedBrowser();
        UUID analysisId = id(postAnalysis(owner, "Descripción técnica suficientemente extensa para consulta.")
                .andExpect(status().isAccepted()).andReturn());
        Browser other = acceptedBrowser();

        getAnalysis(other, analysisId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
        getAnalysis(owner, UUID.randomUUID()).andExpect(status().isNotFound());
        getAnalysis(owner, analysisId).andExpect(status().isOk());

        StandardSession session = sessionRepository.findById(owner.sessionId()).orElseThrow();
        session.setExpiresAt(session.getCreatedAt().plusMillis(1));
        sessionRepository.saveAndFlush(session);
        getAnalysis(owner, analysisId).andExpect(status().isUnauthorized());
    }

    @Test
    void retryableTimeoutSchedulesRetryThenSecondAttemptCompletes() throws Exception {
        Browser browser = acceptedBrowser();
        UUID analysisId = id(postAnalysis(browser, "Mecanismo técnico con descripción suficiente para reintentar.")
                .andReturn());
        when(provider.generate(any()))
                .thenThrow(timeout(true))
                .thenReturn(successResponse());

        assertThat(worker.processOneSynchronously()).isTrue();
        Analysis retrying = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(retrying.getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
        assertThat(retrying.getNextAttemptAt()).isNotNull();
        assertThat(retrying.getLeaseOwner()).isNull();
        assertThat(invocationRepository.findAllByAnalysisIdOrderByAttemptNumber(analysisId).getFirst().getStatus())
                .isEqualTo(AiInvocationStatus.TIMED_OUT);

        Thread.sleep(5);
        assertThat(worker.processOneSynchronously()).isTrue();
        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(invocationRepository.findAllByAnalysisIdOrderByAttemptNumber(analysisId))
                .extracting(AiInvocation::getAttemptNumber).containsExactly(1, 2);
    }

    @Test
    void secondRetryableFailureIsTerminalAndPublicFailureIsSanitized() throws Exception {
        Browser browser = acceptedBrowser();
        UUID analysisId = id(postAnalysis(browser, "Mecanismo técnico con descripción suficiente para fallar.")
                .andReturn());
        when(provider.generate(any())).thenThrow(timeout(true));

        assertThat(worker.processOneSynchronously()).isTrue();
        Thread.sleep(5);
        assertThat(worker.processOneSynchronously()).isTrue();
        assertThat(worker.processOneSynchronously()).isFalse();
        assertThat(invocationRepository.findAllByAnalysisIdOrderByAttemptNumber(analysisId)).hasSize(2);
        getAnalysis(browser, analysisId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure.code").value("ANALYSIS_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.failure.message")
                        .value("No fue posible completar el análisis en este momento."))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void expiredLeaseAbandonsStartedAttemptButActiveLeaseDoesNot() throws Exception {
        Browser browser = acceptedBrowser();
        UUID analysisId = id(postAnalysis(browser, "Descripción técnica extensa para recuperar un worker caído.")
                .andReturn());
        assertThat(claimService.claimNext("crashed-worker")).contains(analysisId);
        assertThat(attemptService.prepare(analysisId, "crashed-worker", provider)).isPresent();
        getAnalysis(browser, analysisId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANALYZING"));
        assertThat(worker.processOneSynchronously()).isFalse();
        assertThat(invocationRepository.findAllByAnalysisIdOrderByAttemptNumber(analysisId).getFirst().getStatus())
                .isEqualTo(AiInvocationStatus.STARTED);

        Analysis stale = analysisRepository.findById(analysisId).orElseThrow();
        stale.setLeaseUntil(Instant.now().minusSeconds(1));
        analysisRepository.saveAndFlush(stale);
        when(provider.generate(any())).thenReturn(successResponse());

        assertThat(worker.processOneSynchronously()).isTrue();
        List<AiInvocation> attempts = invocationRepository.findAllByAnalysisIdOrderByAttemptNumber(analysisId);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).getStatus()).isEqualTo(AiInvocationStatus.ABANDONED);
        assertThat(attempts.get(0).getErrorCode()).isEqualTo("WORKER_LEASE_LOST");
        assertThat(attempts.get(1).getStatus()).isEqualTo(AiInvocationStatus.SUCCEEDED);
    }

    @Test
    void concurrentClaimsUseSkipLockedAndOnlyOneWorkerWins() throws Exception {
        Browser browser = acceptedBrowser();
        UUID analysisId = id(postAnalysis(browser, "Descripción técnica extensa para probar claim concurrente.")
                .andReturn());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Optional<UUID>> first = pool.submit(() -> {
                start.await();
                return claimService.claimNext("worker-a");
            });
            Future<Optional<UUID>> second = pool.submit(() -> {
                start.await();
                return claimService.claimNext("worker-b");
            });
            start.countDown();
            assertThat(List.of(first.get(), second.get()).stream().filter(Optional::isPresent).toList())
                    .containsExactly(Optional.of(analysisId));
        }
        Analysis claimed = analysisRepository.findById(analysisId).orElseThrow();
        Instant startedAt = claimed.getStartedAt();
        assertThat(claimed.getLeaseOwner()).isIn("worker-a", "worker-b");
        claimed.setLeaseUntil(Instant.now().minusSeconds(1));
        analysisRepository.saveAndFlush(claimed);
        assertThat(claimService.claimNext("worker-c")).contains(analysisId);
        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStartedAt()).isEqualTo(startedAt);
    }

    @Test
    void failedSuccessTransactionCannotLeaveCompletedWithoutResult() throws Exception {
        Browser browser = acceptedBrowser();
        UUID analysisId = id(postAnalysis(browser,
                "Descripción técnica extensa para verificar atomicidad transaccional.").andReturn());
        assertThat(claimService.claimNext("atomic-worker")).contains(analysisId);
        AnalysisAttemptService.AttemptContext attempt =
                attemptService.prepare(analysisId, "atomic-worker", provider).orElseThrow();
        GenerativeAiResponse valid = successResponse();
        GenerativeAiResponse oversizedColumn = new GenerativeAiResponse(
                valid.provider(), valid.structuredOutput(), valid.rawProviderResponse(), valid.tokenUsage(),
                "x".repeat(300), valid.latencyMs());
        var canonical = objectMapper.treeToValue(valid.structuredOutput(),
                com.mnk.identipatia.analysis.result.AnalysisResult.class);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                attemptService.succeed(analysisId, attempt.invocationId(), "atomic-worker",
                        oversizedColumn, canonical)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(analysisRepository.findById(analysisId).orElseThrow().getStatus())
                .isEqualTo(AnalysisStatus.ANALYZING);
        assertThat(resultRepository.findById(analysisId)).isEmpty();
        assertThat(invocationRepository.findById(attempt.invocationId()).orElseThrow().getStatus())
                .isEqualTo(AiInvocationStatus.STARTED);
    }

    private Browser acceptedBrowser() throws Exception {
        return browser(true);
    }

    private Browser browser(boolean acceptConsent) throws Exception {
        User user = new User();
        user.setDoi("F13-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType("STANDARD");
        user.setStatus("A");
        userRepository.saveAndFlush(user);
        Csrf csrf = csrf();
        MvcResult sessionResult = mockMvc.perform(post("/standard-sessions")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"" + user.getDoi() + "\",\"doiType\":\"DNI\"}"))
                .andExpect(status().isCreated()).andReturn();
        Cookie sessionCookie = sessionResult.getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        StandardSession session = sessionRepository.findAll().stream()
                .filter(value -> value.getUser().getUserId().equals(user.getUserId())).findFirst().orElseThrow();
        if (acceptConsent) {
            mockMvc.perform(post("/standard-session/consent").cookie(sessionCookie, csrf.cookie())
                            .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"consentVersion\":\"personal-data/1.0\",\"decision\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk());
        }
        return new Browser(sessionCookie, csrf, session.getSessionId());
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/standard-session/csrf"))
                .andExpect(status().isOk()).andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"),
                json(result).path("token").asText());
    }

    private org.springframework.test.web.servlet.ResultActions postAnalysis(Browser browser, String description)
            throws Exception {
        return mockMvc.perform(post("/analyses/text").cookie(browser.session(), browser.csrf().cookie())
                .header("X-XSRF-TOKEN", browser.csrf().token()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("description", description))));
    }

    private org.springframework.test.web.servlet.ResultActions getAnalysis(Browser browser, UUID analysisId)
            throws Exception {
        return mockMvc.perform(get("/analyses/{analysisId}", analysisId).cookie(browser.session()));
    }

    private UUID id(MvcResult result) throws Exception {
        return UUID.fromString(json(result).path("analysisId").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private GenerativeAiResponse successResponse() throws Exception {
        JsonNode structured = objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/1.0",
                  "summary":"La descripción presenta una solución técnica preliminar.",
                  "patentabilityAssessment":{
                    "outcome":"POTENTIALLY_PATENTABLE",
                    "rationale":"Podría existir una solución técnica, sujeta a evaluación posterior."
                  },
                  "protectionOptions":[{
                    "type":"INVENTION_PATENT",
                    "applicability":"POSSIBLE",
                    "rationale":"La descripción plantea características técnicas."
                  }],
                  "observations":[],
                  "warnings":["No se realizó una búsqueda de antecedentes."]
                }
                """);
        return new GenerativeAiResponse(
                new ProviderExecution("fake", "fake-model", "response-1"),
                structured, objectMapper.readTree("{\"response\":\"raw\"}"),
                new TokenUsage(10L, 5L, 15L, objectMapper.readTree("{\"cached\":0}")),
                "completed", 25L);
    }

    private static GenerativeAiException timeout(boolean retryable) {
        return new GenerativeAiException(GenerativeAiErrorType.TIMEOUT, "fake", retryable,
                "The generative AI provider request timed out", null);
    }

    private record Csrf(Cookie cookie, String token) {
    }

    private record Browser(Cookie session, Csrf csrf, UUID sessionId) {
    }
}
