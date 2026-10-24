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
    private static final Set<String> CONTROLLED_AUDIO_CODES = Set.of(
            "AUDIO_TOO_LARGE",
            "UNSUPPORTED_AUDIO_MEDIA_TYPE",
            "INVALID_AUDIO",
            "AUDIO_DURATION_LIMIT_EXCEEDED",
            "AUDIO_TRANSCRIPTION_EMPTY",
            "AUDIO_MODEL_NOT_READY",
            "AUDIO_TRANSCRIPTION_ERROR");

    private final RestClient pdfRestClient;
    private final RestClient audioRestClient;
    private final ObjectMapper objectMapper;

    public PreprocessingServiceClient(
            RestClient pdfRestClient,
            RestClient audioRestClient,
            ObjectMapper objectMapper) {
        this.pdfRestClient = pdfRestClient;
        this.audioRestClient = audioRestClient;
        this.objectMapper = objectMapper;
    }

    public PreprocessingHealthResponse health() {
        try {
            PreprocessingHealthResponse response = pdfRestClient.get()
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
            PdfExtractionResponse response = pdfRestClient.post()
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

    public AudioTranscriptionResponse transcribeAudio(byte[] audioBytes, String mediaType) {
        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "audio";
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(mediaType));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        try {
            AudioTranscriptionResponse response = audioRestClient.post()
                    .uri("/v1/audio/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(AudioTranscriptionResponse.class);
            if (response == null || !response.isValid()) {
                throw new PreprocessingServiceUnavailableException(
                        "Preprocessing service returned an invalid audio transcription response");
            }
            return response;
        } catch (PreprocessingServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()
                    || exception.getStatusCode().value() == 503) {
                throw controlledAudioError(exception);
            }
            throw new PreprocessingServiceUnavailableException(
                    "Preprocessing service audio transcription failed", exception);
        } catch (RestClientException exception) {
            throw new PreprocessingServiceUnavailableException(
                    "Preprocessing service audio transcription failed", exception);
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

    private RuntimeException controlledAudioError(RestClientResponseException exception) {
        try {
            AudioTranscriptionErrorResponse response = objectMapper.readValue(
                    exception.getResponseBodyAsByteArray(), AudioTranscriptionErrorResponse.class);
            if (response != null && CONTROLLED_AUDIO_CODES.contains(response.code())) {
                return new PreprocessingAudioException(response.code());
            }
        } catch (IOException ignored) {
            // An invalid error payload is treated as service unavailability below.
        }
        return new PreprocessingServiceUnavailableException(
                "Preprocessing service returned an invalid audio error response", exception);
    }
}
