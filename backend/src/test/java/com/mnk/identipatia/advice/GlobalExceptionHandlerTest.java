package com.mnk.identipatia.advice;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

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

    @Test
    void unreadableGuidanceViewUsesEndpointSpecificCode() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/analyses/00000000-0000-0000-0000-000000000000/guidance/UTILITY_MODEL/view");

        Map<String, Object> body = handler.handleUnreadableRequest(request).getBody();

        assertThat(body).containsEntry("code", "INVALID_GUIDANCE_VIEW_REQUEST");
    }

    @Test
    void unreadableOtherRequestPreservesGenericCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyses/text");

        Map<String, Object> body = handler.handleUnreadableRequest(request).getBody();

        assertThat(body).containsEntry("code", "INVALID_REQUEST");
    }

    @Test
    void missingResourceReturnsJsonNotFound() {
        var response = handler.handleNoResourceFound();

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    @Test
    void admissionRejectionWritesNumericRetryAfterHeader() {
        var response = handler.handleApiException(
                new com.mnk.identipatia.exception.ApiException(
                        TOO_MANY_REQUESTS, "ANALYSIS_QUOTA_EXCEEDED", "Quota exceeded", 17L),
                new org.springframework.mock.web.MockHttpServletResponse());

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("17");
        assertThat(response.getBody()).containsEntry("code", "ANALYSIS_QUOTA_EXCEEDED");
    }
}
