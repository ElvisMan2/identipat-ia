package com.mnk.identipatia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    @Test
    void productionSecretsHaveMandatoryPlaceholdersWithoutFallbacks() throws IOException {
        String yaml = new ClassPathResource("application-prod.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("password: ${DB_PASSWORD}")
                .contains("secret: ${JWT_SECRET}")
                .doesNotContain("${DB_PASSWORD:")
                .doesNotContain("${JWT_SECRET:");
    }

    @Test
    void incompleteProductionConfigurationFailsForMissingJwtSecret() {
        productionContext(RequiredJwtSecret.class,
                "app.jwt.secret=${F04_MISSING_JWT_SECRET}").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCauseOf(context.getStartupFailure()).getMessage()).contains("F04_MISSING_JWT_SECRET");
        });
    }

    @Test
    void incompleteProductionConfigurationFailsForMissingDatabasePassword() {
        productionContext(RequiredDatabasePassword.class,
                "spring.datasource.password=${F04_MISSING_DB_PASSWORD}").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCauseOf(context.getStartupFailure()).getMessage()).contains("F04_MISSING_DB_PASSWORD");
        });
    }

    private static Throwable rootCauseOf(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private static ApplicationContextRunner productionContext(Class<?> configuration, String requiredProperty) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withPropertyValues(requiredProperty)
                .withUserConfiguration(configuration);
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredJwtSecret {

        @Bean
        Object requiredJwtSecret(@Value("${app.jwt.secret}") String ignored) {
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredDatabasePassword {

        @Bean
        Object requiredDatabasePassword(@Value("${spring.datasource.password}") String ignored) {
            return new Object();
        }
    }
}
