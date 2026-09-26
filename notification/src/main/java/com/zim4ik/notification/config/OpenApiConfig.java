package com.zim4ik.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Notification API")
                .version("v1")
                .description("Internal API of the notification service. Notifications normally arrive through RabbitMQ."));
    }
}
