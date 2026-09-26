package com.zim4ik.customer.rabbitmq;

import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 5000;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;
    private final MeterRegistry meterRegistry;

    @Value("${outbox.publisher.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> events = outboxEventRepository.lockUnpublished(batchSize);
        for (OutboxEvent event : events) {
            Span span = propagator.extract(traceHeaders(event), Map::get)
                    .name("outbox publish")
                    .tag("outbox.event.id", String.valueOf(event.getId()))
                    .start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                send(event);
                event.setPublishedAt(LocalDateTime.now());
                meterRegistry.counter("outbox.events.published").increment();
                log.info("📤 Published outbox event {} to {}", event.getId(), event.getExchange());
            } catch (RuntimeException e) {
                span.error(e);
                event.setAttempts(event.getAttempts() + 1);
                meterRegistry.counter("outbox.publish.failures").increment();
                event.setLastError(truncate(String.valueOf(e.getMessage())));
                log.warn("⚠️ Failed to publish outbox event {} (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
                break;
            } finally {
                span.end();
            }
        }
    }

    private void send(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId("customer-outbox-" + event.getId());
        properties.setHeader("__TypeId__", event.getPayloadType());
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlationData = new CorrelationData(properties.getMessageId());
        rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message, correlationData);

        CorrelationData.Confirm confirm = awaitConfirm(correlationData);
        if (!confirm.ack()) {
            throw new AmqpException("Broker rejected message: " + confirm.reason());
        }
        if (correlationData.getReturned() != null) {
            throw new AmqpException("Message is unroutable: " + correlationData.getReturned().getReplyText());
        }
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlationData) {
        try {
            return correlationData.getFuture().get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for publisher confirm", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AmqpException("No publisher confirm received", e);
        }
    }

    private Map<String, String> traceHeaders(OutboxEvent event) {
        if (event.getTraceHeaders() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(event.getTraceHeaders(), new TypeReference<>() {});
        } catch (JacksonException e) {
            log.warn("Ignoring unreadable trace headers of outbox event {}", event.getId());
            return Map.of();
        }
    }

    private String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
