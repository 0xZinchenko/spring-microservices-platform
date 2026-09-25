package com.zim4ik.notification;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
})
class NotificationConsumerIntegrationTest {

    private static final String EXCHANGE = "internal.exchange";
    private static final String ROUTING_KEY = "internal.notification.routing-key";
    private static final String DEAD_LETTER_QUEUE = "notification.queue.dlq";

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    static {
        postgres.start();
        rabbitmq.start();
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
    }

    @Test
    void consume_savesNotificationFromQueue() {
        send(new NotificationRequest(5, "yan@example.com", "Welcome, Yan!"), "customer-outbox-1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .singleElement()
                        .satisfies(notification -> {
                            assertThat(notification.getToCustomerId()).isEqualTo(5);
                            assertThat(notification.getToCustomerEmail()).isEqualTo("yan@example.com");
                            assertThat(notification.getMessage()).isEqualTo("Welcome, Yan!");
                            assertThat(notification.getSourceMessageId()).isEqualTo("customer-outbox-1");
                        }));
    }

    @Test
    void consume_ignoresDuplicateDelivery_withSameMessageId() {
        NotificationRequest request = new NotificationRequest(6, "anna@example.com", "Welcome, Anna!");

        send(request, "customer-outbox-2");
        send(request, "customer-outbox-2");
        send(new NotificationRequest(7, "bob@example.com", "Welcome, Bob!"), "customer-outbox-3");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findAll()).hasSize(2));
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .extracting(notification -> notification.getSourceMessageId())
                        .containsExactlyInAnyOrder("customer-outbox-2", "customer-outbox-3"));
        assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 2000)).isNull();
    }

    @Test
    void consume_movesMessageToDeadLetterQueue_whenProcessingKeepsFailing() {
        send(new NotificationRequest(8, "yan@example.com", "x".repeat(300)), "customer-outbox-4");

        Message deadLetter = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getMessageId()).isEqualTo("customer-outbox-4");
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void consume_movesMessageToDeadLetterQueue_whenPayloadIsNotValidJson() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId("broken-1");
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY,
                new Message("{not json".getBytes(StandardCharsets.UTF_8), properties));

        Message deadLetter = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15000);

        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getMessageId()).isEqualTo("broken-1");
        assertThat(notificationRepository.count()).isZero();
    }

    private void send(NotificationRequest request, String messageId) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, request, message -> {
            message.getMessageProperties().setMessageId(messageId);
            return message;
        });
    }
}
