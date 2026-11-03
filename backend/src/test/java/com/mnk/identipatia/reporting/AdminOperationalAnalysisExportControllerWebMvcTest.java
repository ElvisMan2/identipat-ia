package com.mnk.identipatia.reporting;

import com.mnk.identipatia.advice.GlobalExceptionHandler;
import com.mnk.identipatia.config.JwtAuthenticationFilter;
import com.mnk.identipatia.config.SecurityConfig;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.reporting.controller.AdminOperationalAnalysisExportController;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportCriteria;
import com.mnk.identipatia.reporting.service.OperationalAnalysisCsvExporter;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetException;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetService;
import com.mnk.identipatia.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOperationalAnalysisExportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminOperationalAnalysisExportControllerWebMvcTest {
    private static final byte[] CSV = "\uFEFF\"Id Analisis\"".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Autowired MockMvc mockMvc;
    @MockBean OperationalAnalysisDatasetService datasetService;
    @MockBean OperationalAnalysisCsvExporter csvExporter;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminExportsDatasetWithFiltersHeadersAndNoCsrf() throws Exception {
        when(datasetService.build(any())).thenReturn(List.of());
        when(csvExporter.export(List.of())).thenReturn(CSV);

        mockMvc.perform(get("/admin/analyses/export.csv")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"identipat-analisis_2026-10-01_2026-10-31.csv\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(content().bytes(CSV));

        verify(datasetService).build(new OperationalAnalysisReportCriteria(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        verify(csvExporter).export(List.of());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void filenameCoversAllOptionalFilterCombinations() throws Exception {
        when(datasetService.build(any())).thenReturn(List.of());
        when(csvExporter.export(any())).thenReturn(CSV);

        assertFilename("", "identipat-analisis_completo.csv");
        assertFilename("?from=2026-10-01", "identipat-analisis_desde_2026-10-01.csv");
        assertFilename("?to=2026-10-31", "identipat-analisis_hasta_2026-10-31.csv");
        assertFilename("?from=2026-10-01&to=2026-10-31",
                "identipat-analisis_2026-10-01_2026-10-31.csv");
    }

    @Test
    void anonymousAndStandardCookieCannotAccessExport() throws Exception {
        mockMvc.perform(get("/admin/analyses/export.csv"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/analyses/export.csv")
                        .cookie(new Cookie("IDENTIPAT_STANDARD_SESSION", "standard-cookie")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STANDARD")
    void authenticatedNonAdminCannotAccessExport() throws Exception {
        mockMvc.perform(get("/admin/analyses/export.csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidOrExpiredBearerCannotFallBackToAuthenticationOrStandardCookie() throws Exception {
        when(jwtService.extractUsername("invalid-token"))
                .thenThrow(new MalformedJwtException("invalid"));
        when(jwtService.extractUsername("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/admin/analyses/export.csv")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .cookie(new Cookie("IDENTIPAT_STANDARD_SESSION", "standard-cookie")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/analyses/export.csv")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidRangeHasFunctionalCodeAndInvalidDateFormatIsBadRequest() throws Exception {
        mockMvc.perform(get("/admin/analyses/export.csv")
                        .param("from", "2026-11-01")
                        .param("to", "2026-10-31"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        mockMvc.perform(get("/admin/analyses/export.csv").param("from", "no-es-fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void structuralDatasetFailureReturnsJson500WithoutPartialCsvOrInternalMessage() throws Exception {
        when(datasetService.build(any())).thenThrow(
                new OperationalAnalysisDatasetException("internal analysisId=secret"));

        mockMvc.perform(get("/admin/analyses/export.csv"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("analysisId=secret"))));
    }

    private void assertFilename(String query, String filename) throws Exception {
        mockMvc.perform(get("/admin/analyses/export.csv" + query))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""));
    }
}
