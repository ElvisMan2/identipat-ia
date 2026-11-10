package com.mnk.identipatia.analysis.controller;

import com.mnk.identipatia.advice.GlobalExceptionHandler;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.service.AnalysisAccessContext;
import com.mnk.identipatia.analysis.service.AnalysisAccessResolver;
import com.mnk.identipatia.analysis.service.AnalysisApplicationService;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.service.StandardSessionCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerAdmissionWebMvcTest {
    private final AnalysisApplicationService service = mock();
    private final AnalysisAccessResolver resolver = mock();
    private MockMvc mvc;
    private AnalysisAccessContext access;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setUserId(7L);
        access = AnalysisAccessContext.admin(admin);
        when(resolver.resolve(any(), any())).thenReturn(access);
        @SuppressWarnings("unchecked")
        ObjectProvider<StandardSessionCookieService> cookies = mock(ObjectProvider.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AnalysisController(service, resolver, mock(AnalysisProperties.class)))
                .setControllerAdvice(new GlobalExceptionHandler(cookies))
                .build();
    }

    @Test
    void quotaRejectionUsesStableJsonCodeAndRetryAfterHeader() throws Exception {
        when(service.createText(eq(access), any(), any())).thenThrow(new ApiException(
                TOO_MANY_REQUESTS, "ANALYSIS_QUOTA_EXCEEDED", "Quota exceeded", 23L));

        mvc.perform(post("/analyses/text").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa para probar cuota.\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "23"))
                .andExpect(jsonPath("$.code").value("ANALYSIS_QUOTA_EXCEEDED"));
    }

    @Test
    void busyAudioRejectionUses503AndRetryAfterHeader() throws Exception {
        when(service.createAudio(eq(access), any(), any())).thenThrow(new ApiException(
                SERVICE_UNAVAILABLE, "AUDIO_PREPROCESSING_BUSY", "Audio busy", 10L));

        mvc.perform(multipart("/analyses/audio")
                        .file("file", new byte[] {1, 2, 3})
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("AUDIO_PREPROCESSING_BUSY"));
    }

    @Test
    void requiredIdempotencyModeRejectsMissingHeaderOverHttp() throws Exception {
        when(service.createText(eq(access), any(), eq(null))).thenThrow(new ApiException(
                BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required"));

        mvc.perform(post("/analyses/text").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Descripción suficientemente extensa sin header.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }
}
