package com.zim4ik.notification;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class NotificationConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void consume_savesNotificationFromQueue() {
        amqpTemplate.convertAndSend(
                "internal.exchange",
                "internal.notification.routing-key",
                new NotificationRequest(5, "yan@example.com", "Welcome, Yan!"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .singleElement()
                        .satisfies(notification -> {
                            assertThat(notification.getToCustomerId()).isEqualTo(5);
                            assertThat(notification.getToCustomerEmail()).isEqualTo("yan@example.com");
                            assertThat(notification.getMessage()).isEqualTo("Welcome, Yan!");
                        }));
    }
}
