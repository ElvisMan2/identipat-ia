package com.mnk.identipatia.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.model.GuidanceView;
import com.mnk.identipatia.guidance.repository.GuidanceViewRepository;
import com.mnk.identipatia.legal.model.LegalDocumentHistory;
import com.mnk.identipatia.legal.model.UserTermsAcceptance;
import com.mnk.identipatia.legal.repository.LegalDocumentHistoryRepository;
import com.mnk.identipatia.legal.repository.UserTermsAcceptanceRepository;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.StandardSessionStatus;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportRow;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.repository.AnalysisReportContextRepository;
import com.mnk.identipatia.reporting.service.AnalysisReportContextService;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetException;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetService;
import com.mnk.identipatia.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OperationalAnalysisDatasetIntegrationTest {
    private static final UUID DISCLAIMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String LEGAL_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_operational_dataset_test");

    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired LegalDocumentHistoryRepository legalHistoryRepository;
    @Autowired UserTermsAcceptanceRepository acceptanceRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired AnalysisReportContextRepository contextRepository;
    @Autowired GuidanceViewRepository guidanceViewRepository;
    @Autowired OperationalAnalysisDatasetService datasetService;
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from guidance_views");
        jdbc.update("delete from analysis_report_contexts");
        jdbc.update("delete from analysis_results");
        jdbc.update("delete from ai_invocations");
        jdbc.update("delete from analysis_inputs");
        jdbc.update("delete from analyses");
        jdbc.update("delete from user_terms_acceptances");
        jdbc.update("delete from standard_sessions");
        userRepository.deleteAll();
    }

    @Test
    void limaCalendarBoundsOptionalCriteriaAndStableOrderingAreAppliedInPostgresql() {
        User user = user("Paterno", "Materno");
        LocalDate day = LocalDate.of(2026, 11, 3);
        OperationalAnalysisReportCriteria sameDay = new OperationalAnalysisReportCriteria(day, day);
        Instant start = Instant.parse("2026-11-03T05:00:00Z");
        Instant end = Instant.parse("2026-11-04T05:00:00Z");

        analysis(user, uuid(1), start.minusMillis(1), AnalysisStatus.RECEIVED, false);
        analysis(user, uuid(3), start, AnalysisStatus.RECEIVED, false);
        analysis(user, uuid(2), start, AnalysisStatus.RECEIVED, false);
        analysis(user, uuid(4), end.minusMillis(1), AnalysisStatus.RECEIVED, false);
        analysis(user, uuid(5), end, AnalysisStatus.RECEIVED, false);

        assertThat(sameDay.fromInclusive()).isEqualTo(start);
        assertThat(sameDay.toExclusive()).isEqualTo(end);
        assertThat(datasetService.build(sameDay)).extracting(OperationalAnalysisReportRow::analysisId)
                .containsExactly(uuid(2), uuid(3), uuid(4));
        assertThat(datasetService.build(new OperationalAnalysisReportCriteria(day, null)))
                .extracting(OperationalAnalysisReportRow::analysisId)
                .containsExactly(uuid(2), uuid(3), uuid(4), uuid(5));
        assertThat(datasetService.build(new OperationalAnalysisReportCriteria(null, day)))
                .extracting(OperationalAnalysisReportRow::analysisId)
                .containsExactly(uuid(1), uuid(2), uuid(3), uuid(4));
        assertThat(datasetService.build(new OperationalAnalysisReportCriteria(null, null)))
                .hasSize(5);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OperationalAnalysisReportCriteria(day.plusDays(1), day));
    }

    @Test
    void oneRowPerAnalysisUsesCurrentProfileCleanLastNamesOriginsAndEmptyStateFields() {
        User shared = user(" Paterno ", " Materno ");
        shared.setFirstName("Nombre");
        shared.setProfession("Inicial");
        shared.setEmail("inicial@example.test");
        userRepository.saveAndFlush(shared);
        Instant now = Instant.parse("2026-11-03T12:00:00Z");
        Analysis received = analysis(shared, UUID.randomUUID(), now, AnalysisStatus.RECEIVED, false);
        Analysis failed = analysis(shared, UUID.randomUUID(), now.plusSeconds(1), AnalysisStatus.FAILED, false);
        reportContext(received);

        User onlyPaternal = user("SoloPaterno", "  ");
        Analysis analyzing = analysis(onlyPaternal, UUID.randomUUID(), now.plusSeconds(2),
                AnalysisStatus.ANALYZING, true);
        User onlyMaternal = user(null, "SoloMaterno");
        Analysis preprocessing = analysis(onlyMaternal, UUID.randomUUID(), now.plusSeconds(3),
                AnalysisStatus.PREPROCESSING, false);
        User noLastNames = user(null, " ");
        Analysis noNames = analysis(noLastNames, UUID.randomUUID(), now.plusSeconds(4),
                AnalysisStatus.RECEIVED, false);

        shared.setProfession("Actual");
        shared.setEmail("actual@example.test");
        shared.setUserType("STANDARD");
        userRepository.saveAndFlush(shared);

        List<OperationalAnalysisReportRow> rows = datasetService.build(
                new OperationalAnalysisReportCriteria(null, null));
        assertThat(rows).hasSize(5);
        assertThat(rows.stream().filter(row -> row.firstName().equals("Nombre"))).hasSize(2);

        OperationalAnalysisReportRow receivedRow = row(rows, received.getAnalysisId());
        assertThat(receivedRow.lastNames()).isEqualTo("Paterno Materno");
        assertThat(receivedRow.profession()).isEqualTo("Actual");
        assertThat(receivedRow.email()).isEqualTo("actual@example.test");
        assertThat(receivedRow.analysisOrigin()).isEqualTo(OperationalAnalysisOrigin.ADMIN);

        OperationalAnalysisReportRow standardRow = row(rows, analyzing.getAnalysisId());
        assertThat(standardRow.lastNames()).isEqualTo("SoloPaterno");
        assertThat(standardRow.analysisOrigin()).isEqualTo(OperationalAnalysisOrigin.STANDARD);
        assertThat(row(rows, preprocessing.getAnalysisId()).lastNames()).isEqualTo("SoloMaterno");
        assertThat(row(rows, noNames.getAnalysisId()).lastNames()).isEmpty();

        assertThat(List.of(received, failed, analyzing, preprocessing, noNames))
                .allSatisfy(analysis -> assertEmptyResult(row(rows, analysis.getAnalysisId())));
    }

    @Test
    void v1V2ReportsMissingContextAndGuidanceExistenceProduceCompleteRowsWithoutDuplicates() throws Exception {
        User user = user("Paterno", "Materno");
        Instant base = Instant.parse("2026-11-03T15:00:00Z");
        Analysis v1 = completed(user, UUID.randomUUID(), base, v1Result(), true);
        Analysis v2 = completed(user, UUID.randomUUID(), base.plusSeconds(1), v2Result(), true);
        Analysis noContext = completed(user, UUID.randomUUID(), base.plusSeconds(2), v2Result(), false);

        guidance(v2.getAnalysisId(), ProtectionType.INVENTION_PATENT);
        guidance(noContext.getAnalysisId(), ProtectionType.INVENTION_PATENT);
        guidance(noContext.getAnalysisId(), ProtectionType.COPYRIGHT);

        List<OperationalAnalysisReportRow> rows = datasetService.build(
                new OperationalAnalysisReportCriteria(null, null));
        assertThat(rows).hasSize(3);

        OperationalAnalysisReportRow v1Row = row(rows, v1.getAnalysisId());
        assertThat(v1Row.conclusion()).isEqualTo("Resumen histórico.");
        assertThat(v1Row.identifiedModalities())
                .containsExactly("Derecho de autor", "Otra modalidad");
        assertThat(v1Row.applicabilityByModality())
                .extracting(value -> value.modality() + ": " + value.applicability())
                .containsExactly("Derecho de autor: Se relaciona claramente",
                        "Otra modalidad: Podría corresponder");
        assertThat(v1Row.reportPdfAvailable()).isTrue();
        assertThat(v1Row.reportText())
                .contains("Resumen:\n", "Modalidades de protección:", "Revisión de patente:\n",
                        "Observaciones:\n", "Advertencias:\n", "Disclaimer:\nImportante:")
                .doesNotContain("COPYRIGHT", "OTHER", "LIKELY", "POSSIBLE", "INSUFFICIENT_INFORMATION");
        assertThat(v1Row.guidanceConsulted()).isFalse();

        OperationalAnalysisReportRow v2Row = row(rows, v2.getAnalysisId());
        assertThat(v2Row.conclusion())
                .isEqualTo("patente de invención — podría corresponder y derecho de autor.");
        assertThat(v2Row.identifiedModalities())
                .containsExactly("Patente de invención", "Derecho de autor");
        assertThat(v2Row.reportPdfAvailable()).isTrue();
        assertThat(v2Row.reportText())
                .contains("Revisión de patente:\n", "Artículo 15", "Artículo 20",
                        "Recursos genéticos:\n", "Información faltante:",
                        "requiere revisión especializada",
                        "Observaciones:\n- Observación presentada.",
                        "Advertencias:\n- Advertencia presentada.")
                .containsOnlyOnce("Revisión de patente:\n")
                .containsOnlyOnce("Recursos genéticos:\n")
                .doesNotContain("INVENTION_PATENT", "COPYRIGHT", "POSSIBLE",
                        "POTENTIALLY_REQUIRED");
        assertThat(v2Row.guidanceConsulted()).isTrue();

        OperationalAnalysisReportRow noContextRow = row(rows, noContext.getAnalysisId());
        assertThat(noContextRow.conclusion()).isNotBlank();
        assertThat(noContextRow.identifiedModalities()).hasSize(2);
        assertThat(noContextRow.reportText()).isNull();
        assertThat(noContextRow.reportPdfAvailable()).isFalse();
        assertThat(noContextRow.guidanceConsulted()).isTrue();
    }

    @Test
    void unknownOrMismatchedResultVersionFailsInsteadOfCreatingMisleadingRows() throws Exception {
        User user = user("Paterno", "Materno");
        Analysis analysis = completed(user, UUID.randomUUID(), Instant.parse("2026-11-03T16:00:00Z"),
                v2Result(), false);
        StoredAnalysisResult stored = resultRepository.findById(analysis.getAnalysisId()).orElseThrow();
        stored.setSchemaVersion("analysis-result/9.0");
        stored.setResultJson(objectMapper.readTree("{\"schemaVersion\":\"analysis-result/9.0\"}"));
        resultRepository.saveAndFlush(stored);

        assertThatThrownBy(() -> datasetService.build(new OperationalAnalysisReportCriteria(null, null)))
                .isInstanceOf(OperationalAnalysisDatasetException.class)
                .hasMessageContaining("invalid or unsupported")
                .hasMessageContaining(analysis.getAnalysisId().toString());

        stored.setSchemaVersion("analysis-result/1.0");
        stored.setResultJson(v2Result());
        resultRepository.saveAndFlush(stored);
        assertThatThrownBy(() -> datasetService.build(new OperationalAnalysisReportCriteria(null, null)))
                .isInstanceOf(OperationalAnalysisDatasetException.class)
                .hasMessageContaining("invalid or unsupported");
    }

    @Test
    void adminHttpExportUsesA1DatasetPreservesOrderAndPassesCalendarFilters() throws Exception {
        User admin = user("Paterno", "Materno");
        admin.setDoi("ADMIN-CSV");
        admin.setPassword("unused-for-direct-jwt-test");
        userRepository.saveAndFlush(admin);

        UUID firstId = uuid(21);
        UUID secondId = uuid(22);
        UUID outsideId = uuid(23);
        completed(admin, firstId, Instant.parse("2026-11-03T06:00:00Z"), v2Result(), true);
        analysis(admin, secondId, Instant.parse("2026-11-03T07:00:00Z"),
                AnalysisStatus.RECEIVED, false);
        analysis(admin, outsideId, Instant.parse("2026-11-04T05:00:00Z"),
                AnalysisStatus.RECEIVED, false);

        byte[] bytes = mockMvc.perform(get("/admin/analyses/export.csv")
                        .param("from", "2026-11-03")
                        .param("to", "2026-11-03")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(admin.getDoi())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"identipat-analisis_2026-11-03_2026-11-03.csv\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String headerRow = csv.substring(1, csv.indexOf("\r\n"));
        assertThat(headerRow.split(",", -1)).hasSize(21);
        assertThat(headerRow).contains("\"Id Analisis\"", "\"Orientacion Consultada\"")
                .doesNotContain("userId", "sessionId", "provider", "model", "tokens");
        assertThat(csv).contains(firstId.toString(), secondId.toString(), "\"COMPLETADO\"")
                .contains("requiere revisión especializada")
                .doesNotContain(outsideId.toString(), "POTENTIALLY_REQUIRED");
        assertThat(csv.indexOf(firstId.toString())).isLessThan(csv.indexOf(secondId.toString()));
    }

    private User user(String paternalLastName, String maternalLastName) {
        User user = new User();
        user.setFirstName("Perfil-" + UUID.randomUUID());
        user.setPaternalLastName(paternalLastName);
        user.setMaternalLastName(maternalLastName);
        user.setDoi("DNI-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setGender("OTHER");
        user.setEmail("perfil@example.test");
        user.setPhone("01-0000000");
        user.setMobilePhone("900000000");
        user.setProfession("Profesión");
        user.setStatus("A");
        user.setUserType("ADMIN");
        user.setPassword("unused");
        return userRepository.saveAndFlush(user);
    }

    private Analysis analysis(User user, UUID id, Instant createdAt, AnalysisStatus status, boolean standard) {
        Analysis analysis = new Analysis();
        analysis.setAnalysisId(id);
        analysis.setUserId(user.getUserId());
        if (standard) {
            StandardContext context = standardContext(user, createdAt);
            analysis.setSessionId(context.sessionId());
            analysis.setTermsAcceptanceId(context.acceptanceId());
        }
        analysis.setInputType(AnalysisInputType.TEXT);
        analysis.setStatus(status);
        analysis.setCreatedAt(createdAt);
        analysis.setUpdatedAt(createdAt);
        if (status == AnalysisStatus.ANALYZING || status == AnalysisStatus.COMPLETED) {
            analysis.setStartedAt(createdAt);
        }
        if (status == AnalysisStatus.COMPLETED) {
            analysis.setCompletedAt(createdAt.plus(1, ChronoUnit.SECONDS));
        }
        if (status == AnalysisStatus.FAILED) {
            analysis.setFailedAt(createdAt.plus(1, ChronoUnit.SECONDS));
            analysis.setFailureCode("WORKER_PROCESSING_ERROR");
        }
        return analysisRepository.saveAndFlush(analysis);
    }

    private Analysis completed(User user, UUID id, Instant createdAt, JsonNode result, boolean reportContext) {
        Analysis analysis = analysis(user, id, createdAt, AnalysisStatus.COMPLETED, false);
        StoredAnalysisResult stored = new StoredAnalysisResult();
        stored.setAnalysisId(id);
        stored.setSchemaVersion(result.path("schemaVersion").asText());
        stored.setResultJson(result);
        stored.setCreatedAt(createdAt.plusSeconds(1));
        resultRepository.saveAndFlush(stored);
        if (reportContext) {
            reportContext(analysis);
        }
        return analysis;
    }

    private void reportContext(Analysis analysis) {
        AnalysisReportContext context = new AnalysisReportContext();
        context.setAnalysisId(analysis.getAnalysisId());
        context.setDisclaimerId(DISCLAIMER_ID);
        context.setReportVersion(AnalysisReportContextService.REPORT_VERSION);
        context.setCreatedAt(analysis.getUpdatedAt().plusSeconds(1));
        contextRepository.saveAndFlush(context);
    }

    private StandardContext standardContext(User user, Instant now) {
        LegalDocumentHistory history = legalHistoryRepository.findById(LEGAL_HASH).orElseGet(() -> {
            LegalDocumentHistory created = new LegalDocumentHistory();
            created.setContentSha256(LEGAL_HASH);
            created.setTitle("Términos de prueba");
            created.setContentHtml("<p>Contenido</p>");
            created.setCapturedAt(now);
            return legalHistoryRepository.saveAndFlush(created);
        });
        UUID acceptanceId = UUID.randomUUID();
        UserTermsAcceptance acceptance = new UserTermsAcceptance();
        acceptance.setAcceptanceId(acceptanceId);
        acceptance.setUserId(user.getUserId());
        acceptance.setAcceptedContentSha256(history.getContentSha256());
        acceptance.setAcceptedDocumentUpdatedAt(now);
        acceptance.setAcceptedAt(now);
        acceptance.setSource("STANDARD_WEB");
        acceptanceRepository.saveAndFlush(acceptance);

        UUID sessionId = UUID.randomUUID();
        StandardSession session = new StandardSession();
        session.setSessionId(sessionId);
        session.setUser(user);
        session.setTokenHash(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        session.setStatus(StandardSessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plusSeconds(3600));
        session.setAbsoluteExpiresAt(now.plusSeconds(7200));
        sessionRepository.saveAndFlush(session);
        return new StandardContext(sessionId, acceptanceId);
    }

    private void guidance(UUID analysisId, ProtectionType protectionType) {
        GuidanceView view = new GuidanceView();
        view.setGuidanceViewId(UUID.randomUUID());
        view.setAnalysisId(analysisId);
        view.setProtectionType(protectionType);
        view.setGuideVersion(1L);
        view.setFirstViewedAt(Instant.parse("2026-11-03T17:00:00Z"));
        guidanceViewRepository.saveAndFlush(view);
    }

    private JsonNode v1Result() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/1.0",
                  "summary":"Resumen histórico.",
                  "patentabilityAssessment":{
                    "outcome":"INSUFFICIENT_INFORMATION",
                    "rationale":"Revisión histórica orientativa."
                  },
                  "protectionOptions":[
                    {"type":"COPYRIGHT","applicability":"LIKELY","rationale":"Obra original."},
                    {"type":"OTHER","applicability":"POSSIBLE","rationale":"Otra vía posible."}
                  ],
                  "observations":["Observación histórica."],
                  "warnings":["Advertencia histórica."]
                }
                """);
    }

    private JsonNode v2Result() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/2.0",
                  "summary":"INVENTION_PATENT (POSSIBLE) y COPYRIGHT.",
                  "protectionOptions":[
                    {
                      "type":"INVENTION_PATENT",
                      "applicability":"POSSIBLE",
                      "protectedSubjectMatter":"Solución técnica.",
                      "rationale":"Existe relación técnica.",
                      "legalBasis":[{"instrument":"DECISION_486","articles":["14"]}]
                    },
                    {
                      "type":"COPYRIGHT",
                      "applicability":"LIKELY",
                      "protectedSubjectMatter":"Expresión original.",
                      "rationale":"Existe expresión protegible.",
                      "legalBasis":[{"instrument":"DECISION_351","articles":["4"]}]
                    }
                  ],
                  "patentScreening":{
                    "applicable":true,
                    "article15":{
                      "assessment":"POTENTIAL_MATCH",
                      "rationale":"Requiere revisión.",
                      "matches":[{"literal":"b","rationale":"Coincidencia orientativa."}]
                    },
                    "article20":{
                      "assessment":"NO_POTENTIAL_MATCH",
                      "rationale":"Sin coincidencias.",
                      "matches":[]
                    }
                  },
                  "geneticResourceAccess":{
                    "assessment":"POTENTIALLY_REQUIRED",
                    "rationale":"Se identificó material biológico (POTENTIALLY_REQUIRED).",
                    "missingInformation":["Origen del recurso."]
                  },
                  "observations":["Observación presentada."],
                  "warnings":["Advertencia presentada."]
                }
                """);
    }

    private static OperationalAnalysisReportRow row(List<OperationalAnalysisReportRow> rows, UUID analysisId) {
        return rows.stream().filter(row -> row.analysisId().equals(analysisId)).findFirst().orElseThrow();
    }

    private static void assertEmptyResult(OperationalAnalysisReportRow row) {
        assertThat(row.conclusion()).isNull();
        assertThat(row.reportText()).isNull();
        assertThat(row.reportPdfAvailable()).isFalse();
        assertThat(row.identifiedModalities()).isEmpty();
        assertThat(row.applicabilityByModality()).isEmpty();
    }

    private static UUID uuid(long value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }

    private record StandardContext(UUID sessionId, UUID acceptanceId) {
    }
}
