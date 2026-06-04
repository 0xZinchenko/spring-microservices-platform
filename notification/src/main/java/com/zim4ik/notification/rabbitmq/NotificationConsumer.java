package com.zim4ik.notification.rabbitmq;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.notification.NotificationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "${rabbitmq.queues.notification}")
    public void consume(NotificationRequest request) {
        log.info("📩 Received from queue: {}", request);
        notificationService.send(request);
    }
}
