package com.mnk.identipatia.preprocessing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class PreprocessingServiceClientTest {

    private MockRestServiceServer server;
    private PreprocessingServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://preprocessing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PreprocessingServiceClient(builder.build(), new ObjectMapper());
    }

    @Test
    void healthCallsConfiguredBaseUrlAndDeserializesResponse() {
        server.expect(requestTo("http://preprocessing.test/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"status":"UP","service":"preprocessing-service","version":"0.1.0"}
                        """, MediaType.APPLICATION_JSON));

        PreprocessingHealthResponse response = client.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.service()).isEqualTo("preprocessing-service");
        assertThat(response.version()).isEqualTo("0.1.0");
        server.verify();
    }

    @Test
    void healthConvertsServerFailureToControlledException() {
        server.expect(requestTo("http://preprocessing.test/health"))
                .andExpect(method(GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.health())
                .isInstanceOf(PreprocessingServiceUnavailableException.class)
                .hasMessage("Preprocessing service health check failed");
        server.verify();
    }

    @Test
    void healthRejectsInvalidResponse() {
        server.expect(requestTo("http://preprocessing.test/health"))
                .andRespond(withSuccess("""
                        {"status":"UP","service":"unexpected"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.health())
                .isInstanceOf(PreprocessingServiceUnavailableException.class)
                .hasMessage("Preprocessing service returned an invalid health response");
        server.verify();
    }

    @Test
    void extractPdfSendsMultipartAndDeserializesStableResponse() {
        server.expect(requestTo("http://preprocessing.test/v1/pdf/extract"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"text":"Texto extraído suficientemente largo","pageCount":2,
                         "characterCount":36,"extractionMethod":"TEXT_LAYER"}
                        """, MediaType.APPLICATION_JSON));

        PdfExtractionResponse response = client.extractPdf("%PDF-test".getBytes());

        assertThat(response.pageCount()).isEqualTo(2);
        assertThat(response.text()).isEqualTo("Texto extraído suficientemente largo");
        server.verify();
    }

    @Test
    void extractPdfMapsControlledClientErrorWithoutLeakingMessage() {
        server.expect(requestTo("http://preprocessing.test/v1/pdf/extract"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"PDF_ENCRYPTED\",\"message\":\"internal detail\"}"));

        assertThatThrownBy(() -> client.extractPdf("%PDF-test".getBytes()))
                .isInstanceOf(PreprocessingPdfException.class)
                .extracting(exception -> ((PreprocessingPdfException) exception).getCode())
                .isEqualTo("PDF_ENCRYPTED");
        server.verify();
    }

    @Test
    void extractPdfMapsServerFailureToUnavailable() {
        server.expect(requestTo("http://preprocessing.test/v1/pdf/extract"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.extractPdf("%PDF-test".getBytes()))
                .isInstanceOf(PreprocessingServiceUnavailableException.class);
        server.verify();
    }
}
