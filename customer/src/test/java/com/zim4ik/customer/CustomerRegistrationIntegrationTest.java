package com.zim4ik.customer;

import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.exception.CustomerAlreadyExistsException;
import com.zim4ik.customer.exception.CustomerFraudException;
import com.zim4ik.customer.repository.CustomerRepository;
import com.zim4ik.customer.repository.OutboxEventRepository;
import com.zim4ik.customer.service.CustomerService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@AutoConfigureTracing
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "outbox.publisher.fixed-delay=200",
        "management.tracing.export.zipkin.enabled=false"
})
class CustomerRegistrationIntegrationTest {

    private static final String TEST_QUEUE = "test.notification.queue";

    private static final CustomerRegistrationRequest REQUEST =
            new CustomerRegistrationRequest("Yan", "Zinchenko", "yan@example.com");

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.3-management");

    static {
        postgres.start();
        rabbitmq.start();
    }

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

    @Autowired
    private Tracer tracer;

    @Autowired
    private Binding testNotificationBinding;

    @MockitoBean
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
        await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
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
        await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero());
    }

    @Test
    void registerCustomer_keepsEventInOutboxAndRetries_whenMessageIsUnroutable() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(false));
        rabbitAdmin.removeBinding(testNotificationBinding);

        Customer customer = customerService.registerCustomer(REQUEST);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxEventRepository.findAll())
                        .singleElement()
                        .satisfies(event -> {
                            assertThat(event.getPublishedAt()).isNull();
                            assertThat(event.getAttempts()).isPositive();
                            assertThat(event.getLastError()).contains("unroutable");
                        }));

        rabbitAdmin.declareBinding(testNotificationBinding);

        assertThat(receiveNotification()).isEqualTo(
                new NotificationRequest(customer.getId(), "yan@example.com", "Welcome, Yan!"));
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

    @Test
    void registerCustomer_rejectsSecondRegistration_withSameEmailInDifferentCase() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(false));
        customerService.registerCustomer(REQUEST);

        assertThatThrownBy(() -> customerService.registerCustomer(
                new CustomerRegistrationRequest("Other", "Person", "YAN@example.com")))
                .isInstanceOf(CustomerAlreadyExistsException.class);

        assertThat(customerRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        verify(fraudClient, times(1)).isFraudster(anyInt());
    }

    @Test
    void registerCustomer_propagatesTraceContextThroughOutboxToRabbitMq() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(false));
        Span requestSpan = tracer.nextSpan().name("test request").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(requestSpan)) {
            customerService.registerCustomer(REQUEST);
        } finally {
            requestSpan.end();
        }

        Message message = rabbitTemplate.receive(TEST_QUEUE, 10000);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().<String>getHeader("traceparent"))
                .contains(requestSpan.context().traceId());
    }

    private NotificationRequest receiveNotification() {
        return rabbitTemplate.receiveAndConvert(TEST_QUEUE, 10000, new ParameterizedTypeReference<>() {});
    }
}
