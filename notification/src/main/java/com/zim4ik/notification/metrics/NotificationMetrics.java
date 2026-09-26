package com.zim4ik.notification.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.counter("notifications.processed", "result", "saved");
        registry.counter("notifications.processed", "result", "duplicate");
    }
}
