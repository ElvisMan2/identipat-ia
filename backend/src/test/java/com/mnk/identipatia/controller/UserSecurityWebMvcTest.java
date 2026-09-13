package com.mnk.identipatia.controller;

import com.mnk.identipatia.advice.GlobalExceptionHandler;
import com.mnk.identipatia.config.JwtAuthenticationFilter;
import com.mnk.identipatia.config.SecurityConfig;
import com.mnk.identipatia.dto.DocumentRecognitionResponse;
import com.mnk.identipatia.dto.LoginResponseDTO;
import com.mnk.identipatia.dto.StandardUserRegistrationResponse;
import com.mnk.identipatia.dto.UserDTO;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
import com.mnk.identipatia.service.UserService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
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
        mockMvc.perform(get("/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/doi/12345678")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content(STANDARD_REGISTRATION))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/users/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void authenticatedAdminCanExecuteAdministrativeOperations() throws Exception {
        UserDTO user = userDto();
        when(userService.findAll()).thenReturn(List.of(user));
        when(userService.findById(1L)).thenReturn(user);
        when(userService.findByDoi("12345678")).thenReturn(user);
        when(userService.update(eq(1L), any())).thenReturn(user);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doi").value("12345678"));
        mockMvc.perform(get("/users/1")).andExpect(status().isOk());
        mockMvc.perform(get("/users/doi/12345678")).andExpect(status().isOk());
        mockMvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content(adminUserJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/1")).andExpect(status().isNoContent());
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
