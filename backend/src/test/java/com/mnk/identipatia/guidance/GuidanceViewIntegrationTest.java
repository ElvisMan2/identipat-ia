package com.mnk.identipatia.guidance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.dto.ProtectionGuideCreateRequest;
import com.mnk.identipatia.guidance.repository.GuidanceViewRepository;
import com.mnk.identipatia.guidance.repository.ProtectionGuideRepository;
import com.mnk.identipatia.guidance.service.ProtectionGuideService;
import com.mnk.identipatia.model.ConsentEvent;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.ConsentEventRepository;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class GuidanceViewIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_guidance_view_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired ConsentEventRepository consentRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired ProtectionGuideRepository guideRepository;
    @Autowired GuidanceViewRepository viewRepository;
    @Autowired ProtectionGuideService guideService;
    @Autowired JwtService jwtService;

    @BeforeEach
    void resetDatabase() {
        viewRepository.deleteAll();
        jdbc.update("delete from analysis_report_contexts");
        resultRepository.deleteAll();
        jdbc.update("delete from ai_invocations");
        jdbc.update("delete from analysis_inputs");
        analysisRepository.deleteAll();
        consentRepository.deleteAll();
        sessionRepository.deleteAll();
        jdbc.update("delete from protection_guides where created_source='ADMIN'");
        jdbc.update("update protection_guides set status='ACTIVE', retired_at=null where created_source='SYSTEM'");
        jdbc.update("update protection_guide_version_counters set next_version=2");
        userRepository.deleteAll();
    }

    @Test
    void migrationCreatesRequiredConstraintsWithoutCascadeDelete() {
        assertThat(jdbc.queryForObject("select to_regclass('public.guidance_views')::text", String.class))
                .isEqualTo("guidance_views");
        List<String> definitions = jdbc.queryForList("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'guidance_views'::regclass
                """, String.class);
        assertThat(definitions).anyMatch(value -> value.startsWith("PRIMARY KEY (guidance_view_id)"));
        assertThat(definitions).anyMatch(value -> value.contains("FOREIGN KEY (analysis_id)")
                && value.contains("REFERENCES analyses(analysis_id)") && !value.contains("CASCADE"));
        assertThat(definitions).anyMatch(value -> value.contains("FOREIGN KEY (protection_type, guide_version)")
                && value.contains("REFERENCES protection_guides(protection_type, version)"));
        assertThat(definitions).anyMatch(value -> value.contains("UNIQUE (analysis_id, protection_type, guide_version)"));
        assertThat(definitions).anyMatch(value -> value.contains("guide_version > 0"));
        assertThat(definitions).anyMatch(value -> value.contains("INVENTION_PATENT")
                && value.contains("TRADE_SECRET"));
    }

    @Test
    void firstViewAndSequentialRetryAreIdempotentAndPrivate() throws Exception {
        Browser owner = browser();
        UUID analysisId = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));

        MvcResult first = postView(owner, analysisId, "UTILITY_MODEL", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.protectionType").value("UTILITY_MODEL"))
                .andExpect(jsonPath("$.guideVersion").value(1))
                .andExpect(jsonPath("$.firstViewedAt").isNotEmpty())
                .andExpect(jsonPath("$.guidanceViewId").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.contentHtml").doesNotExist()).andReturn();
        MvcResult retry = postView(owner, analysisId, "UTILITY_MODEL", 1).andExpect(status().isOk()).andReturn();

        assertThat(viewRepository.count()).isEqualTo(1);
        assertThat(json(first).path("firstViewedAt").asText())
                .isEqualTo(json(retry).path("firstViewedAt").asText());
    }

    @Test
    void concurrentRequestsCreateOneRowAndReturnTheSameFirstViewedAt() throws Exception {
        Browser owner = browser();
        UUID analysisId = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return postViewResult(owner, analysisId); });
            var second = pool.submit(() -> { start.await(); return postViewResult(owner, analysisId); });
            start.countDown();
            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(json(firstResult).path("firstViewedAt").asText())
                    .isEqualTo(json(secondResult).path("firstViewedAt").asText());
        }
        assertThat(viewRepository.count()).isEqualTo(1);
    }

    @Test
    void likelyAndPossibleAreAcceptedWhileUnlikelyIsRejected() throws Exception {
        Browser owner = browser();
        UUID likely = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));
        UUID possible = completedAnalysis(owner, v2Result("COPYRIGHT", "POSSIBLE"));
        UUID unlikely = completedAnalysis(owner, v2Result("INVENTION_PATENT", "UNLIKELY"));

        postView(owner, likely, "UTILITY_MODEL", 1).andExpect(status().isOk());
        postView(owner, possible, "COPYRIGHT", 1).andExpect(status().isOk());
        postView(owner, unlikely, "INVENTION_PATENT", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_NEXT_STEPS_NOT_AVAILABLE"));
        assertThat(viewRepository.count()).isEqualTo(2);
    }

    @Test
    void modalityMustBelongToAnalysisAndProtectionTypeMustBeCanonical() throws Exception {
        Browser owner = browser();
        UUID analysisId = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));
        postView(owner, analysisId, "TRADE_SECRET", 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUIDANCE_MODALITY_NOT_FOUND"));
        postView(owner, analysisId, "NOT_A_TYPE", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROTECTION_TYPE"));
        assertThat(viewRepository.count()).isZero();
    }

    @Test
    void activeAndRetiredVersionsAreAcceptedButDraftAndMissingVersionsAreRejected() throws Exception {
        Browser owner = browser();
        User admin = admin();
        UUID activeAnalysisId = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));
        UUID displayedAnalysisId = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));

        postView(owner, activeAnalysisId, "UTILITY_MODEL", 1).andExpect(status().isOk());
        MvcResult displayedGuidance = mockMvc.perform(get("/analyses/{id}/guidance", displayedAnalysisId)
                        .cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalities[0].protectionType").value("UTILITY_MODEL"))
                .andExpect(jsonPath("$.modalities[0].guideVersion").value(1))
                .andReturn();
        long displayedVersion = json(displayedGuidance).path("modalities").path(0).path("guideVersion").asLong();
        var draft = guideService.create(new ProtectionGuideCreateRequest(ProtectionType.UTILITY_MODEL,
                "Protege mejoras funcionales mediante una orientación institucional controlada.",
                "<p>Contenido institucional de la versión siguiente.</p>"), authentication(admin));
        postView(owner, displayedAnalysisId, "UTILITY_MODEL", draft.version()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_VERSION_NOT_AVAILABLE"));
        postView(owner, displayedAnalysisId, "UTILITY_MODEL", 999).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_VERSION_NOT_AVAILABLE"));

        guideService.activate(draft.guideId(), authentication(admin));
        assertThat(guideRepository.findByProtectionTypeAndVersion(ProtectionType.UTILITY_MODEL, 1L)
                .orElseThrow().getStatus().name()).isEqualTo("RETIRED");
        postView(owner, displayedAnalysisId, "UTILITY_MODEL", displayedVersion).andExpect(status().isOk());
        assertThat(viewRepository.findByAnalysisIdAndProtectionTypeAndGuideVersion(
                displayedAnalysisId, ProtectionType.UTILITY_MODEL, 1L)).isPresent();
    }

    @Test
    void ownershipSessionCsrfAndBearerPrecedenceAreEnforced() throws Exception {
        Browser owner = browser();
        Browser other = browser();
        UUID analysisId = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));

        postView(other, analysisId, "UTILITY_MODEL", 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
        mockMvc.perform(post(viewUrl(analysisId, "UTILITY_MODEL"))
                        .cookie(owner.session(), owner.csrf().cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guideVersion\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(viewUrl(analysisId, "UTILITY_MODEL"))
                        .cookie(owner.session(), owner.csrf().cookie()).header("X-XSRF-TOKEN", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guideVersion\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(viewUrl(analysisId, "UTILITY_MODEL"))
                        .cookie(owner.csrf().cookie()).header("X-XSRF-TOKEN", owner.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guideVersion\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STANDARD_SESSION_REQUIRED"));

        User admin = admin();
        mockMvc.perform(post(viewUrl(analysisId, "UTILITY_MODEL"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(admin.getDoi()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guideVersion\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUIDANCE_VIEW_STANDARD_ONLY"));
        mockMvc.perform(post(viewUrl(analysisId, "UTILITY_MODEL"))
                        .cookie(owner.session())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manipulated-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guideVersion\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_AUTHENTICATION_REQUIRED"));
        assertThat(viewRepository.count()).isZero();
    }

    @Test
    void analysisStatesAndInvalidRequestUseControlledErrors() throws Exception {
        Browser owner = browser();
        UUID received = analysis(owner, AnalysisStatus.RECEIVED);
        UUID analyzing = analysis(owner, AnalysisStatus.ANALYZING);
        UUID failed = analysis(owner, AnalysisStatus.FAILED);
        UUID completed = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));

        postView(owner, received, "UTILITY_MODEL", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_NOT_READY"));
        postView(owner, analyzing, "UTILITY_MODEL", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_NOT_READY"));
        postView(owner, failed, "UTILITY_MODEL", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_UNAVAILABLE"));
        postView(owner, completed, "UTILITY_MODEL", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUIDANCE_VIEW_REQUEST"));
        postView(owner, completed, "UTILITY_MODEL", -1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUIDANCE_VIEW_REQUEST"));
        postViewBody(owner, completed, "UTILITY_MODEL", "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUIDANCE_VIEW_REQUEST"));
        postViewBody(owner, completed, "UTILITY_MODEL", "{\"guideVersion\":\"not-a-number\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUIDANCE_VIEW_REQUEST"));
        postViewBody(owner, completed, "UTILITY_MODEL", "{\"guideVersion\":1.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUIDANCE_VIEW_REQUEST"));
        postViewBody(owner, completed, "UTILITY_MODEL", "{\"guideVersion\":9223372036854775808}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUIDANCE_VIEW_REQUEST"));
        assertThat(viewRepository.count()).isZero();
    }

    @Test
    void v1AndV2AreSupportedWhileUnknownResultVersionIsRejected() throws Exception {
        Browser owner = browser();
        UUID v1 = completedAnalysis(owner, objectMapper.readTree("""
                {"schemaVersion":"analysis-result/1.0","summary":"Resumen histórico",
                 "patentabilityAssessment":{"outcome":"INSUFFICIENT_INFORMATION","rationale":"Faltan datos."},
                 "protectionOptions":[{"type":"COPYRIGHT","applicability":"POSSIBLE","rationale":"Original."}],
                 "observations":[],"warnings":[]}
                """));
        UUID v2 = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));
        UUID unsupported = completedAnalysis(owner, v2Result("UTILITY_MODEL", "LIKELY"));
        StoredAnalysisResult stored = resultRepository.findById(unsupported).orElseThrow();
        stored.setSchemaVersion("analysis-result/9.0");
        stored.setResultJson(objectMapper.readTree("{\"schemaVersion\":\"analysis-result/9.0\"}"));
        resultRepository.saveAndFlush(stored);

        postView(owner, v1, "COPYRIGHT", 1).andExpect(status().isOk());
        postView(owner, v2, "UTILITY_MODEL", 1).andExpect(status().isOk());
        postView(owner, unsupported, "UTILITY_MODEL", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_UNSUPPORTED_RESULT_VERSION"));
    }

    private Browser browser() throws Exception {
        User user = new User();
        user.setDoi("F18D-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType("STANDARD");
        user.setStatus("A");
        User savedUser = userRepository.saveAndFlush(user);
        Csrf csrf = csrf();
        MvcResult created = mockMvc.perform(post("/standard-sessions")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"" + savedUser.getDoi() + "\",\"doiType\":\"DNI\"}"))
                .andExpect(status().isCreated()).andReturn();
        Cookie sessionCookie = created.getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        StandardSession session = sessionRepository.findAll().stream()
                .filter(value -> value.getUser().getUserId().equals(savedUser.getUserId())).findFirst().orElseThrow();
        mockMvc.perform(post("/standard-session/consent").cookie(sessionCookie, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentVersion\":\"personal-data/1.0\",\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());
        ConsentEvent consent = consentRepository.findAll().stream()
                .filter(value -> value.getSession().getSessionId().equals(session.getSessionId()))
                .findFirst().orElseThrow();
        return new Browser(sessionCookie, csrf, savedUser, session, consent.getConsentEventId());
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/standard-session/csrf"))
                .andExpect(status().isOk()).andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"), json(result).path("token").asText());
    }

    private UUID analysis(Browser owner, AnalysisStatus status) {
        Instant now = Instant.now();
        Analysis analysis = new Analysis();
        analysis.setAnalysisId(UUID.randomUUID());
        analysis.setUserId(owner.user().getUserId());
        analysis.setSessionId(owner.standardSession().getSessionId());
        analysis.setConsentEventId(owner.consentEventId());
        analysis.setInputType(AnalysisInputType.TEXT);
        analysis.setStatus(status);
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        if (status == AnalysisStatus.ANALYZING) {
            analysis.setStartedAt(now);
        } else if (status == AnalysisStatus.FAILED) {
            analysis.setFailedAt(now);
            analysis.setFailureCode("WORKER_PROCESSING_ERROR");
        }
        return analysisRepository.saveAndFlush(analysis).getAnalysisId();
    }

    private UUID completedAnalysis(Browser owner, JsonNode resultJson) {
        UUID analysisId = analysis(owner, AnalysisStatus.RECEIVED);
        Instant now = Instant.now();
        Analysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(now);
        analysis.setUpdatedAt(now);
        analysisRepository.saveAndFlush(analysis);
        StoredAnalysisResult stored = new StoredAnalysisResult();
        stored.setAnalysisId(analysisId);
        stored.setSchemaVersion(resultJson.path("schemaVersion").asText());
        stored.setResultJson(resultJson);
        stored.setCreatedAt(now);
        resultRepository.saveAndFlush(stored);
        return analysisId;
    }

    private JsonNode v2Result(String protectionType, String applicability) throws Exception {
        return objectMapper.readTree("""
                {"schemaVersion":"analysis-result/2.0","summary":"Resumen.",
                 "protectionOptions":[
                   {"type":"%s","applicability":"%s","protectedSubjectMatter":"Materia protegible.",
                    "rationale":"Existe relación.","legalBasis":[{"instrument":"DECISION_486","articles":["81"]}]}
                 ],
                 "patentScreening":{"applicable":false,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}},
                 "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No indicado.","missingInformation":[]},
                 "observations":[],"warnings":[]}
                """.formatted(protectionType, applicability));
    }

    private User admin() {
        User user = new User();
        user.setDoi("F18D-ADMIN-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType("ADMIN");
        user.setStatus("A");
        user.setPassword("unused-for-direct-jwt-test");
        return userRepository.saveAndFlush(user);
    }

    private UsernamePasswordAuthenticationToken authentication(User admin) {
        return new UsernamePasswordAuthenticationToken(admin.getDoi(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private org.springframework.test.web.servlet.ResultActions postView(
            Browser browser, UUID analysisId, String protectionType, long guideVersion) throws Exception {
        return postViewBody(browser, analysisId, protectionType, "{\"guideVersion\":" + guideVersion + "}");
    }

    private org.springframework.test.web.servlet.ResultActions postViewBody(
            Browser browser, UUID analysisId, String protectionType, String body) throws Exception {
        return mockMvc.perform(post(viewUrl(analysisId, protectionType))
                .cookie(browser.session(), browser.csrf().cookie())
                .header("X-XSRF-TOKEN", browser.csrf().token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MvcResult postViewResult(Browser browser, UUID analysisId) throws Exception {
        return postView(browser, analysisId, "UTILITY_MODEL", 1).andReturn();
    }

    private String viewUrl(UUID analysisId, String protectionType) {
        return "/analyses/" + analysisId + "/guidance/" + protectionType + "/view";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Csrf(Cookie cookie, String token) {
    }

    private record Browser(Cookie session, Csrf csrf, User user,
            StandardSession standardSession, UUID consentEventId) {
    }
}
