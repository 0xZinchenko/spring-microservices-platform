package com.zim4ik.customer.event;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.customer.service.OutboxService;
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

    private final OutboxService outboxService;

    @Value("${rabbitmq.exchange.internal}")
    private String internalExchange;

    @Value("${rabbitmq.routing-key.internal-notification}")
    private String internalNotificationRoutingKey;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        NotificationRequest notificationRequest = new NotificationRequest(
                event.customerId(),
                event.email(),
                "Welcome, " + event.firstName() + "!"
        );

        outboxService.enqueue(internalExchange, internalNotificationRoutingKey, notificationRequest);

        log.info("📥 Notification event stored in outbox for customer {}", event.customerId());
    }
}
