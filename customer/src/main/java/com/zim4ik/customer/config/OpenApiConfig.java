package com.zim4ik.customer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Customer API")
                .version("v1")
                .description("Public API of the customer service, exposed through the API Gateway."));
    }
}
