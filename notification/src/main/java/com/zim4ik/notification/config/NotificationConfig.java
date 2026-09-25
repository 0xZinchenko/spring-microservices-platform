package com.zim4ik.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {

    @Value("${rabbitmq.exchanges.internal}")
    private String internalExchange;

    @Value("${rabbitmq.exchanges.dead-letter}")
    private String deadLetterExchange;

    @Value("${rabbitmq.queues.notification}")
    private String notificationQueue;

    @Value("${rabbitmq.queues.notification-dead-letter}")
    private String notificationDeadLetterQueue;

    @Value("${rabbitmq.routing-keys.internal-notification}")
    private String internalNotificationRoutingKey;

    @Bean
    public TopicExchange internalTopicExchange() {
        return new TopicExchange(internalExchange);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(deadLetterExchange);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(notificationQueue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(internalNotificationRoutingKey)
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(notificationDeadLetterQueue).build();
    }

    @Bean
    public Binding internalToNotificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(internalTopicExchange())
                .with(internalNotificationRoutingKey);
    }

    @Bean
    public Binding deadLetterToNotificationDeadLetterBinding() {
        return BindingBuilder
                .bind(notificationDeadLetterQueue())
                .to(deadLetterExchange())
                .with(internalNotificationRoutingKey);
    }
}
