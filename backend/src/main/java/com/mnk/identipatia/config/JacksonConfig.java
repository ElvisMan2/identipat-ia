package com.mnk.identipatia.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    private final String dateFormat;
    private final TimeZone timeZone;

    public JacksonConfig(
            @Value("${spring.jackson.date-format}") String dateFormat,
            @Value("${spring.jackson.time-zone}") String timeZone) {
        this.dateFormat = dateFormat;
        this.timeZone = TimeZone.getTimeZone(timeZone);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                .simpleDateFormat(dateFormat)
                .timeZone(timeZone);
    }
}

