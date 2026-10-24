package com.mnk.identipatia.preprocessing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.Set;

/**
 * Internal Java-to-Python client. It is intentionally not exposed through a public controller.
 */
public class PreprocessingServiceClient {
    private static final Set<String> CONTROLLED_PDF_CODES = Set.of(
            "INVALID_PDF",
            "PDF_ENCRYPTED",
            "PDF_PAGE_LIMIT_EXCEEDED",
            "PDF_TEXT_NOT_EXTRACTABLE",
            "PDF_TOO_LARGE",
            "UNSUPPORTED_PDF_MEDIA_TYPE");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PreprocessingServiceClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public PreprocessingHealthResponse health() {
        try {
            PreprocessingHealthResponse response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(PreprocessingHealthResponse.class);
            if (response == null || !response.isValid()) {
                throw new PreprocessingServiceUnavailableException(
                        "Preprocessing service returned an invalid health response");
            }
            return response;
        } catch (PreprocessingServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PreprocessingServiceUnavailableException(
                    "Preprocessing service health check failed", exception);
        }
    }

    public PdfExtractionResponse extractPdf(byte[] pdfBytes) {
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "document.pdf";
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        try {
            PdfExtractionResponse response = restClient.post()
                    .uri("/v1/pdf/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(PdfExtractionResponse.class);
            if (response == null || !response.isValid()) {
                throw new PreprocessingServiceUnavailableException(
                        "Preprocessing service returned an invalid PDF extraction response");
            }
            return response;
        } catch (PreprocessingServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw controlledPdfError(exception);
            }
            throw new PreprocessingServiceUnavailableException(
                    "Preprocessing service PDF extraction failed", exception);
        } catch (RestClientException exception) {
            throw new PreprocessingServiceUnavailableException(
                    "Preprocessing service PDF extraction failed", exception);
        }
    }

    private RuntimeException controlledPdfError(RestClientResponseException exception) {
        try {
            PdfExtractionErrorResponse response = objectMapper.readValue(
                    exception.getResponseBodyAsByteArray(), PdfExtractionErrorResponse.class);
            if (response != null && CONTROLLED_PDF_CODES.contains(response.code())) {
                return new PreprocessingPdfException(response.code());
            }
        } catch (IOException ignored) {
            // An invalid error payload is treated as service unavailability below.
        }
        return new PreprocessingServiceUnavailableException(
                "Preprocessing service returned an invalid PDF error response", exception);
    }
}
