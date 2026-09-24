package com.zim4ik.customer.rabbitmq;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.customer.event.CustomerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRegisteredListener {

    private final RabbitMQMessageProducer rabbitMQMessageProducer;

    @Value("${rabbitmq.exchange.internal}")
    private String internalExchange;

    @Value("${rabbitmq.routing-key.internal-notification}")
    private String internalNotificationRoutingKey;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        NotificationRequest notificationRequest = new NotificationRequest(
                event.customerId(),
                event.email(),
                "Welcome, " + event.firstName() + "!"
        );

        rabbitMQMessageProducer.publish(
                notificationRequest,
                internalExchange,
                internalNotificationRoutingKey
        );

        log.info("📤 Notification event sent for customer {}", event.customerId());
    }
}
