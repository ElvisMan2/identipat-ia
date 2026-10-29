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
import com.mnk.identipatia.guidance.dto.ProtectionGuideUpdateRequest;
import com.mnk.identipatia.guidance.model.ProtectionGuideStatus;
import com.mnk.identipatia.guidance.repository.ProtectionGuideRepository;
import com.mnk.identipatia.guidance.service.ProtectionGuideContentHasher;
import com.mnk.identipatia.guidance.service.ProtectionGuideSanitizer;
import com.mnk.identipatia.guidance.service.ProtectionGuideService;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class GuidanceIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_guidance_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired ProtectionGuideRepository guideRepository;
    @Autowired ProtectionGuideService guideService;
    @Autowired ProtectionGuideSanitizer sanitizer;
    @Autowired ProtectionGuideContentHasher hasher;
    @Autowired JwtService jwtService;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from analysis_report_contexts");
        jdbc.update("delete from analysis_results");
        jdbc.update("delete from ai_invocations");
        jdbc.update("delete from analysis_inputs");
        jdbc.update("delete from analyses");
        jdbc.update("delete from protection_guides where created_source='ADMIN'");
        jdbc.update("update protection_guides set status='ACTIVE', retired_at=null where created_source='SYSTEM'");
        jdbc.update("update protection_guide_version_counters set next_version=2");
        userRepository.deleteAll();
    }

    @Test
    void migrationSeedsSevenSafeActiveGuidesWithMatchingCanonicalHashesAndCounters() {
        var guides = guideRepository.findAll();
        assertThat(guides).hasSize(7).allSatisfy(guide -> {
            assertThat(guide.getVersion()).isEqualTo(1);
            assertThat(guide.getStatus()).isEqualTo(ProtectionGuideStatus.ACTIVE);
            assertThat(guide.getCreatedBy()).isNull();
            assertThat(guide.getNextStepsContentHtml()).isEqualTo(sanitizer.sanitize(guide.getNextStepsContentHtml()));
            assertThat(guide.getContentSha256()).isEqualTo(hasher.sha256(guide.getProtectionType(),
                    guide.getShortDescription(), guide.getNextStepsContentHtml()));
        });
        assertThat(jdbc.queryForObject(
                "select count(*) from protection_guide_version_counters where next_version=2", Integer.class))
                .isEqualTo(7);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("""
                insert into protection_guides(guide_id,protection_type,version,status,short_description,
                    next_steps_content_html,content_sha256,created_source,created_at,activated_at,last_verified_at)
                select gen_random_uuid(),protection_type,99,'ACTIVE',short_description,
                    next_steps_content_html,content_sha256,'SYSTEM',current_timestamp,current_timestamp,current_timestamp
                from protection_guides where protection_type='UTILITY_MODEL' and status='ACTIVE'
                """))).isNotNull();
    }

    @Test
    void adminLifecycleSanitizesPersistentHtmlAndGuidanceDynamicallyResolvesCurrentActive() throws Exception {
        User admin = admin();
        String token = jwtService.generateToken(admin.getDoi());
        UUID analysisId = completedAnalysis(admin, resultJson());

        mockMvc.perform(get("/analyses/{id}/guidance", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.guidanceVersion").value("analysis-guidance/1.0"))
                .andExpect(jsonPath("$.modalities[0].protectionType").value("UTILITY_MODEL"))
                .andExpect(jsonPath("$.modalities[0].guideVersion").value(1))
                .andExpect(jsonPath("$.modalities[0].nextSteps.contentHtml").isNotEmpty())
                .andExpect(jsonPath("$.modalities[1].protectionType").value("INVENTION_PATENT"))
                .andExpect(jsonPath("$.modalities[1].name").value("Patente de invención"))
                .andExpect(jsonPath("$.modalities[1].applicability").value("UNLIKELY"))
                .andExpect(jsonPath("$.modalities[1].applicabilityLabel").value("La relación es limitada"))
                .andExpect(jsonPath("$.modalities[1].guideVersion").value(1))
                .andExpect(jsonPath("$.modalities[1].shortDescription").isNotEmpty())
                .andExpect(jsonPath("$.modalities[1].lastVerifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.modalities[1].nextSteps").isEmpty());

        mockMvc.perform(get("/admin/protection-guides/active/UTILITY_MODEL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protectionType").value("UTILITY_MODEL"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String clone = mockMvc.perform(post("/admin/protection-guides/active/UTILITY_MODEL/draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID draftId = UUID.fromString(objectMapper.readTree(clone).path("guideId").asText());
        mockMvc.perform(put("/admin/protection-guides/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shortDescription":"Protege mejoras funcionales con una ventaja práctica claramente identificable.",
                                 "nextStepsContentHtml":"<h3 onclick='x'>Nueva orientación</h3><p>Seguro<script>alert(1)</script></p><a href='javascript:alert(1)'>sin destino</a>"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStepsContentHtml").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("script"))))
                .andExpect(jsonPath("$.nextStepsContentHtml").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("onclick"))))
                .andExpect(jsonPath("$.nextStepsContentHtml").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("javascript:"))));
        mockMvc.perform(post("/admin/protection-guides/{id}/activate", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/analyses/{id}/guidance", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalities[0].guideVersion").value(2))
                .andExpect(jsonPath("$.modalities[0].nextSteps.contentHtml").value(
                        org.hamcrest.Matchers.containsString("Nueva orientación")));
        mockMvc.perform(put("/admin/protection-guides/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortDescription\":\"Descripción suficientemente larga para ser válida.\",\"nextStepsContentHtml\":\"<p>x</p>\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTION_GUIDE_IMMUTABLE"));
    }

    @Test
    void statesOwnershipAuthenticationAndUnsupportedResultsUseControlledErrors() throws Exception {
        User owner = admin();
        User other = admin();
        String ownerToken = jwtService.generateToken(owner.getDoi());
        UUID pending = analysis(owner, AnalysisStatus.RECEIVED);
        UUID failed = analysis(owner, AnalysisStatus.FAILED);
        UUID completed = completedAnalysis(owner, resultJson());

        mockMvc.perform(get("/analyses/{id}/guidance", pending)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUIDANCE_NOT_READY"));
        mockMvc.perform(get("/analyses/{id}/guidance", failed)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUIDANCE_UNAVAILABLE"));
        mockMvc.perform(get("/analyses/{id}/guidance", completed)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(other.getDoi())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/analyses/{id}/guidance", completed)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manipulated-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/protection-guides")).andExpect(status().isUnauthorized());

        StoredAnalysisResult stored = resultRepository.findById(completed).orElseThrow();
        stored.setSchemaVersion("analysis-result/9.0");
        stored.setResultJson(objectMapper.readTree("{\"schemaVersion\":\"analysis-result/9.0\"}"));
        resultRepository.saveAndFlush(stored);
        mockMvc.perform(get("/analyses/{id}/guidance", completed)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDANCE_UNSUPPORTED_RESULT_VERSION"));
    }

    @Test
    void incompleteCatalogFailsAtomicallyWhileAnAnalysisWithoutOptionsReturnsEmptyModalities() throws Exception {
        User admin = admin();
        String token = jwtService.generateToken(admin.getDoi());
        UUID withOptions = completedAnalysis(admin, resultJson());
        jdbc.update("""
                update protection_guides set status='RETIRED', retired_at=current_timestamp
                where protection_type='UTILITY_MODEL' and status='ACTIVE'
                """);

        mockMvc.perform(get("/analyses/{id}/guidance", withOptions)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GUIDANCE_CATALOG_INCOMPLETE"));

        JsonNode empty = objectMapper.readTree("""
                {"schemaVersion":"analysis-result/2.0","summary":"Resumen.","protectionOptions":[],
                 "patentScreening":{"applicable":false,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}},
                 "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No indicado.","missingInformation":[]},
                 "observations":[],"warnings":[]}
                """);
        UUID withoutOptions = completedAnalysis(admin, empty);
        mockMvc.perform(get("/analyses/{id}/guidance", withoutOptions)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalities").isEmpty());
    }

    @Test
    void historicalV1UsesOnlyExplicitSupportedProtectionOptions() throws Exception {
        User admin = admin();
        String token = jwtService.generateToken(admin.getDoi());
        JsonNode historical = objectMapper.readTree("""
                {"schemaVersion":"analysis-result/1.0","summary":"Resumen histórico",
                 "patentabilityAssessment":{"outcome":"INSUFFICIENT_INFORMATION","rationale":"Faltan datos."},
                 "protectionOptions":[{"type":"COPYRIGHT","applicability":"POSSIBLE","rationale":"Expresión original."}],
                 "observations":[],"warnings":[]}
                """);
        UUID analysisId = completedAnalysis(admin, historical);

        mockMvc.perform(get("/analyses/{id}/guidance", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modalities[0].protectionType").value("COPYRIGHT"))
                .andExpect(jsonPath("$.modalities[0].applicability").value("POSSIBLE"))
                .andExpect(jsonPath("$.modalities[0].nextSteps.contentHtml").isNotEmpty());
    }

    @Test
    void concurrentCreationAndActivationAreSerializedPerModality() throws Exception {
        User admin = admin();
        var authentication = authentication(admin);
        var request = new ProtectionGuideCreateRequest(ProtectionType.TRADE_SECRET,
                "Protege información confidencial bajo medidas razonables de reserva y control.",
                "<p>Contenido administrado seguro.</p>");
        CountDownLatch createStart = new CountDownLatch(1);
        UUID first;
        UUID second;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { createStart.await(); return guideService.create(request, authentication); });
            var b = pool.submit(() -> { createStart.await(); return guideService.create(request, authentication); });
            createStart.countDown();
            var firstResponse = a.get();
            var secondResponse = b.get();
            assertThat(List.of(firstResponse.version(), secondResponse.version())).containsExactlyInAnyOrder(2L, 3L);
            first = firstResponse.guideId();
            second = secondResponse.guideId();
        }

        CountDownLatch activateStart = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { activateStart.await(); return guideService.activate(first, authentication); });
            var b = pool.submit(() -> { activateStart.await(); return guideService.activate(second, authentication); });
            activateStart.countDown();
            a.get();
            b.get();
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from protection_guides
                where protection_type='TRADE_SECRET' and status='ACTIVE'
                """, Integer.class)).isEqualTo(1);
    }

    private User admin() {
        User user = new User();
        user.setDoi("F18-" + UUID.randomUUID());
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

    private UUID analysis(User owner, AnalysisStatus status) {
        Instant now = Instant.now();
        Analysis analysis = new Analysis();
        analysis.setAnalysisId(UUID.randomUUID());
        analysis.setUserId(owner.getUserId());
        analysis.setInputType(AnalysisInputType.TEXT);
        analysis.setStatus(status);
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        if (status == AnalysisStatus.FAILED) {
            analysis.setFailedAt(now);
            analysis.setFailureCode("WORKER_PROCESSING_ERROR");
        }
        return analysisRepository.saveAndFlush(analysis).getAnalysisId();
    }

    private UUID completedAnalysis(User owner, JsonNode json) {
        UUID id = analysis(owner, AnalysisStatus.RECEIVED);
        Instant now = Instant.now();
        Analysis analysis = analysisRepository.findById(id).orElseThrow();
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(now);
        analysis.setUpdatedAt(now);
        analysisRepository.saveAndFlush(analysis);
        StoredAnalysisResult stored = new StoredAnalysisResult();
        stored.setAnalysisId(id);
        stored.setSchemaVersion(json.path("schemaVersion").asText());
        stored.setResultJson(json);
        stored.setCreatedAt(now);
        resultRepository.saveAndFlush(stored);
        return id;
    }

    private JsonNode resultJson() throws Exception {
        return objectMapper.readTree("""
                {"schemaVersion":"analysis-result/2.0","summary":"Resumen.",
                 "protectionOptions":[
                   {"type":"UTILITY_MODEL","applicability":"LIKELY","protectedSubjectMatter":"Mejora funcional.",
                    "rationale":"Existe relación.","legalBasis":[{"instrument":"DECISION_486","articles":["81"]}]},
                   {"type":"INVENTION_PATENT","applicability":"UNLIKELY","protectedSubjectMatter":"Solución técnica.",
                    "rationale":"Relación limitada.","legalBasis":[{"instrument":"DECISION_486","articles":["14"]}]}
                 ],
                 "patentScreening":{"applicable":false,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}},
                 "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No indicado.","missingInformation":[]},
                 "observations":[],"warnings":[]}
                """);
    }
}
