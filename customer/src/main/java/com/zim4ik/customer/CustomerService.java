package com.zim4ik.customer;

import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.customer.rabbitmq.RabbitMQMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final FraudClient fraudClient;
    private final RabbitMQMessageProducer rabbitMQMessageProducer;


    @Value("${rabbitmq.exchange.internal}")
    private String internalExchange;

    @Value("${rabbitmq.routing-key.internal-notification}")
    private String internalNotificationRoutingKey;

    public void registerCustomer(CustomerRegistrationRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .build();

        customerRepository.saveAndFlush(customer);
        log.info("✅ Saved customer with id: {}", customer.getId());


        FraudCheckResponse fraudResponse = fraudClient.isFraudster(customer.getId());
        if (fraudResponse.isFraudster()) {
            throw new IllegalStateException("Customer is fraudulent");
        }

        NotificationRequest notificationRequest = new NotificationRequest(
                customer.getId(),
                customer.getEmail(),
                "Welcome, " + customer.getFirstName() + "!"
        );

        rabbitMQMessageProducer.publish(
                notificationRequest,
                internalExchange,
                internalNotificationRoutingKey
        );

        log.info("📤 Notification event sent for customer {}", customer.getId());
    }
}