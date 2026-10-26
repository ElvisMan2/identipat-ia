package com.mnk.identipatia.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.reporting.dto.DisclaimerUpsertRequest;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimerStatus;
import com.mnk.identipatia.reporting.repository.AnalysisReportContextRepository;
import com.mnk.identipatia.reporting.repository.ReportDisclaimerRepository;
import com.mnk.identipatia.reporting.service.AnalysisReportContextService;
import com.mnk.identipatia.reporting.service.ReportDisclaimerService;
import com.mnk.identipatia.service.JwtService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class ReportingIntegrationTest {
    private static final UUID SEED_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_reporting_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired AnalysisReportContextRepository contextRepository;
    @Autowired ReportDisclaimerRepository disclaimerRepository;
    @Autowired ReportDisclaimerService disclaimerService;
    @Autowired AnalysisReportContextService contextService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JwtService jwtService;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from analysis_report_contexts");
        jdbc.update("delete from analysis_results");
        jdbc.update("delete from ai_invocations");
        jdbc.update("delete from analysis_inputs");
        jdbc.update("delete from analyses");
        jdbc.update("delete from report_disclaimers where disclaimer_id <> ?", SEED_ID);
        jdbc.update("update report_disclaimers set status='ACTIVE', retired_at=null where disclaimer_id=?", SEED_ID);
        jdbc.queryForObject("select setval('report_disclaimer_version_seq', 1, true)", Long.class);
        userRepository.deleteAll();
    }

    @Test
    void migrationSeedsSystemDisclaimerAndEnforcesSingleActiveAndForeignKeys() {
        var seed = disclaimerRepository.findById(SEED_ID).orElseThrow();
        assertThat(seed.getVersion()).isEqualTo(1);
        assertThat(seed.getStatus()).isEqualTo(ReportDisclaimerStatus.ACTIVE);
        assertThat(seed.getCreatedBy()).isNull();
        assertThat(seed.getActivatedBy()).isNull();
        assertThat(seed.getContentSha256())
                .isEqualTo("4ec5ec3db0098b29667c1e3ba03359bfc1bc46542b0931dfb19937775e128967");
        assertThat(jdbc.queryForObject("select count(*) from report_disclaimers where status='ACTIVE'", Integer.class))
                .isEqualTo(1);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("""
                insert into analysis_report_contexts(analysis_id, disclaimer_id, report_version, created_at)
                values (?, ?, 'diagnostic-report/1.0', current_timestamp)
                """, UUID.randomUUID(), UUID.randomUUID()))).isNotNull();
    }

    @Test
    void adminCanCreateCloneEditAndActivateWithJwtAuditWhileImmutableVersionsRejectEdits() throws Exception {
        User admin = admin();
        String token = jwtService.generateToken(admin.getDoi());
        String createdBody = mockMvc.perform(post("/admin/report-disclaimers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" Nuevo \",\"content\":\" Contenido administrado \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdSource").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        UUID draftId = UUID.fromString(objectMapper.readTree(createdBody).path("id").asText());
        assertThat(disclaimerRepository.findById(draftId).orElseThrow().getCreatedBy()).isEqualTo(admin.getUserId());

        mockMvc.perform(put("/admin/report-disclaimers/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Editado\",\"content\":\"Contenido editado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Editado"));
        mockMvc.perform(post("/admin/report-disclaimers/{id}/activate", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(disclaimerRepository.findById(SEED_ID).orElseThrow().getStatus())
                .isEqualTo(ReportDisclaimerStatus.RETIRED);

        mockMvc.perform(put("/admin/report-disclaimers/{id}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No\",\"content\":\"No\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISCLAIMER_IMMUTABLE"));
        mockMvc.perform(post("/admin/report-disclaimers/active/draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Editado"))
                .andExpect(jsonPath("$.content").value("Contenido editado"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void adminEndpointsRequireJwtAndRejectNonAdminAuthority() throws Exception {
        mockMvc.perform(get("/admin/report-disclaimers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/report-disclaimers").with(user("standard").roles("STANDARD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jsonAndPdfUseHistoricalDisclaimerAndReturnRequiredHeaders() throws Exception {
        User admin = admin();
        UUID analysisId = completedAnalysis(admin, v2ResultJson(true, true));
        String token = jwtService.generateToken(admin.getDoi());

        mockMvc.perform(get("/analyses/{id}/report", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.reportVersion").value("diagnostic-report/1.0"))
                .andExpect(jsonPath("$.sourceResultVersion").value("analysis-result/2.0"))
                .andExpect(jsonPath("$.summary")
                        .value("patente de invención — podría corresponder. Resumen técnico orientativo."))
                .andExpect(jsonPath("$.protectionOptions[0].name").value("Patente de invención"))
                .andExpect(jsonPath("$.protectionOptions[0].patentScreening.title")
                        .value("Revisión específica de la vía de patente de invención"))
                .andExpect(jsonPath("$.patentScreening").isEmpty())
                .andExpect(jsonPath("$.disclaimer.version").value(1));

        byte[] pdf = mockMvc.perform(get("/analyses/{id}/report.pdf", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"identipat-diagnostico-" + analysisId + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf).startsWith('%', 'P', 'D', 'F', '-');
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Patente de invención", "podría corresponder", "Importante",
                            analysisId.toString(), "Versión 1")
                    .doesNotContain("INVENTION_PATENT", "POSSIBLE");
        }

        User otherAdmin = admin();
        mockMvc.perform(get("/analyses/{id}/report.pdf", analysisId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(otherAdmin.getDoi()))
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));

        UUID next = disclaimerService.create(new DisclaimerUpsertRequest("Nueva", "Versión posterior"),
                auth(admin)).id();
        disclaimerService.activate(next, auth(admin));
        mockMvc.perform(get("/analyses/{id}/report", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclaimer.version").value(1));
    }

    @Test
    void utilityModelOnlyAssociatesSingleScreeningInJsonAndPdfAndSanitizesNarratives() throws Exception {
        User admin = admin();
        UUID analysisId = completedAnalysis(admin, utilityModelObservedResultJson());
        String token = jwtService.generateToken(admin.getDoi());

        String jsonBody = mockMvc.perform(get("/analyses/{id}/report", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("signo distintivo — se relaciona claramente, "
                        + "derecho de autor — podría corresponder, diseño industrial — se relaciona claramente "
                        + "y modelo de utilidad — se relaciona claramente."))
                .andExpect(jsonPath("$.protectionOptions[3].name").value("Modelo de utilidad"))
                .andExpect(jsonPath("$.protectionOptions[3].patentScreening.title")
                        .value("Revisión específica de la vía de modelo de utilidad"))
                .andExpect(jsonPath("$.protectionOptions[3].patentScreening.article15.assessmentLabel")
                        .value("No se identifican coincidencias potenciales"))
                .andExpect(jsonPath("$.protectionOptions[3].patentScreening.article20.assessmentLabel")
                        .value("No se identifican coincidencias potenciales"))
                .andExpect(jsonPath("$.patentScreening").isEmpty())
                .andExpect(jsonPath("$.protectionOptions[1].rationale")
                        .value("La relación podría corresponder requiere revisión."))
                .andExpect(jsonPath("$.observations[0]")
                        .value("Observación COPYRIGHTABLE y signo distintivo."))
                .andExpect(jsonPath("$.warnings[0]")
                        .value("Advertencia relación limitada y PRELIKELY."))
                .andReturn().getResponse().getContentAsString();
        assertThat(jsonBody).doesNotContain("patente no aparece como modalidad positiva");

        byte[] pdf = mockMvc.perform(get("/analyses/{id}/report.pdf", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("Signo distintivo", "Derecho de autor", "Diseño industrial", "Modelo de utilidad",
                            "Revisión específica de la vía de modelo de utilidad", "Artículo 15", "Artículo 20",
                            "No se identifican coincidencias potenciales")
                    .containsOnlyOnce("Este screening se limita")
                    .doesNotContain("patente no aparece como modalidad positiva", "DISTINCTIVE_SIGN",
                            "INDUSTRIAL_DESIGN", "UTILITY_MODEL", "(LIKELY)", "(POSSIBLE)");
            assertThat(text.indexOf("Modelo de utilidad"))
                    .isLessThan(text.indexOf("Revisión específica de la vía de modelo de utilidad"));
        }
    }

    @Test
    void missingContextAndUnknownResultVersionUseControlledErrors() throws Exception {
        User admin = admin();
        String token = jwtService.generateToken(admin.getDoi());
        UUID missingContext = completedAnalysis(admin, v2ResultJson(false, false));
        contextRepository.deleteById(missingContext);
        mockMvc.perform(get("/analyses/{id}/report", missingContext)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REPORT_CONTEXT_UNAVAILABLE"));

        UUID unsupported = completedAnalysis(admin, v2ResultJson(false, false));
        StoredAnalysisResult stored = resultRepository.findById(unsupported).orElseThrow();
        stored.setSchemaVersion("analysis-result/9.0");
        stored.setResultJson(objectMapper.readTree("{\"schemaVersion\":\"analysis-result/9.0\"}"));
        resultRepository.saveAndFlush(stored);
        mockMvc.perform(get("/analyses/{id}/report", unsupported)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_UNSUPPORTED_RESULT_VERSION"));
    }

    @Test
    void reportStatesOwnershipAndBearerPrecedenceAreEnforced() throws Exception {
        User owner = admin();
        User other = admin();
        String ownerToken = jwtService.generateToken(owner.getDoi());
        UUID pending = analysis(owner, AnalysisStatus.RECEIVED);
        UUID failed = analysis(owner, AnalysisStatus.FAILED);

        mockMvc.perform(get("/analyses/{id}/report", pending)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_NOT_READY"));
        mockMvc.perform(get("/analyses/{id}/report", failed)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_UNAVAILABLE"));
        mockMvc.perform(get("/analyses/{id}/report", pending)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(other.getDoi())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/analyses/{id}/report", pending)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manipulated-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentActivationsNeverLeaveTwoActive() throws Exception {
        User admin = admin();
        var authentication = auth(admin);
        UUID first = disclaimerService.create(new DisclaimerUpsertRequest("Primero", "Contenido primero"),
                authentication).id();
        UUID second = disclaimerService.create(new DisclaimerUpsertRequest("Segundo", "Contenido segundo"),
                authentication).id();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return disclaimerService.activate(first, authentication); });
            var b = pool.submit(() -> { start.await(); return disclaimerService.activate(second, authentication); });
            start.countDown();
            a.get();
            b.get();
        }
        assertThat(jdbc.queryForObject("select count(*) from report_disclaimers where status='ACTIVE'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void activationRacingReportBindingAlwaysProducesValidHistoricalContext() throws Exception {
        User admin = admin();
        UUID analysisId = analysis(admin, AnalysisStatus.ANALYZING);
        var authentication = auth(admin);
        UUID draft = disclaimerService.create(new DisclaimerUpsertRequest("Nueva", "Nueva versión"),
                authentication).id();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var activation = pool.submit(() -> { start.await(); return disclaimerService.activate(draft, authentication); });
            var binding = pool.submit(() -> {
                start.await();
                return transactionTemplate.execute(status -> contextService.bindToActiveDisclaimer(
                        analysisId, Instant.now()));
            });
            start.countDown();
            activation.get();
            binding.get();
        }
        AnalysisReportContext context = contextRepository.findById(analysisId).orElseThrow();
        assertThat(disclaimerRepository.findById(context.getDisclaimerId())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from report_disclaimers where status='ACTIVE'", Integer.class))
                .isEqualTo(1);
    }

    private User admin() {
        User user = new User();
        user.setDoi("F17-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType("ADMIN");
        user.setStatus("A");
        user.setPassword("unused-for-direct-jwt-test");
        return userRepository.saveAndFlush(user);
    }

    private UsernamePasswordAuthenticationToken auth(User admin) {
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

    private UUID completedAnalysis(User owner, JsonNode resultJson) {
        UUID id = analysis(owner, AnalysisStatus.RECEIVED);
        Instant now = Instant.now();
        Analysis analysis = analysisRepository.findById(id).orElseThrow();
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(now);
        analysis.setUpdatedAt(now);
        analysisRepository.saveAndFlush(analysis);
        StoredAnalysisResult result = new StoredAnalysisResult();
        result.setAnalysisId(id);
        result.setSchemaVersion(resultJson.path("schemaVersion").asText());
        result.setResultJson(resultJson);
        result.setCreatedAt(now);
        resultRepository.saveAndFlush(result);
        transactionTemplate.executeWithoutResult(status -> contextService.bindToActiveDisclaimer(id, now));
        return id;
    }

    private JsonNode v2ResultJson(boolean screeningApplicable, boolean withPatent) throws Exception {
        String option = withPatent ? """
                [{"type":"INVENTION_PATENT","applicability":"POSSIBLE",
                  "protectedSubjectMatter":"Mecanismo con precisión y señalización",
                  "rationale":"La descripción contiene una solución técnica.",
                  "legalBasis":[{"instrument":"DECISION_486","articles":["14"]}]}]
                """ : "[]";
        return objectMapper.readTree("""
                {"schemaVersion":"analysis-result/2.0","summary":"INVENTION_PATENT (POSSIBLE). Resumen técnico orientativo.",
                 "protectionOptions":%s,
                 "patentScreening":{"applicable":%s,
                   "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                   "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}},
                 "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No hay indicios.","missingInformation":[]},
                 "observations":["Aspecto a considerar"],"warnings":["Advertencia del caso"]}
                """.formatted(option, screeningApplicable));
    }

    private JsonNode utilityModelObservedResultJson() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/2.0",
                  "summary":"DISTINCTIVE_SIGN (LIKELY), COPYRIGHT (POSSIBLE), INDUSTRIAL_DESIGN (LIKELY) y UTILITY_MODEL (LIKELY).",
                  "protectionOptions":[
                    {"type":"DISTINCTIVE_SIGN","applicability":"LIKELY",
                     "protectedSubjectMatter":"El signo empleado","rationale":"La relación LIKELY es directa.",
                     "legalBasis":[{"instrument":"DECISION_486","articles":["134"]}]},
                    {"type":"COPYRIGHT","applicability":"POSSIBLE",
                     "protectedSubjectMatter":"La expresión original","rationale":"La relación POSSIBLE requiere revisión.",
                     "legalBasis":[{"instrument":"DECISION_351","articles":["4"]}]},
                    {"type":"INDUSTRIAL_DESIGN","applicability":"LIKELY",
                     "protectedSubjectMatter":"La apariencia particular","rationale":"La relación LIKELY es directa.",
                     "legalBasis":[{"instrument":"DECISION_486","articles":["113"]}]},
                    {"type":"UTILITY_MODEL","applicability":"LIKELY",
                     "protectedSubjectMatter":"La mejora funcional","rationale":"UTILITY_MODEL presenta relación LIKELY.",
                     "legalBasis":[{"instrument":"DECISION_486","articles":["81"]}]}
                  ],
                  "patentScreening":{"applicable":true,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias potenciales.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias potenciales.","matches":[]}},
                  "geneticResourceAccess":{"assessment":"NOT_INDICATED","rationale":"No hay indicios.","missingInformation":[]},
                  "observations":["Observación COPYRIGHTABLE y DISTINCTIVE_SIGN."],
                  "warnings":["Advertencia UNLIKELY y PRELIKELY."]
                }
                """);
    }
}
