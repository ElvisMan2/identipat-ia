package com.mnk.identipatia.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.dto.PagedResponse;
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
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListItem;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetException;
import com.mnk.identipatia.reporting.service.OperationalAnalysisQueryService;
import com.mnk.identipatia.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class OperationalAnalysisQueryIntegrationTest {
    private static final String LEGAL_HASH =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_operational_query_test");

    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired LegalDocumentHistoryRepository legalHistoryRepository;
    @Autowired UserTermsAcceptanceRepository acceptanceRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired StoredAnalysisResultRepository resultRepository;
    @Autowired GuidanceViewRepository guidanceViewRepository;
    @Autowired OperationalAnalysisQueryService queryService;
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @Test
    void paginatesInPostgresqlWithStableDescendingOrderCurrentProfileAndCurrentPageEnrichment() {
        User user = user("Inicial", "Paterno", "Materno", "PAGE-USER", "page@example.test");
        Instant createdAt = Instant.parse("2026-11-03T15:00:00Z");
        Analysis first = analysis(user, uuid(1), createdAt, AnalysisStatus.RECEIVED,
                AnalysisInputType.TEXT, false);
        Analysis second = analysis(user, uuid(2), createdAt, AnalysisStatus.RECEIVED,
                AnalysisInputType.TEXT, false);
        Analysis third = analysis(user, uuid(3), createdAt.minusSeconds(1), AnalysisStatus.FAILED,
                AnalysisInputType.AUDIO, false);
        Analysis fourth = analysis(user, uuid(4), createdAt.minusSeconds(2), AnalysisStatus.ANALYZING,
                AnalysisInputType.PDF, false);
        analysis(user, uuid(5), createdAt.minusSeconds(3), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, false);

        user.setFirstName("Actual");
        user.setPaternalLastName(" Apellido ");
        user.setMaternalLastName(" Vigente ");
        userRepository.saveAndFlush(user);

        PagedResponse<OperationalAnalysisListItem> page0 = query(0, 2, null, null, null,
                null, null, null, null);
        PagedResponse<OperationalAnalysisListItem> page1 = query(1, 2, null, null, null,
                null, null, null, null);

        assertThat(page0.content()).extracting(OperationalAnalysisListItem::analysisId)
                .containsExactly(second.getAnalysisId(), first.getAnalysisId());
        assertThat(page1.content()).extracting(OperationalAnalysisListItem::analysisId)
                .containsExactly(third.getAnalysisId(), fourth.getAnalysisId());
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.first()).isTrue();
        assertThat(page0.last()).isFalse();
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page0.content()).allSatisfy(item -> {
            assertThat(item.firstName()).isEqualTo("Actual");
            assertThat(item.lastNames()).isEqualTo("Apellido Vigente");
            assertThat(item.conclusion()).isNull();
            assertThat(item.identifiedModalities()).isEmpty();
        });
        assertThatThrownBy(() -> query(2, 2, null, null, null,
                null, null, null, null))
                .isInstanceOf(OperationalAnalysisDatasetException.class)
                .hasMessageContaining("Stored analysis result is unavailable");

        PagedResponse<OperationalAnalysisListItem> empty = query(
                0, 20, "no-match", null, null, null, null, null, null);
        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.first()).isTrue();
        assertThat(empty.last()).isTrue();
    }

    @Test
    void searchesCurrentUserFieldsWithAndSemanticsAndEscapesLikeMetacharacters() {
        User jose = user("Jose", "Zuniga", "Lima", "DNI-87654321", "jose@example.test");
        User literal = user("Literal%_\\Name", "Especial", "Prueba",
                "DOC-%_\\", "literal@example.test");
        User other = user("Jose", "Ramos", "Norte", "OTHER-1", "other@example.test");
        analysis(jose, UUID.randomUUID(), Instant.parse("2026-11-03T12:00:00Z"),
                AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, false);
        analysis(literal, UUID.randomUUID(), Instant.parse("2026-11-03T12:01:00Z"),
                AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, false);
        analysis(other, UUID.randomUUID(), Instant.parse("2026-11-03T12:02:00Z"),
                AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, false);

        assertThat(query("JOSE zuniga").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DNI-87654321");
        assertThat(query("lima").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DNI-87654321");
        assertThat(query("87654321").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DNI-87654321");
        assertThat(query("JOSE@EXAMPLE.TEST").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DNI-87654321");
        assertThat(query("%").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DOC-%_\\");
        assertThat(query("_").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DOC-%_\\");
        assertThat(query("\\").content()).extracting(OperationalAnalysisListItem::doi)
                .containsExactly("DOC-%_\\");
    }

    @Test
    void combinesDateStatusOriginAndInputTypeFiltersWithMatchingCount() throws Exception {
        User target = user("Target", "Filtro", "Uno", "FILTER-1", "target@example.test");
        User excluded = user("Excluded", "Filtro", "Dos", "FILTER-2", "excluded@example.test");
        Analysis included = analysis(target, UUID.randomUUID(), Instant.parse("2026-11-03T06:00:00Z"),
                AnalysisStatus.COMPLETED, AnalysisInputType.PDF, true);
        storedResult(included, v2Result());
        Analysis wrongOrigin = analysis(excluded, UUID.randomUUID(), Instant.parse("2026-11-03T07:00:00Z"),
                AnalysisStatus.COMPLETED, AnalysisInputType.PDF, false);
        storedResult(wrongOrigin, v2Result());
        analysis(excluded, UUID.randomUUID(), Instant.parse("2026-11-03T08:00:00Z"),
                AnalysisStatus.RECEIVED, AnalysisInputType.PDF, true);
        analysis(excluded, UUID.randomUUID(), Instant.parse("2026-11-04T05:00:00Z"),
                AnalysisStatus.RECEIVED, AnalysisInputType.TEXT, false);

        PagedResponse<OperationalAnalysisListItem> response = query(
                0, 20, null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3),
                "completed", "standard", "pdf", "createdAt,asc");

        assertThat(response.totalElements()).isOne();
        assertThat(response.content()).extracting(OperationalAnalysisListItem::analysisId)
                .containsExactly(included.getAnalysisId());
        assertThat(response.content().get(0).analysisOrigin())
                .isEqualTo(OperationalAnalysisOrigin.STANDARD);
        assertThat(query(0, 20, null, LocalDate.of(2026, 11, 3), null,
                null, null, null, null).totalElements()).isEqualTo(4);
        assertThat(query(0, 20, null, null, LocalDate.of(2026, 11, 3),
                null, null, null, null).totalElements()).isEqualTo(3);
        assertThat(query(0, 20, null, null, null,
                "completed", null, null, null).totalElements()).isEqualTo(2);
        assertThat(query(0, 20, null, null, null,
                null, "standard", null, null).totalElements()).isEqualTo(2);
        assertThat(query(0, 20, null, null, null,
                null, null, "text", null).totalElements()).isEqualTo(1);
    }

    @Test
    void enrichesV1V2OtherNonterminalFailedAndGuidanceInBatchAndRejectsBrokenResults() throws Exception {
        User user = user("Resultados", "Paterno", "Materno", "RESULT-1", "result@example.test");
        Instant base = Instant.parse("2026-11-03T10:00:00Z");
        Analysis v1 = analysis(user, uuid(11), base, AnalysisStatus.COMPLETED,
                AnalysisInputType.TEXT, false);
        Analysis v2 = analysis(user, uuid(12), base.plusSeconds(1), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, false);
        Analysis received = analysis(user, uuid(13), base.plusSeconds(2), AnalysisStatus.RECEIVED,
                AnalysisInputType.TEXT, false);
        Analysis failed = analysis(user, uuid(14), base.plusSeconds(3), AnalysisStatus.FAILED,
                AnalysisInputType.AUDIO, false);
        storedResult(v1, v1Result());
        storedResult(v2, v2Result());
        guidance(v2.getAnalysisId(), "INVENTION_PATENT");
        guidance(v2.getAnalysisId(), "COPYRIGHT");

        PagedResponse<OperationalAnalysisListItem> response = query(
                0, 20, null, null, null, null, null, null, "createdAt,asc");

        assertThat(response.content()).hasSize(4);
        OperationalAnalysisListItem v1Item = item(response, v1.getAnalysisId());
        OperationalAnalysisListItem v2Item = item(response, v2.getAnalysisId());
        assertThat(v1Item.conclusion()).isEqualTo("Resumen historico.");
        assertThat(v1Item.identifiedModalities())
                .containsExactly("Derecho de autor", "Otra modalidad");
        assertThat(v2Item.conclusion())
                .isEqualTo("patente de invención — podría corresponder y derecho de autor.");
        assertThat(v2Item.identifiedModalities())
                .containsExactly("Patente de invención", "Derecho de autor");
        assertThat(v2Item.guidanceConsulted()).isTrue();
        assertThat(item(response, received.getAnalysisId()).identifiedModalities()).isEmpty();
        assertThat(item(response, failed.getAnalysisId()).conclusion()).isNull();

        StoredAnalysisResult stored = resultRepository.findById(v1.getAnalysisId()).orElseThrow();
        stored.setSchemaVersion("analysis-result/9.0");
        stored.setResultJson(objectMapper.readTree("{\"schemaVersion\":\"analysis-result/9.0\"}"));
        resultRepository.saveAndFlush(stored);
        assertThatThrownBy(() -> query(
                0, 20, null, null, null, null, null, null, "createdAt,asc"))
                .isInstanceOf(OperationalAnalysisDatasetException.class)
                .hasMessageContaining("invalid or unsupported");
    }

    @Test
    void realAdminHttpPaginatesAcrossPagesWithoutDuplicatesAndAppliesCombinedFilter() throws Exception {
        User admin = user("Admin", "Operador", null, "ADMIN-LIST", "admin-list@example.test");
        admin.setUserType("ADMIN");
        admin.setPassword("unused-for-jwt");
        userRepository.saveAndFlush(admin);
        User subject = user("Sujeto", "Tabla", "Actual", "SUBJECT-1", "subject@example.test");
        Instant base = Instant.parse("2026-11-03T15:00:00Z");
        Analysis a1 = analysis(subject, uuid(21), base, AnalysisStatus.RECEIVED,
                AnalysisInputType.TEXT, false);
        Analysis a2 = analysis(subject, uuid(22), base.plusSeconds(1), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, true);
        storedResult(a2, v2Result());
        Analysis a3 = analysis(subject, uuid(23), base.plusSeconds(2), AnalysisStatus.FAILED,
                AnalysisInputType.AUDIO, false);
        Analysis a4 = analysis(subject, uuid(24), base.plusSeconds(3), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, false);
        storedResult(a4, v2Result());

        String token = "Bearer " + jwtService.generateToken(admin.getDoi());
        JsonNode page0 = response(get("/admin/analyses")
                .param("page", "0").param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, token));
        JsonNode page1 = response(get("/admin/analyses")
                .param("page", "1").param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, token));

        assertThat(page0.path("totalElements").asLong()).isEqualTo(4);
        assertThat(page0.path("content")).hasSize(2);
        assertThat(page1.path("content")).hasSize(2);
        List<String> page0Ids = ids(page0);
        List<String> page1Ids = ids(page1);
        assertThat(page0Ids).containsExactly(a4.getAnalysisId().toString(), a3.getAnalysisId().toString());
        assertThat(page1Ids).containsExactly(a2.getAnalysisId().toString(), a1.getAnalysisId().toString());
        Set<String> unique = new HashSet<>(page0Ids);
        unique.addAll(page1Ids);
        assertThat(unique).hasSize(4);

        mockMvc.perform(get("/admin/analyses")
                        .param("status", "COMPLETED")
                        .param("origin", "STANDARD")
                        .param("inputType", "PDF")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].analysisId")
                        .value(a2.getAnalysisId().toString()));
    }

    @Test
    void tablePagesAndCsvContainTheSameFullyFilteredSetInTheSameGlobalOrder() throws Exception {
        User admin = user("Admin", "Exportador", null, "ADMIN-EQUIV", "admin-equiv@example.test");
        admin.setUserType("ADMIN");
        admin.setPassword("unused-for-jwt");
        userRepository.saveAndFlush(admin);
        User target = user(
                "Equivalencia", "Compartida", "Filtros", "EQUIV-1", "equiv@example.test");
        User other = user("Otro", "Usuario", null, "EQUIV-2", "other-equiv@example.test");
        Instant base = Instant.parse("2026-11-03T10:00:00Z");

        Analysis first = analysis(target, uuid(31), base, AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, true);
        storedResult(first, v2Result());
        Analysis second = analysisWithStandardContext(
                target, uuid(32), base.plusSeconds(1), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, first);
        storedResult(second, v2Result());
        Analysis third = analysisWithStandardContext(
                target, uuid(33), base.plusSeconds(2), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, first);
        storedResult(third, v2Result());
        Analysis fourth = analysisWithStandardContext(
                target, uuid(34), base.plusSeconds(2), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, first);
        storedResult(fourth, v2Result());

        Analysis wrongOrigin = analysis(target, uuid(35), base.plusSeconds(4),
                AnalysisStatus.COMPLETED, AnalysisInputType.PDF, false);
        storedResult(wrongOrigin, v2Result());
        Analysis wrongInput = analysisWithStandardContext(
                target, uuid(36), base.plusSeconds(5), AnalysisStatus.COMPLETED,
                AnalysisInputType.AUDIO, first);
        storedResult(wrongInput, v2Result());
        analysisWithStandardContext(target, uuid(37), base.plusSeconds(6),
                AnalysisStatus.RECEIVED, AnalysisInputType.PDF, first);
        Analysis outsideDate = analysisWithStandardContext(
                target, uuid(38), Instant.parse("2026-11-04T05:00:00Z"),
                AnalysisStatus.COMPLETED, AnalysisInputType.PDF, first);
        storedResult(outsideDate, v2Result());
        Analysis wrongSearch = analysis(
                other, uuid(39), base.plusSeconds(7), AnalysisStatus.COMPLETED,
                AnalysisInputType.PDF, true);
        storedResult(wrongSearch, v2Result());

        String token = "Bearer " + jwtService.generateToken(admin.getDoi());
        JsonNode page0 = response(get("/admin/analyses")
                .param("page", "0").param("size", "2")
                .param("search", "equivalencia compartida")
                .param("from", "2026-11-03").param("to", "2026-11-03")
                .param("status", "COMPLETED").param("origin", "STANDARD")
                .param("inputType", "PDF").param("sort", "createdAt,desc")
                .header(HttpHeaders.AUTHORIZATION, token));
        JsonNode page1 = response(get("/admin/analyses")
                .param("page", "1").param("size", "2")
                .param("search", "equivalencia compartida")
                .param("from", "2026-11-03").param("to", "2026-11-03")
                .param("status", "COMPLETED").param("origin", "STANDARD")
                .param("inputType", "PDF").param("sort", "createdAt,desc")
                .header(HttpHeaders.AUTHORIZATION, token));

        byte[] csvBytes = mockMvc.perform(get("/admin/analyses/export.csv")
                        .param("search", "equivalencia compartida")
                        .param("from", "2026-11-03").param("to", "2026-11-03")
                        .param("status", "COMPLETED").param("origin", "STANDARD")
                        .param("inputType", "PDF").param("sort", "createdAt,desc")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"identipat-analisis_2026-11-03_2026-11-03_filtrado.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> tableIds = java.util.stream.Stream.concat(
                ids(page0).stream(), ids(page1).stream()).toList();
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        List<String> csvIds = csvAnalysisIds(csv);

        assertThat(page0.path("totalElements").asLong()).isEqualTo(4);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(tableIds).containsExactly(
                fourth.getAnalysisId().toString(), third.getAnalysisId().toString(),
                second.getAnalysisId().toString(), first.getAnalysisId().toString());
        assertThat(csvIds).containsExactlyElementsOf(tableIds);
        assertThat(csv).contains("\"Equivalencia\"", "patente de invención")
                .doesNotContain(wrongOrigin.getAnalysisId().toString(), wrongInput.getAnalysisId().toString(),
                        outsideDate.getAnalysisId().toString(), wrongSearch.getAnalysisId().toString());
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.first").isBoolean())
                .andExpect(jsonPath("$.last").isBoolean())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<String> ids(JsonNode response) {
        return java.util.stream.StreamSupport.stream(
                        response.path("content").spliterator(), false)
                .map(node -> node.path("analysisId").asText())
                .toList();
    }

    private static List<String> csvAnalysisIds(String csv) {
        return csv.lines()
                .skip(1)
                .map(line -> line.substring(1, line.indexOf('"', 1)))
                .toList();
    }

    private PagedResponse<OperationalAnalysisListItem> query(String search) {
        return query(0, 20, search, null, null, null, null, null, "createdAt,desc");
    }

    private PagedResponse<OperationalAnalysisListItem> query(
            int page, int size, String search, LocalDate from, LocalDate to,
            String status, String origin, String inputType, String sort) {
        return queryService.findAll(OperationalAnalysisListCriteria.of(
                page, size, search, from, to, status, origin, inputType, sort));
    }

    private User user(
            String firstName, String paternalLastName, String maternalLastName,
            String doi, String email) {
        User user = new User();
        user.setFirstName(firstName);
        user.setPaternalLastName(paternalLastName);
        user.setMaternalLastName(maternalLastName);
        user.setDoi(doi);
        user.setDoiType("DNI");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender("OTHER");
        user.setEmail(email);
        user.setPhone("010000000");
        user.setMobilePhone("900000000");
        user.setUserType("STANDARD");
        user.setProfession("Profesional");
        user.setStatus("A");
        user.setCreationDate(LocalDateTime.of(2026, 1, 1, 10, 0));
        return userRepository.saveAndFlush(user);
    }

    private Analysis analysis(
            User user, UUID id, Instant createdAt, AnalysisStatus status,
            AnalysisInputType inputType, boolean standard) {
        Analysis analysis = new Analysis();
        analysis.setAnalysisId(id);
        analysis.setUserId(user.getUserId());
        if (standard) {
            StandardContext context = standardContext(user, createdAt);
            analysis.setSessionId(context.sessionId());
            analysis.setTermsAcceptanceId(context.acceptanceId());
        }
        analysis.setInputType(inputType);
        analysis.setStatus(status);
        analysis.setCreatedAt(createdAt);
        analysis.setUpdatedAt(createdAt);
        if (status == AnalysisStatus.ANALYZING || status == AnalysisStatus.COMPLETED) {
            analysis.setStartedAt(createdAt);
        }
        if (status == AnalysisStatus.COMPLETED) {
            analysis.setCompletedAt(createdAt.plusSeconds(1));
        }
        if (status == AnalysisStatus.FAILED) {
            analysis.setFailedAt(createdAt.plusSeconds(1));
            analysis.setFailureCode("WORKER_PROCESSING_ERROR");
        }
        return analysisRepository.saveAndFlush(analysis);
    }

    private Analysis analysisWithStandardContext(
            User user, UUID id, Instant createdAt, AnalysisStatus status,
            AnalysisInputType inputType, Analysis contextSource) {
        Analysis analysis = analysis(user, id, createdAt, status, inputType, false);
        analysis.setSessionId(contextSource.getSessionId());
        analysis.setTermsAcceptanceId(contextSource.getTermsAcceptanceId());
        return analysisRepository.saveAndFlush(analysis);
    }

    private void storedResult(Analysis analysis, JsonNode result) {
        StoredAnalysisResult stored = new StoredAnalysisResult();
        stored.setAnalysisId(analysis.getAnalysisId());
        stored.setSchemaVersion(result.path("schemaVersion").asText());
        stored.setResultJson(result);
        stored.setCreatedAt(analysis.getCreatedAt().plusSeconds(1));
        resultRepository.saveAndFlush(stored);
    }

    private StandardContext standardContext(User user, Instant now) {
        LegalDocumentHistory history = legalHistoryRepository.findById(LEGAL_HASH).orElseGet(() -> {
            LegalDocumentHistory created = new LegalDocumentHistory();
            created.setContentSha256(LEGAL_HASH);
            created.setTitle("Terminos de prueba");
            created.setContentHtml("<p>Contenido</p>");
            created.setCapturedAt(now);
            return legalHistoryRepository.saveAndFlush(created);
        });
        UserTermsAcceptance acceptance = new UserTermsAcceptance();
        acceptance.setAcceptanceId(UUID.randomUUID());
        acceptance.setUserId(user.getUserId());
        acceptance.setAcceptedContentSha256(history.getContentSha256());
        acceptance.setAcceptedDocumentUpdatedAt(history.getCapturedAt());
        acceptance.setAcceptedAt(now);
        acceptance.setSource("STANDARD_WEB");
        acceptanceRepository.saveAndFlush(acceptance);

        StandardSession session = new StandardSession();
        session.setSessionId(UUID.randomUUID());
        session.setUser(user);
        session.setTokenHash(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        session.setStatus(StandardSessionStatus.ACTIVE);
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plusSeconds(3600));
        session.setAbsoluteExpiresAt(now.plusSeconds(7200));
        sessionRepository.saveAndFlush(session);
        return new StandardContext(session.getSessionId(), acceptance.getAcceptanceId());
    }

    private void guidance(UUID analysisId, String protectionType) {
        GuidanceView view = new GuidanceView();
        view.setGuidanceViewId(UUID.randomUUID());
        view.setAnalysisId(analysisId);
        view.setProtectionType(com.mnk.identipatia.analysis.result.v2.ProtectionType.valueOf(protectionType));
        view.setGuideVersion(1L);
        view.setFirstViewedAt(Instant.parse("2026-11-03T17:00:00Z"));
        guidanceViewRepository.saveAndFlush(view);
    }

    private JsonNode v1Result() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/1.0",
                  "summary":"Resumen historico.",
                  "patentabilityAssessment":{"outcome":"INSUFFICIENT_INFORMATION","rationale":"Revision."},
                  "protectionOptions":[
                    {"type":"COPYRIGHT","applicability":"LIKELY","rationale":"Obra."},
                    {"type":"OTHER","applicability":"POSSIBLE","rationale":"Otra via."}
                  ],
                  "observations":[],"warnings":[]
                }
                """);
    }

    private JsonNode v2Result() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/2.0",
                  "summary":"INVENTION_PATENT (POSSIBLE) y COPYRIGHT.",
                  "protectionOptions":[
                    {"type":"INVENTION_PATENT","applicability":"POSSIBLE",
                     "protectedSubjectMatter":"Solucion.","rationale":"Relacion.",
                     "legalBasis":[{"instrument":"DECISION_486","articles":["14"]}]},
                    {"type":"COPYRIGHT","applicability":"LIKELY",
                     "protectedSubjectMatter":"Expresion.","rationale":"Relacion.",
                     "legalBasis":[{"instrument":"DECISION_351","articles":["4"]}]}
                  ],
                  "patentScreening":{
                    "applicable":true,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}
                  },
                  "geneticResourceAccess":{
                    "assessment":"NOT_INDICATED","rationale":"No aplica.","missingInformation":[]
                  },
                  "observations":[],"warnings":[]
                }
                """);
    }

    private static OperationalAnalysisListItem item(
            PagedResponse<OperationalAnalysisListItem> response, UUID analysisId) {
        return response.content().stream()
                .filter(item -> item.analysisId().equals(analysisId))
                .findFirst().orElseThrow();
    }

    private static UUID uuid(long value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }

    private record StandardContext(UUID sessionId, UUID acceptanceId) {
    }
}
