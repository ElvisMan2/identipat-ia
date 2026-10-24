package com.mnk.identipatia.advice;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock());

    @Test
    void oversizedMultipartUsesAudioCodeForAudioEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyses/audio");
        request.setServletPath("/analyses/audio");

        Map<String, Object> body = handler.handleMaxUploadSizeExceeded(request).getBody();

        assertThat(body).containsEntry("code", "AUDIO_TOO_LARGE");
    }

    @Test
    void oversizedMultipartPreservesPdfCodeForPdfEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyses/pdf");
        request.setServletPath("/analyses/pdf");

        Map<String, Object> body = handler.handleMaxUploadSizeExceeded(request).getBody();

        assertThat(body).containsEntry("code", "PDF_TOO_LARGE");
    }
}
