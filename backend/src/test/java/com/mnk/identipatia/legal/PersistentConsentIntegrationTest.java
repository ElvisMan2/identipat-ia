package com.mnk.identipatia.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.config.AiProperties;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentHistoryRepository;
import com.mnk.identipatia.legal.repository.LegalDocumentRepository;
import com.mnk.identipatia.legal.repository.UserTermsAcceptanceRepository;
import com.mnk.identipatia.legal.model.UserTermsAcceptance;
import com.mnk.identipatia.legal.service.TermsAcceptanceService;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PersistentConsentIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_persistent_consent_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired ConsentEventRepository legacyConsentRepository;
    @Autowired LegalDocumentRepository documentRepository;
    @Autowired LegalDocumentHistoryRepository historyRepository;
    @SpyBean UserTermsAcceptanceRepository acceptanceRepository;
    @Autowired TermsAcceptanceService termsAcceptanceService;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired JwtService jwtService;
    @MockBean GenerativeAiProvider provider;
    @MockBean AiProperties aiProperties;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from guidance_views");
        jdbc.update("delete from analysis_report_contexts");
        jdbc.update("delete from analysis_results");
        jdbc.update("delete from ai_invocations");
        jdbc.update("delete from analysis_inputs");
        jdbc.update("delete from analyses");
        acceptanceRepository.deleteAll();
        legacyConsentRepository.deleteAll();
        sessionRepository.deleteAll();
        documentRepository.deleteAll();
        historyRepository.deleteAll();
        userRepository.deleteAll();
        reset(provider, aiProperties);
        when(aiProperties.enabled()).thenReturn(true);
    }

    @Test
    void migrationCreatesSingletonHistoryAcceptanceAndTransitionalAnalysisInvariant() {
        assertThat(jdbc.queryForObject("select version from flyway_schema_history order by installed_rank desc limit 1",
                String.class)).isEqualTo("10");
        assertThat(jdbc.queryForObject("select to_regclass('public.legal_document')::text", String.class))
                .isEqualTo("legal_document");
        assertThat(jdbc.queryForObject("select to_regclass('public.legal_document_history')::text", String.class))
                .isEqualTo("legal_document_history");
        assertThat(jdbc.queryForObject("select to_regclass('public.user_terms_acceptances')::text", String.class))
                .isEqualTo("user_terms_acceptances");

        List<String> acceptanceConstraints = constraints("user_terms_acceptances");
        assertThat(acceptanceConstraints).anyMatch(value -> value.contains(
                "UNIQUE (user_id, accepted_content_sha256, accepted_document_updated_at)"));
        assertThat(acceptanceConstraints).anyMatch(value -> value.contains("UNIQUE (acceptance_id, user_id)"));
        assertThat(acceptanceConstraints).anyMatch(value -> value.contains("STANDARD_REGISTRATION")
                && value.contains("STANDARD_WEB"));
        assertThat(acceptanceConstraints).anyMatch(value -> value.contains("legal_document_history"));
        assertThat(jdbc.queryForObject("""
                select is_nullable
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'user_terms_acceptances'
                  and column_name = 'accepted_document_updated_at'
                """, String.class)).isEqualTo("NO");

        List<String> documentConstraints = constraints("legal_document");
        assertThat(documentConstraints).anyMatch(value -> value.contains("UNIQUE (singleton_key)"));
        assertThat(documentConstraints).anyMatch(value -> value.contains("singleton_key = 1"));

        List<String> analysisConstraints = constraints("analyses");
        assertThat(analysisConstraints).anyMatch(value -> value.contains("terms_acceptance_id")
                && value.contains("user_terms_acceptances"));
        assertThat(analysisConstraints).anyMatch(value -> value.contains("consent_event_id IS NOT NULL")
                && value.contains("terms_acceptance_id IS NOT NULL"));
    }

    @Test
    void adminUpsertSanitizesHashesSnapshotsAndCanReturnToHistoricalHash() throws Exception {
        mockMvc.perform(get("/legal-document"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_NOT_FOUND"));
        mockMvc.perform(put("/admin/legal-document").contentType(MediaType.APPLICATION_JSON)
                        .content(legalBody("Title A", "<p>Text A</p>")))
                .andExpect(status().isUnauthorized());

        User admin = saveUser("ADMIN");
        String bearer = "Bearer " + jwtService.generateToken(admin.getDoi());
        String dangerous = "<h1 id='x'>Heading</h1><script>alert(1)</script>"
                + "<p onclick='x()'>Visible</p><a href='https://example.org' target='_self'>Link</a>"
                + "<img src='https://example.org/x.png'>";
        MvcResult first = mockMvc.perform(put("/admin/legal-document")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(legalBody("  Title A  ", dangerous)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Title A"))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andReturn();
        JsonNode firstJson = json(first);
        String hashA = firstJson.path("contentHash").asText();
        String documentId = firstJson.path("documentId").asText();
        String sanitized = firstJson.path("contentHtml").asText();
        assertThat(sanitized).doesNotContain("script", "onclick", "<img", "id=");
        assertThat(sanitized).contains("target=\"_blank\"", "rel=\"noopener noreferrer\"", "https://example.org");
        assertThat(historyRepository.count()).isEqualTo(1);

        User secondAdmin = saveUser("ADMIN");
        String secondBearer = "Bearer " + jwtService.generateToken(secondAdmin.getDoi());
        MvcResult same = mockMvc.perform(put("/admin/legal-document")
                        .header("Authorization", secondBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(legalBody("Title A", dangerous)))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(same).path("contentHash").asText()).isEqualTo(hashA);
        assertThat(json(same).path("updatedAt").asText()).isEqualTo(firstJson.path("updatedAt").asText());
        assertThat(documentRepository.findCurrent().orElseThrow().getUpdatedBy()).isEqualTo(admin.getUserId());
        assertThat(historyRepository.count()).isEqualTo(1);

        MvcResult second = mockMvc.perform(put("/admin/legal-document")
                        .header("Authorization", secondBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(legalBody("Title B", "<p>Text B</p>")))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(second).path("contentHash").asText()).isNotEqualTo(hashA);
        assertThat(json(second).path("documentId").asText()).isEqualTo(documentId);
        assertThat(documentRepository.findCurrent().orElseThrow().getUpdatedBy()).isEqualTo(secondAdmin.getUserId());
        assertThat(historyRepository.count()).isEqualTo(2);

        MvcResult returned = mockMvc.perform(put("/admin/legal-document").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(legalBody("Title A", dangerous)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentHash").value(hashA))
                .andExpect(jsonPath("$.documentId").value(documentId)).andReturn();
        assertThat(json(returned).path("updatedAt").asText())
                .isNotEqualTo(firstJson.path("updatedAt").asText());
        assertThat(historyRepository.count()).isEqualTo(2);

        mockMvc.perform(get("/legal-document"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentHash").value(hashA))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.acceptanceId").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist());

        assertThatThrownBy(() -> jdbc.update("""
                insert into legal_document(document_id,singleton_key,title,content_html,content_sha256,updated_at)
                values (?,1,'Duplicate','<p>Duplicate</p>',?,now())
                """, UUID.randomUUID(), hashA)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registrationRequiresCsrfCurrentExplicitAcceptanceAndIsAtomic() throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("REG-NO-CSRF", null, true)))
                .andExpect(status().isForbidden());

        mockMvc.perform(csrfPost("/users", csrf).content(registrationBody("REG-NO-DOC", null, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));
        mockMvc.perform(csrfPost("/users", csrf).content(registrationBody("REG-NO-DOC-2", zeroHash(), true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_NOT_FOUND"));

        User admin = saveUser("ADMIN");
        String currentHash = putLegal(admin, "Registration", "<p>Registration terms</p>");
        mockMvc.perform(csrfPost("/users", csrf).content(registrationBody("REG-FALSE", currentHash, false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));
        mockMvc.perform(csrfPost("/users", csrf).content(registrationBody("REG-STALE", zeroHash(), true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_CHANGED"));
        assertThat(userRepository.findByDoi("REG-FALSE")).isEmpty();
        assertThat(userRepository.findByDoi("REG-STALE")).isEmpty();

        mockMvc.perform(csrfPost("/users", csrf).content(registrationBody("REG-OK", currentHash, true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registered").value(true));
        User registered = userRepository.findByDoi("REG-OK").orElseThrow();
        assertThat(registered.getUserType()).isEqualTo("STANDARD");
        assertThat(acceptanceRepository.findByUserIdAndAcceptedContentSha256AndAcceptedDocumentUpdatedAt(
                registered.getUserId(), currentHash,
                documentRepository.findCurrent().orElseThrow().getUpdatedAt())).get()
                .extracting("source").isEqualTo("STANDARD_REGISTRATION");

        doThrow(new DataIntegrityViolationException("forced acceptance failure"))
                .when(acceptanceRepository).insertIfAbsent(
                        any(), anyLong(), anyString(), any(), any(), anyString());
        mockMvc.perform(csrfPost("/users", csrf).content(registrationBody("REG-ROLLBACK", currentHash, true)))
                .andExpect(status().is5xxServerError());
        assertThat(userRepository.findByDoi("REG-ROLLBACK")).isEmpty();
    }

    @Test
    void acceptancePersistsAcrossSessionsAndReturningToHistoricalHashRequiresReacceptance() throws Exception {
        User admin = saveUser("ADMIN");
        String hashA = putLegal(admin, "A", "<p>Terms A</p>");
        User standard = saveUser("STANDARD");
        Csrf csrf = csrf();
        Cookie session1 = createSession(standard, csrf, true);

        MvcResult accepted = mockMvc.perform(consentPost(session1, csrf, hashA, "ACCEPTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(false)).andReturn();
        String acceptedAt = json(accepted).path("acceptedAt").asText();
        UserTermsAcceptance acceptanceA1 = currentAcceptance(standard);
        Instant effectiveA1 = documentRepository.findCurrent().orElseThrow().getUpdatedAt();
        mockMvc.perform(consentPost(session1, csrf, hashA, "ACCEPTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedAt").value(acceptedAt));
        assertThat(acceptanceRepository.count()).isEqualTo(1);

        String identicalHash = putLegal(admin, "A", "<p>Terms A</p>");
        assertThat(identicalHash).isEqualTo(hashA);
        assertThat(documentRepository.findCurrent().orElseThrow().getUpdatedAt()).isEqualTo(effectiveA1);
        mockMvc.perform(get("/standard-session").cookie(session1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(false));

        Cookie session2 = createSession(standard, csrf, false);
        String hashB = putLegal(admin, "B", "<p>Terms B</p>");
        mockMvc.perform(get("/standard-session").cookie(session2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(true));
        mockMvc.perform(post("/analyses/text").cookie(session2, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa para analizar.\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mockMvc.perform(consentPost(session2, csrf, hashA, "ACCEPTED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_CHANGED"));

        User rejecting = saveUser("STANDARD");
        Cookie rejectingSession = createSession(rejecting, csrf, true);
        mockMvc.perform(consentPost(rejectingSession, csrf, hashB, "REJECTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.sessionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(cookie().doesNotExist("IDENTIPAT_STANDARD_SESSION"));
        assertThat(acceptanceRepository.findByUserIdAndAcceptedContentSha256AndAcceptedDocumentUpdatedAt(
                rejecting.getUserId(), hashB,
                documentRepository.findCurrent().orElseThrow().getUpdatedAt())).isEmpty();

        String returnedHashA = putLegal(admin, "A", "<p>Terms A</p>");
        assertThat(returnedHashA).isEqualTo(hashA);
        Instant effectiveA2 = documentRepository.findCurrent().orElseThrow().getUpdatedAt();
        assertThat(effectiveA2).isNotEqualTo(effectiveA1);
        assertThat(historyRepository.count()).isEqualTo(2);

        mockMvc.perform(get("/standard-session").cookie(session2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(true));
        mockMvc.perform(post("/analyses/text").cookie(session2, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa para analizar.\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        MvcResult reaccepted = mockMvc.perform(consentPost(session2, csrf, hashA, "ACCEPTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(false)).andReturn();
        UserTermsAcceptance acceptanceA2 = currentAcceptance(standard);
        assertThat(acceptanceA2.getAcceptanceId()).isNotEqualTo(acceptanceA1.getAcceptanceId());
        assertThat(acceptanceA2.getAcceptedAt()).isNotEqualTo(acceptanceA1.getAcceptedAt());
        assertThat(acceptanceA2.getAcceptedDocumentUpdatedAt()).isEqualTo(effectiveA2);
        assertThat(acceptanceA1.getAcceptedDocumentUpdatedAt()).isEqualTo(effectiveA1);
        assertThat(json(reaccepted).path("acceptedAt").asText()).isNotEqualTo(acceptedAt);
        assertThat(acceptanceRepository.findAll()).filteredOn(acceptance ->
                acceptance.getUserId().equals(standard.getUserId())
                        && acceptance.getAcceptedContentSha256().equals(hashA)).hasSize(2);
        mockMvc.perform(get("/standard-session").cookie(session2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(false));

        MvcResult created = mockMvc.perform(post("/analyses/text").cookie(session2, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa posterior a A2.\"}"))
                .andExpect(status().isAccepted()).andReturn();
        UUID analysisId = UUID.fromString(json(created).path("analysisId").asText());
        assertThat(analysisRepository.findById(analysisId).orElseThrow().getTermsAcceptanceId())
                .isEqualTo(acceptanceA2.getAcceptanceId());
    }

    @Test
    void concurrentAcceptanceIsIdempotentWithinTheSameEffectivePeriod() throws Exception {
        User admin = saveUser("ADMIN");
        String hash = putLegal(admin, "Concurrent", "<p>Concurrent terms</p>");
        User standard = saveUser("STANDARD");
        Instant effectiveAt = documentRepository.findCurrent().orElseThrow().getUpdatedAt();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<UserTermsAcceptance> accept = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent acceptance start timed out");
                }
                return termsAcceptanceService.acceptCurrent(
                        standard.getUserId(), hash, TermsAcceptanceService.STANDARD_WEB);
            };
            Future<UserTermsAcceptance> first = executor.submit(accept);
            Future<UserTermsAcceptance> second = executor.submit(accept);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            UserTermsAcceptance firstResult = first.get(20, TimeUnit.SECONDS);
            UserTermsAcceptance secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(secondResult.getAcceptanceId()).isEqualTo(firstResult.getAcceptanceId());
            assertThat(secondResult.getAcceptedAt()).isEqualTo(firstResult.getAcceptedAt());
            assertThat(acceptanceRepository.findAll()).singleElement().satisfies(acceptance -> {
                assertThat(acceptance.getAcceptedContentSha256()).isEqualTo(hash);
                assertThat(acceptance.getAcceptedDocumentUpdatedAt()).isEqualTo(effectiveAt);
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private UserTermsAcceptance currentAcceptance(User user) {
        var document = documentRepository.findCurrent().orElseThrow();
        return acceptanceRepository
                .findByUserIdAndAcceptedContentSha256AndAcceptedDocumentUpdatedAt(
                        user.getUserId(), document.getContentSha256(), document.getUpdatedAt())
                .orElseThrow();
    }

    private List<String> constraints(String table) {
        return jdbc.queryForList("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = ?::regclass
                """, String.class, table);
    }

    private String putLegal(User admin, String title, String html) throws Exception {
        MvcResult result = mockMvc.perform(put("/admin/legal-document")
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin.getDoi()))
                        .contentType(MediaType.APPLICATION_JSON).content(legalBody(title, html)))
                .andExpect(status().isOk()).andReturn();
        return json(result).path("contentHash").asText();
    }

    private Cookie createSession(User user, Csrf csrf, boolean consentRequired) throws Exception {
        return mockMvc.perform(csrfPost("/standard-sessions", csrf)
                        .content("{\"doi\":\"" + user.getDoi() + "\",\"doiType\":\"DNI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentRequired").value(consentRequired))
                .andReturn().getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consentPost(
            Cookie session, Csrf csrf, String hash, String decision) {
        return csrfPost("/standard-session/consent", csrf).cookie(session)
                .content("{\"contentHash\":\"" + hash + "\",\"decision\":\"" + decision + "\"}");
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/standard-session/csrf"))
                .andExpect(status().isOk()).andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"), json(result).path("token").asText());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder csrfPost(String path, Csrf csrf) {
        return post(path).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private User saveUser(String type) {
        User user = new User();
        user.setDoi(type + "-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType(type);
        user.setStatus("A");
        user.setPassword("ADMIN".equals(type) ? "unused" : null);
        return userRepository.saveAndFlush(user);
    }

    private String registrationBody(String doi, String hash, boolean accepted) {
        String acceptance = hash == null ? "null" : "{\"contentHash\":\"" + hash
                + "\",\"accepted\":" + accepted + "}";
        return """
                {"firstName":"Ana","paternalLastName":"Torres","maternalLastName":"Salas",
                 "doi":"%s","doiType":"DNI","birthDate":"01/01/1990","gender":"FEMALE",
                 "email":"%s@example.test","phone":"016543210","mobilePhone":"912345678",
                 "profession":"Abogada","legalAcceptance":%s}
                """.formatted(doi, doi.toLowerCase(), acceptance);
    }

    private String legalBody(String title, String html) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("title", title, "contentHtml", html));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String zeroHash() {
        return "0".repeat(64);
    }

    private record Csrf(Cookie cookie, String token) {
    }
}
