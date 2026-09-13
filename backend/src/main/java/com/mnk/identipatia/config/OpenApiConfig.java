package com.mnk.identipatia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI identipatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IDENTIPAT-IA API")
                        .version("v1")
                        .description("API IDENTIPAT-IA. ADMIN usa JWT Bearer. STANDARD usa una cookie HttpOnly temporal "
                                + "independiente y XSRF-TOKEN/X-XSRF-TOKEN para CSRF; CSRF no autentica."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
