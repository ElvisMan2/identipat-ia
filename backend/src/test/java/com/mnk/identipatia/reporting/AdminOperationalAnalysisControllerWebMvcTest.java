package com.mnk.identipatia.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.advice.GlobalExceptionHandler;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.config.JwtAuthenticationFilter;
import com.mnk.identipatia.config.SecurityConfig;
import com.mnk.identipatia.dto.PagedResponse;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.reporting.controller.AdminOperationalAnalysisController;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListItem;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.service.OperationalAnalysisDatasetException;
import com.mnk.identipatia.reporting.service.OperationalAnalysisQueryService;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOperationalAnalysisController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminOperationalAnalysisControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OperationalAnalysisQueryService queryService;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminReceivesExactPagedContractWithoutAdditionalPiiAndNoCsrf() throws Exception {
        OperationalAnalysisListItem item = new OperationalAnalysisListItem(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-11-03T15:00:00Z"),
                AnalysisStatus.COMPLETED,
                "Jose", "Zuniga Lima", "DNI", "12345678",
                AnalysisInputType.PDF,
                "Conclusion", List.of("Derecho de autor"),
                OperationalAnalysisOrigin.STANDARD, true);
        when(queryService.findAll(any())).thenReturn(
                new PagedResponse<>(List.of(item), 0, 20, 1, 1, true, true));

        String response = mockMvc.perform(get("/admin/analyses")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "jose")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30")
                        .param("status", "completed")
                        .param("origin", "standard")
                        .param("inputType", "pdf")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode row = objectMapper.readTree(response).path("content").get(0);
        assertThat(row.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "analysisId", "createdAt", "status", "firstName", "lastNames",
                        "doiType", "doi", "inputType", "conclusion",
                        "identifiedModalities", "analysisOrigin", "guidanceConsulted");
        assertThat(row.properties()).hasSize(12);
        assertThat(response).doesNotContain(
                "reportText", "birthDate", "phone", "mobilePhone", "email",
                "profession", "sessionId", "termsAcceptanceId");
    }

    @Test
    void anonymousAndStandardCookieCannotAccess() throws Exception {
        mockMvc.perform(get("/admin/analyses"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/analyses")
                        .cookie(new Cookie("IDENTIPAT_STANDARD_SESSION", "standard-cookie")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STANDARD")
    void authenticatedStandardCannotAccess() throws Exception {
        mockMvc.perform(get("/admin/analyses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidAndExpiredBearerCannotFallBackToExistingAuthenticationOrCookie() throws Exception {
        when(jwtService.extractUsername("invalid-token"))
                .thenThrow(new MalformedJwtException("invalid"));
        when(jwtService.extractUsername("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/admin/analyses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .cookie(new Cookie("IDENTIPAT_STANDARD_SESSION", "standard-cookie")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/analyses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidQueryRangeAndDateReturnControlledBadRequests() throws Exception {
        mockMvc.perform(get("/admin/analyses").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANALYSIS_QUERY"));
        mockMvc.perform(get("/admin/analyses")
                        .param("from", "2026-11-04").param("to", "2026-11-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mockMvc.perform(get("/admin/analyses").param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void structuralFailureReturnsGenericJson500WithoutInternalDetails() throws Exception {
        when(queryService.findAll(any())).thenThrow(
                new OperationalAnalysisDatasetException("internal analysisId=secret"));

        mockMvc.perform(get("/admin/analyses"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("analysisId=secret"))));
    }
}
