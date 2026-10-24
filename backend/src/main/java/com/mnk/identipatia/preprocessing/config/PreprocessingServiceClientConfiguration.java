package com.mnk.identipatia.preprocessing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PreprocessingServiceProperties.class)
public class PreprocessingServiceClientConfiguration {

    @Bean
    RestClient preprocessingPdfRestClient(PreprocessingServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.pdfTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    RestClient preprocessingAudioRestClient(PreprocessingServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.audioTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    PreprocessingServiceClient preprocessingServiceClient(
            @Qualifier("preprocessingPdfRestClient") RestClient pdfRestClient,
            @Qualifier("preprocessingAudioRestClient") RestClient audioRestClient,
            ObjectMapper objectMapper) {
        return new PreprocessingServiceClient(pdfRestClient, audioRestClient, objectMapper);
    }
}
