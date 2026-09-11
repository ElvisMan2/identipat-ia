package com.mnk.identipatia.preprocessing.client;

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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class PreprocessingServiceClientTest {

    private MockRestServiceServer server;
    private PreprocessingServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://preprocessing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PreprocessingServiceClient(builder.build());
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
}
