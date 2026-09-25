package com.zim4ik.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String exchange;

    private String routingKey;

    private String payloadType;

    @Column(columnDefinition = "text")
    private String payload;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private int attempts;

    private String lastError;

    @Column(columnDefinition = "text")
    private String traceHeaders;
}
