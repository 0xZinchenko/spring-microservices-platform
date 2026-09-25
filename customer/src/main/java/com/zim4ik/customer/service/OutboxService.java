package com.zim4ik.customer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.repository.OutboxEventRepository;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String exchange, String routingKey, Object payload) {
        return outboxEventRepository.save(OutboxEvent.builder()
                .exchange(exchange)
                .routingKey(routingKey)
                .payloadType(payload.getClass().getName())
                .payload(toJson(payload))
                .traceHeaders(currentTraceHeaders())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String currentTraceHeaders() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        propagator.inject(context, headers, Map::put);
        return headers.isEmpty() ? null : toJson(headers);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox payload " + payload.getClass().getName(), e);
        }
    }
}
