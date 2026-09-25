package com.zim4ik.customer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zim4ik.customer.entity.OutboxEvent;
import com.zim4ik.customer.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String exchange, String routingKey, Object payload) {
        return outboxEventRepository.save(OutboxEvent.builder()
                .exchange(exchange)
                .routingKey(routingKey)
                .payloadType(payload.getClass().getName())
                .payload(toJson(payload))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox payload " + payload.getClass().getName(), e);
        }
    }
}
