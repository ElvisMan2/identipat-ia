package com.mnk.identipatia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.model.ConsentDecision;
import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.StandardSessionCloseReason;
import com.mnk.identipatia.model.StandardSessionStatus;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.ConsentEventRepository;
import com.mnk.identipatia.repository.StandardSessionRepository;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.StandardSessionTokenService;
import com.mnk.identipatia.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class StandardSessionIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("identipat_standard_session_test");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired StandardSessionRepository sessionRepository;
    @Autowired ConsentEventRepository consentRepository;
    @Autowired StandardSessionTokenService tokenService;
    @Autowired UserService userService;

    @Test
    void realCookieHeaderCsrfProtectsCreateConsentAndClose() throws Exception {
        User user = saveUser("STANDARD", "A");
        Csrf csrf = csrf();

        mockMvc.perform(post("/standard-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/standard-session/consent")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(consentBody("ACCEPTED", "personal-data/1.0")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STANDARD_SESSION_REQUIRED"));

        MvcResult created = createSession(user, csrf, null);
        Cookie standard = created.getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        assertThat(standard).isNotNull();
        assertThat(standard.isHttpOnly()).isTrue();
        assertThat(standard.getSecure()).isFalse();
        assertThat(standard.getPath()).isEqualTo("/identipat-ia");
        assertThat(created.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");
        assertThat(created.getResponse().getContentAsString()).doesNotContain(standard.getValue());

        mockMvc.perform(post("/standard-session/consent").cookie(standard)
                        .contentType(MediaType.APPLICATION_JSON).content(consentBody("ACCEPTED", "personal-data/1.0")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/standard-session/consent")
                        .cookie(standard, csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(consentBody("ACCEPTED", "personal-data/1.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.consentDocumentHash").doesNotExist());

        mockMvc.perform(delete("/standard-session").cookie(standard)).andExpect(status().isForbidden());
        MvcResult closed = mockMvc.perform(delete("/standard-session")
                        .cookie(standard, csrf.cookie()).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(closed.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value)
                        .contains("IDENTIPAT_STANDARD_SESSION=", "Max-Age=0", "Path=/identipat-ia"));
        mockMvc.perform(get("/standard-session").cookie(standard))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIsHmacProtectedAndTtlsAndRenewalRespectAbsoluteLimit() throws Exception {
        User user = saveUser("STANDARD", "A");
        Instant before = Instant.now();
        Cookie cookie = createSession(user, csrf(), null).getResponse()
                .getCookie("IDENTIPAT_STANDARD_SESSION");
        StandardSession stored = sessionRepository.findByTokenHash(tokenService.hash(cookie.getValue())).orElseThrow();

        assertThat(stored.getTokenHash()).containsExactly(tokenService.hash(cookie.getValue()));
        assertThat(Base64.getUrlDecoder().decode(cookie.getValue())).hasSize(32);
        assertThat(stored.getTokenHash()).isNotEqualTo(cookie.getValue().getBytes(StandardCharsets.US_ASCII));
        assertThat(stored.getCreatedAt()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(Duration.between(stored.getCreatedAt(), stored.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(Duration.between(stored.getCreatedAt(), stored.getAbsoluteExpiresAt())).isEqualTo(Duration.ofHours(8));

        Instant oldActivity = stored.getLastActivityAt();
        Thread.sleep(5);
        mockMvc.perform(get("/standard-session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.requiredConsentVersion").value("personal-data/1.0"))
                .andExpect(jsonPath("$.doi").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
        StandardSession renewed = sessionRepository.findById(stored.getSessionId()).orElseThrow();
        assertThat(renewed.getLastActivityAt()).isAfter(oldActivity);
        assertThat(renewed.getExpiresAt()).isBeforeOrEqualTo(renewed.getAbsoluteExpiresAt());
    }

    @Test
    void createRejectsMissingAdminInactiveAndInvalidDocument() throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(csrfPost("/standard-sessions", csrf)
                        .content("{\"doi\":\"missing\",\"doiType\":\"DNI\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STANDARD_REGISTRATION_REQUIRED"));

        for (User user : new User[]{saveUser("ADMIN", "A"), saveUser("STANDARD", "I")}) {
            mockMvc.perform(csrfPost("/standard-sessions", csrf).content(sessionBody(user)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STANDARD_SESSION_NOT_AVAILABLE"));
        }
        mockMvc.perform(csrfPost("/standard-sessions", csrf)
                        .content("{\"doi\":\"anything\",\"doiType\":\"PASSPORT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void rotationClosesOnlyPresentedBrowserSessionAndInvalidCookieDoesNotBlock() throws Exception {
        User user = saveUser("STANDARD", "A");
        Csrf csrf = csrf();
        Cookie first = createSession(user, csrf, null).getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        Cookie other = createSession(user, csrf, null).getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        MvcResult replacement = createSession(user, csrf, first);

        StandardSession rotated = sessionRepository.findByTokenHash(tokenService.hash(first.getValue())).orElseThrow();
        StandardSession untouched = sessionRepository.findByTokenHash(tokenService.hash(other.getValue())).orElseThrow();
        assertThat(rotated.getStatus()).isEqualTo(StandardSessionStatus.CLOSED);
        assertThat(rotated.getCloseReason()).isEqualTo(StandardSessionCloseReason.ROTATED);
        assertThat(untouched.getStatus()).isEqualTo(StandardSessionStatus.ACTIVE);
        assertThat(replacement.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value).contains("Max-Age=0", "Path=/identipat-ia"));
        createSession(user, csrf, new Cookie("IDENTIPAT_STANDARD_SESSION", "invalid-token"));
    }

    @Test
    void consentIsVersionedIdempotentConflictingAndRejectionCloses() throws Exception {
        User user = saveUser("STANDARD", "A");
        Csrf csrf = csrf();
        Cookie standard = createSession(user, csrf, null).getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");

        mockMvc.perform(consentPost(standard, csrf, "UNKNOWN", "personal-data/1.0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CONSENT_DECISION"));
        mockMvc.perform(consentPost(standard, csrf, "ACCEPTED", "old/0.9"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSENT_VERSION_OUTDATED"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(consentPost(standard, csrf, "ACCEPTED", "personal-data/1.0"))
                    .andExpect(status().isOk());
        }
        StandardSession accepted = sessionRepository.findByTokenHash(tokenService.hash(standard.getValue())).orElseThrow();
        assertThat(consentRepository.findBySessionAndConsentVersion(accepted, "personal-data/1.0"))
                .get().satisfies(event -> {
                    assertThat(event.getDecision()).isEqualTo(ConsentDecision.ACCEPTED);
                    assertThat(event.getConsentDocumentHash()).matches("[0-9a-f]{64}");
                    assertThat(event.getSource()).isEqualTo("STANDARD_WEB");
                });
        mockMvc.perform(get("/standard-session").cookie(standard))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(false));
        mockMvc.perform(consentPost(standard, csrf, "REJECTED", "personal-data/1.0"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSENT_ALREADY_DECIDED"));

        Cookie rejectedCookie = createSession(user, csrf, null).getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        mockMvc.perform(consentPost(rejectedCookie, csrf, "REJECTED", "personal-data/1.0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionStatus").value("CLOSED"))
                .andExpect(cookie().maxAge("IDENTIPAT_STANDARD_SESSION", 0));
        mockMvc.perform(consentPost(rejectedCookie, csrf, "REJECTED", "personal-data/1.0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(cookie().maxAge("IDENTIPAT_STANDARD_SESSION", 0));
        StandardSession rejected = sessionRepository.findByTokenHash(tokenService.hash(rejectedCookie.getValue())).orElseThrow();
        assertThat(rejected.getCloseReason()).isEqualTo(StandardSessionCloseReason.CONSENT_REJECTED);
    }

    @Test
    void missingInvalidExpiredAndInactiveSessionsReturn401AndAreInvalidated() throws Exception {
        mockMvc.perform(get("/standard-session"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("STANDARD_SESSION_REQUIRED"));
        mockMvc.perform(get("/standard-session").cookie(new Cookie("IDENTIPAT_STANDARD_SESSION", "invalid")))
                .andExpect(status().isUnauthorized()).andExpect(cookie().maxAge("IDENTIPAT_STANDARD_SESSION", 0));

        User expiredUser = saveUser("STANDARD", "A");
        Cookie expiredCookie = createSession(expiredUser, csrf(), null).getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        StandardSession expired = sessionRepository.findByTokenHash(tokenService.hash(expiredCookie.getValue())).orElseThrow();
        Instant now = Instant.now();
        expired.setCreatedAt(now.minus(Duration.ofHours(1)));
        expired.setLastActivityAt(now.minus(Duration.ofMinutes(31)));
        expired.setExpiresAt(now.minusSeconds(1));
        sessionRepository.saveAndFlush(expired);
        mockMvc.perform(get("/standard-session").cookie(expiredCookie)).andExpect(status().isUnauthorized());
        assertThat(sessionRepository.findById(expired.getSessionId()).orElseThrow().getStatus())
                .isEqualTo(StandardSessionStatus.EXPIRED);

        User inactiveUser = saveUser("STANDARD", "A");
        Cookie inactiveCookie = createSession(inactiveUser, csrf(), null).getResponse().getCookie("IDENTIPAT_STANDARD_SESSION");
        inactiveUser.setStatus("I");
        userRepository.saveAndFlush(inactiveUser);
        mockMvc.perform(get("/standard-session").cookie(inactiveCookie)).andExpect(status().isUnauthorized());
        StandardSession inactive = sessionRepository.findByTokenHash(tokenService.hash(inactiveCookie.getValue())).orElseThrow();
        assertThat(inactive.getCloseReason()).isEqualTo(StandardSessionCloseReason.USER_INACTIVE);
    }

    @Test
    void corsAllowsExplicitDevOriginCredentialsAndCsrfHeaderOnly() throws Exception {
        mockMvc.perform(options("/standard-sessions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "X-XSRF-TOKEN"));
        mockMvc.perform(get("/standard-session/csrf").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void deletingUserWithSessionHistoryDeactivatesAndPreservesTraceability() throws Exception {
        User user = saveUser("STANDARD", "A");
        createSession(user, csrf(), null);

        userService.delete(user.getUserId());

        assertThat(userRepository.findById(user.getUserId())).get()
                .extracting(User::getStatus).isEqualTo("I");
        assertThat(sessionRepository.existsByUserUserId(user.getUserId())).isTrue();
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/standard-session/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false)).andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"), token);
    }

    private MvcResult createSession(User user, Csrf csrf, Cookie presented) throws Exception {
        var request = csrfPost("/standard-sessions", csrf).content(sessionBody(user));
        if (presented != null) request.cookie(presented);
        return mockMvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.consentRequired").value(true)).andReturn();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder csrfPost(String path, Csrf csrf) {
        return post(path).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consentPost(
            Cookie standard, Csrf csrf, String decision, String version) {
        return post("/standard-session/consent").cookie(standard, csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(consentBody(decision, version));
    }

    private User saveUser(String type, String status) {
        User user = new User();
        user.setDoi("TEST-" + UUID.randomUUID());
        user.setDoiType("DNI");
        user.setUserType(type);
        user.setStatus(status);
        return userRepository.saveAndFlush(user);
    }

    private static String sessionBody(User user) {
        return "{\"doi\":\"" + user.getDoi() + "\",\"doiType\":\"DNI\"}";
    }

    private static String consentBody(String decision, String version) {
        return "{\"consentVersion\":\"" + version + "\",\"decision\":\"" + decision + "\"}";
    }

    private record Csrf(Cookie cookie, String token) { }
}
