package com.mnk.identipatia.preprocessing.config;

import com.mnk.identipatia.preprocessing.client.PreprocessingServiceClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PreprocessingServiceProperties.class)
public class PreprocessingServiceClientConfiguration {

    @Bean
    RestClient preprocessingRestClient(PreprocessingServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    PreprocessingServiceClient preprocessingServiceClient(RestClient preprocessingRestClient) {
        return new PreprocessingServiceClient(preprocessingRestClient);
    }
}
