package com.zim4ik.customer.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RegistrationMetrics {

    public static final String SUCCESS = "success";
    public static final String INVALID = "invalid";
    public static final String DUPLICATE = "duplicate";
    public static final String FRAUD = "fraud";
    public static final String FRAUD_UNAVAILABLE = "fraud_unavailable";

    private static final List<String> RESULTS = List.of(SUCCESS, INVALID, DUPLICATE, FRAUD, FRAUD_UNAVAILABLE);

    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerCounters() {
        RESULTS.forEach(this::counter);
    }

    public void record(String result) {
        counter(result).increment();
    }

    private Counter counter(String result) {
        return Counter.builder("customer.registrations")
                .description("Customer registration attempts by result")
                .tag("result", result)
                .register(meterRegistry);
    }
}
