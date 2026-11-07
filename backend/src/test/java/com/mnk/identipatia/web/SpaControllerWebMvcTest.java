package com.mnk.identipatia.web;

import com.mnk.identipatia.config.JwtAuthenticationFilter;
import com.mnk.identipatia.config.SecurityConfig;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
class SpaControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void rootServesTheAngularIndex() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(startsWith("<!doctype html>")))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void hashedJavascriptAssetIsPublicAndHasTheCorrectContentType() throws Exception {
        mockMvc.perform(get("/main.abc123.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/javascript"));
    }

    @Test
    void clientRouteReloadServesTheAngularIndex() throws Exception {
        mockMvc.perform(get("/terminos-y-privacidad"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(startsWith("<!doctype html>")));
    }

    @Test
    void unknownApiPathIsNotRewrittenToTheAngularIndex() throws Exception {
        mockMvc.perform(get("/analyses/not-an-endpoint/extra"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", not(startsWith(MediaType.TEXT_HTML_VALUE))))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void administrativePathsRemainProtected() throws Exception {
        mockMvc.perform(get("/admin/not-an-endpoint"))
                .andExpect(status().isUnauthorized());
    }
}
