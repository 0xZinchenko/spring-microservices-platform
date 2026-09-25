package com.zim4ik.customer.rabbitmq;

import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
    }

    @Test
    void publishPending_sendsEventWithTypeHeaderAndMarksItPublished() throws Exception {
        OutboxEvent event = event(1L);
        when(outboxEventRepository.lockUnpublished(anyInt())).thenReturn(List.of(event));
        RabbitOperations operations = mock(RabbitOperations.class);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation ->
                invocation.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operations));

        outboxPublisher.publishPending();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(operations).send(eq("internal.exchange"), eq("internal.notification.routing-key"), message.capture());
        verify(operations).waitForConfirmsOrDie(anyLong());
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("customer-outbox-1");
        assertThat(message.getValue().getMessageProperties().<String>getHeader("__TypeId__"))
                .isEqualTo("com.example.Payload");
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void publishPending_keepsEventUnpublishedAndStops_whenBrokerIsUnavailable() {
        OutboxEvent first = event(1L);
        OutboxEvent second = event(2L);
        when(outboxEventRepository.lockUnpublished(anyInt())).thenReturn(List.of(first, second));
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpConnectException(new RuntimeException("connection refused")));

        outboxPublisher.publishPending();

        verify(rabbitTemplate, times(1)).invoke(any());
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("connection refused");
        assertThat(second.getAttempts()).isZero();
    }

    private static OutboxEvent event(Long id) {
        return OutboxEvent.builder()
                .id(id)
                .exchange("internal.exchange")
                .routingKey("internal.notification.routing-key")
                .payloadType("com.example.Payload")
                .payload("{\"id\":" + id + "}")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
