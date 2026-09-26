package com.zim4ik.fraud.metrics;

import com.zim4ik.fraud.model.FraudReason;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class FraudMetrics implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.counter("fraud.checks", "result", "clean");
        Arrays.stream(FraudReason.values())
                .forEach(reason -> registry.counter("fraud.checks", "result", reason.name().toLowerCase(Locale.ROOT)));
    }
}
