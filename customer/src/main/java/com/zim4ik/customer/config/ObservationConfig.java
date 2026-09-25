package com.zim4ik.customer.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {

    @Bean
    public ObservationPredicate skipScheduledTaskObservations() {
        return (name, context) -> !name.startsWith("tasks.scheduled");
    }
}
