package com.zim4ik.customer;

import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.exception.CustomerFraudException;
import com.zim4ik.customer.repository.CustomerRepository;
import com.zim4ik.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
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
        TopicExchange internalExchange() {
            return new TopicExchange("internal.exchange");
        }

        @Bean
        Queue testNotificationQueue() {
            return new Queue(TEST_QUEUE);
        }

        @Bean
        Binding testNotificationBinding() {
            return BindingBuilder.bind(testNotificationQueue())
                    .to(internalExchange())
                    .with("internal.notification.routing-key");
        }
    }

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockBean
    private FraudClient fraudClient;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    void registerCustomer_persistsCustomerAndPublishesNotification() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(false));

        Customer customer = customerService.registerCustomer(REQUEST);

        assertThat(customerRepository.findById(customer.getId()))
                .get()
                .extracting(Customer::getEmail)
                .isEqualTo("yan@example.com");

        NotificationRequest notification = rabbitTemplate.receiveAndConvert(
                TEST_QUEUE, 5000, new ParameterizedTypeReference<>() {});
        assertThat(notification).isEqualTo(
                new NotificationRequest(customer.getId(), "yan@example.com", "Welcome, Yan!"));
    }

    @Test
    void registerCustomer_rollsBackAndSendsNothing_whenFraudster() {
        when(fraudClient.isFraudster(anyInt())).thenReturn(new FraudCheckResponse(true));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(CustomerFraudException.class);

        assertThat(customerRepository.count()).isZero();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 1000)).isNull();
    }

    @Test
    void registerCustomer_rollsBackAndSendsNothing_whenFraudServiceFails() {
        when(fraudClient.isFraudster(anyInt())).thenThrow(new IllegalStateException("fraud is down"));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customerRepository.count()).isZero();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 1000)).isNull();
    }
}
