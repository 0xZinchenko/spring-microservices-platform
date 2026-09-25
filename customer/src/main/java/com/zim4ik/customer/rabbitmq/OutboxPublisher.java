package com.zim4ik.customer.rabbitmq;

import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 5000;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${outbox.publisher.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> events = outboxEventRepository.lockUnpublished(batchSize);
        for (OutboxEvent event : events) {
            try {
                send(event);
                event.setPublishedAt(LocalDateTime.now());
                log.info("📤 Published outbox event {} to {}", event.getId(), event.getExchange());
            } catch (RuntimeException e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(String.valueOf(e.getMessage())));
                log.warn("⚠️ Failed to publish outbox event {} (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
                break;
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

        rabbitTemplate.invoke(operations -> {
            operations.send(event.getExchange(), event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    private String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
