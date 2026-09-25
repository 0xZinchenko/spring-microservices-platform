package com.zim4ik.customer;

import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.exception.CustomerFraudException;
import com.zim4ik.customer.repository.CustomerRepository;
import com.zim4ik.customer.repository.OutboxEventRepository;
import com.zim4ik.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "outbox.publisher.fixed-delay=200"
})
class CustomerRegistrationIntegrationTest {

    private static final String TEST_QUEUE = "test.notification.queue";

    private static final CustomerRegistrationRequest REQUEST =
            new CustomerRegistrationRequest("Yan", "Zinchenko", "yan@example.com");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    @TestConfiguration
    static class TestQueueConfig {

        @Bean
        Queue testNotificationQueue() {
            return new Queue(TEST_QUEUE);
        }

        @Bean
        Binding testNotificationBinding(Queue testNotificationQueue, TopicExchange internalTopicExchange) {
            return BindingBuilder.bind(testNotificationQueue)
                    .to(internalTopicExchange)
                    .with("internal.notification.routing-key");
        }
    }

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @MockBean
    private FraudClient fraudClient;

    @BeforeEach
    void setUp() {
        rabbitAdmin.initialize();
        outboxEventRepository.deleteAll();
        customerRepository.deleteAll();
        rabbitAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    void registerCustomer_persistsCustomerAndPublishesNotificationThroughOutbox() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(false));

        Customer customer = customerService.registerCustomer(REQUEST);

        assertThat(customerRepository.findById(customer.getId()))
                .get()
                .extracting(Customer::getEmail)
                .isEqualTo("yan@example.com");

        assertThat(receiveNotification()).isEqualTo(
                new NotificationRequest(customer.getId(), "yan@example.com", "Welcome, Yan!"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxEventRepository.findAll())
                        .singleElement()
                        .extracting(OutboxEvent::getPublishedAt)
                        .isNotNull());
    }

    @Test
    void registerCustomer_keepsEventInOutboxAndRetries_whenBrokerRejectsMessage() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(false));
        rabbitAdmin.deleteExchange("internal.exchange");

        Customer customer = customerService.registerCustomer(REQUEST);

        assertThat(customerRepository.existsById(customer.getId())).isTrue();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxEventRepository.findAll())
                        .singleElement()
                        .satisfies(event -> {
                            assertThat(event.getPublishedAt()).isNull();
                            assertThat(event.getAttempts()).isPositive();
                            assertThat(event.getLastError()).isNotBlank();
                        }));

        rabbitAdmin.initialize();

        assertThat(receiveNotification()).isEqualTo(
                new NotificationRequest(customer.getId(), "yan@example.com", "Welcome, Yan!"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero());
    }

    @Test
    void registerCustomer_rollsBackAndSendsNothing_whenFraudster() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(true));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(CustomerFraudException.class);

        assertThat(customerRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 1000)).isNull();
    }

    @Test
    void registerCustomer_rollsBackAndSendsNothing_whenFraudServiceFails() {
        when(fraudClient.isFraudster(anyInt())).thenThrow(new IllegalStateException("fraud is down"));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customerRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 1000)).isNull();
    }

    private NotificationRequest receiveNotification() {
        return rabbitTemplate.receiveAndConvert(TEST_QUEUE, 10000, new ParameterizedTypeReference<>() {});
    }
}
