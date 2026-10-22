package com.mnk.identipatia.controller;

import com.mnk.identipatia.advice.GlobalExceptionHandler;
import com.mnk.identipatia.config.JwtAuthenticationFilter;
import com.mnk.identipatia.config.SecurityConfig;
import com.mnk.identipatia.dto.DocumentRecognitionResponse;
import com.mnk.identipatia.dto.LoginResponseDTO;
import com.mnk.identipatia.dto.PagedResponse;
import com.mnk.identipatia.dto.StandardUserRegistrationResponse;
import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
import com.mnk.identipatia.service.UserService;
import com.mnk.identipatia.service.UserSearchCriteria;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class UserSecurityWebMvcTest {

    private static final String STANDARD_REGISTRATION = """
            {
              "firstName": "Ana",
              "paternalLastName": "Torres",
              "maternalLastName": "Salas",
              "doi": "12345678",
              "doiType": "DNI",
              "birthDate": "01/01/1990",
              "gender": "FEMALE",
              "email": "ana.torres@example.com",
              "phone": "016543210",
              "mobilePhone": "912345678",
              "profession": "Abogada",
              "userType": "ADMIN",
              "status": "I",
              "password": "untrusted-password"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private Clock clock;

    @BeforeEach
    void configureClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void anonymousUserCanRegisterOnlyThroughTheStandardRegistrationContract() throws Exception {
        when(userService.registerStandard(any())).thenReturn(new StandardUserRegistrationResponse(true));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STANDARD_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.userType").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());

        verify(userService).registerStandard(any());
    }

    @Test
        void publicRecognitionReturnsOnlyAccessStateForExistingDocument() throws Exception {
                when(userService.identifyDocument(any())).thenReturn(new DocumentRecognitionResponse(true, true));

        mockMvc.perform(post("/users/identify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"12345678\",\"doiType\":\"DNI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.passwordRequired").value(true))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.userType").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void publicRecognitionReturnsFalseForUnknownDocument() throws Exception {
                when(userService.identifyDocument(any())).thenReturn(new DocumentRecognitionResponse(false, false));

        mockMvc.perform(post("/users/identify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"99999999\",\"doiType\":\"CE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.passwordRequired").value(false));
    }

    @Test
    void adminWithValidCredentialsReceivesJwt() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(jwtService.generateToken("admin-001")).thenReturn("signed-jwt");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"admin-001\",\"password\":\"safe-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("signed-jwt"));
    }

    @Test
    void standardUserCannotLogInOrReceiveJwt() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("Standard users cannot log in"));

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doi\":\"12345678\",\"password\":\"not-a-credential\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void anonymousRequestsCannotAccessAdministrativeOperations() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/doi/12345678")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content(STANDARD_REGISTRATION))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/users/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void authenticatedAdminCanExportCsvWithDownloadHeaders() throws Exception {
        byte[] csv = "\uFEFF\"ID\",\"Nombre completo\"".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(userService.exportCsv(any(UserSearchCriteria.class))).thenReturn(csv);

        mockMvc.perform(get("/users/export")
                        .param("search", " Jose ")
                        .param("userType", " standard ")
                        .param("status", " a ")
                        .param("doiType", " dni ")
                        .param("sort", "firstName,asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"usuarios-2026-10-22.csv\""))
                .andExpect(content().bytes(csv));

        ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
        verify(userService).exportCsv(captor.capture());
        UserSearchCriteria criteria = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(List.of("jose"), criteria.searchTerms());
        org.junit.jupiter.api.Assertions.assertEquals("STANDARD", criteria.userType());
        org.junit.jupiter.api.Assertions.assertEquals("A", criteria.status());
        org.junit.jupiter.api.Assertions.assertEquals("DNI", criteria.doiType());
        org.junit.jupiter.api.Assertions.assertEquals("firstName", criteria.sortField());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.data.domain.Sort.Direction.ASC,
                criteria.sortDirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void authenticatedAdminCanExecuteAdministrativeOperations() throws Exception {
        UserDTO user = userDto();
        when(userService.findAll(any(UserSearchCriteria.class)))
                .thenReturn(new PagedResponse<>(List.of(user), 0, 20, 1, 1, true, true));
        when(userService.findById(1L)).thenReturn(user);
        when(userService.findByDoi("12345678")).thenReturn(user);
        when(userService.update(eq(1L), any())).thenReturn(user);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].doi").value("12345678"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
        mockMvc.perform(get("/users/1")).andExpect(status().isOk());
        mockMvc.perform(get("/users/doi/12345678")).andExpect(status().isOk());
        mockMvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content(adminUserJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/1")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listingUsesDefaultsAndNormalizesFilters() throws Exception {
        when(userService.findAll(any(UserSearchCriteria.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/users")
                        .param("search", "  Ana   TORRES ")
                        .param("userType", " standard ")
                        .param("status", " a ")
                        .param("doiType", " dni "))
                .andExpect(status().isOk());

        ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
        verify(userService).findAll(captor.capture());
        UserSearchCriteria criteria = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(0, criteria.page());
        org.junit.jupiter.api.Assertions.assertEquals(20, criteria.size());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("ana", "torres"), criteria.searchTerms());
        org.junit.jupiter.api.Assertions.assertEquals("STANDARD", criteria.userType());
        org.junit.jupiter.api.Assertions.assertEquals("A", criteria.status());
        org.junit.jupiter.api.Assertions.assertEquals("DNI", criteria.doiType());
        org.junit.jupiter.api.Assertions.assertEquals("creationDate", criteria.sortField());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.data.domain.Sort.Direction.DESC,
                criteria.sortDirection());
    }

    @Test
    @WithMockUser(roles = "STANDARD")
    void authenticatedNonAdminCannotListUsers() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/users/export")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidExportFiltersAndSortReturnBadRequest() throws Exception {
        List<String> invalidQueries = List.of(
                "?userType=SUPERADMIN",
                "?status=X",
                "?doiType=PASSPORT",
                "?sort=password,asc",
                "?sort=firstName,sideways");

        for (String query : invalidQueries) {
            mockMvc.perform(get("/users/export" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidListingParametersReturnBadRequest() throws Exception {
        List<String> invalidQueries = List.of(
                "?page=-1",
                "?size=0",
                "?size=101",
                "?userType=SUPERADMIN",
                "?status=X",
                "?doiType=PASSPORT",
                "?sort=password,asc",
                "?sort=firstName,sideways",
                "?sort=firstName",
                "?sort=firstName,asc,extra",
                "?page=not-a-number");

        for (String query : invalidQueries) {
            mockMvc.perform(get("/users" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void malformedJwtDoesNotGrantAdministrativeAccess() throws Exception {
        when(jwtService.extractUsername("manipulated-token")).thenThrow(new MalformedJwtException("invalid"));

        mockMvc.perform(get("/users").header("Authorization", "Bearer manipulated-token"))
                .andExpect(status().isUnauthorized());
    }

    private static UserDTO userDto() {
        return UserDTO.builder()
                .userId(1L)
                .password("sensitive-password")
                .firstName("Ana")
                .paternalLastName("Torres")
                .maternalLastName("Salas")
                .doi("12345678")
                .doiType("DNI")
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender("FEMALE")
                .email("ana.torres@example.com")
                .phone("016543210")
                .mobilePhone("912345678")
                .userType("STANDARD")
                .profession("Abogada")
                .status("A")
                .build();
    }

    private static String adminUserJson() {
        return """
                {
                  "firstName": "Ana",
                  "paternalLastName": "Torres",
                  "maternalLastName": "Salas",
                  "doi": "12345678",
                  "doiType": "DNI",
                  "birthDate": "01/01/1990",
                  "gender": "FEMALE",
                  "email": "ana.torres@example.com",
                  "phone": "016543210",
                  "mobilePhone": "912345678",
                  "userType": "STANDARD",
                  "profession": "Abogada",
                  "status": "A"
                }
                """;
    }
}
