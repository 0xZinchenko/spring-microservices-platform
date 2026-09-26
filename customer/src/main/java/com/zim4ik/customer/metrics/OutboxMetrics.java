package com.zim4ik.customer.metrics;

import com.zim4ik.customer.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxMetrics implements MeterBinder {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.events.pending", outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published to RabbitMQ")
                .register(registry);
        registry.counter("outbox.events.published");
        registry.counter("outbox.publish.failures");
    }
}
