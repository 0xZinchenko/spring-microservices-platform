package com.zim4ik.customer.rabbitmq;

import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private SimpleMeterRegistry meterRegistry;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        outboxPublisher = new OutboxPublisher(outboxEventRepository, rabbitTemplate,
                JsonMapper.builder().build(), Tracer.NOOP, Propagator.NOOP, meterRegistry);
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
    }

    @Test
    void publishPending_sendsEventWithTypeHeaderAndMarksItPublished_whenBrokerConfirms() {
        OutboxEvent event = event(1L);
        when(outboxEventRepository.lockUnpublished(anyInt())).thenReturn(List.of(event));
        brokerConfirms(true);

        outboxPublisher.publishPending();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("internal.exchange"), eq("internal.notification.routing-key"),
                message.capture(), any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("customer-outbox-1");
        assertThat(message.getValue().getMessageProperties().<String>getHeader("__TypeId__"))
                .isEqualTo("com.example.Payload");
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(meterRegistry.counter("outbox.events.published").count()).isEqualTo(1);
    }

    @Test
    void publishPending_keepsEventUnpublished_whenBrokerNacks() {
        OutboxEvent event = event(1L);
        when(outboxEventRepository.lockUnpublished(anyInt())).thenReturn(List.of(event));
        brokerConfirms(false);

        outboxPublisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("rejected");
        assertThat(meterRegistry.counter("outbox.publish.failures").count()).isEqualTo(1);
    }

    @Test
    void publishPending_keepsEventUnpublishedAndStops_whenBrokerIsUnavailable() {
        OutboxEvent first = event(1L);
        OutboxEvent second = event(2L);
        when(outboxEventRepository.lockUnpublished(anyInt())).thenReturn(List.of(first, second));
        doThrow(new AmqpConnectException(new RuntimeException("connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        outboxPublisher.publishPending();

        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("connection refused");
        assertThat(second.getAttempts()).isZero();
    }

    private void brokerConfirms(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
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
