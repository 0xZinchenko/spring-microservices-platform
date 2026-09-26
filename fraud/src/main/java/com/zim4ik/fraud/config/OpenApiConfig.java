package com.zim4ik.fraud.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Fraud API")
                .version("v1")
                .description("Internal API of the fraud service, called by the customer service."));
    }
}
