package com.mnk.identipatia.preprocessing.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal Java-to-Python client. It is intentionally not exposed through a public controller.
 */
public class PreprocessingServiceClient {

    private final RestClient restClient;

    public PreprocessingServiceClient(RestClient restClient) {
        this.restClient = restClient;
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
}
