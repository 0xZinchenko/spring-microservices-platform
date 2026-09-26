package com.zim4ik.notification.service;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.notification.entity.Notification;
import com.zim4ik.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MeterRegistry meterRegistry;

    public void send(NotificationRequest notificationRequest, String sourceMessageId) {
        if (sourceMessageId != null && notificationRepository.existsBySourceMessageId(sourceMessageId)) {
            log.info("♻️ Skipping duplicate message {}", sourceMessageId);
            meterRegistry.counter("notifications.processed", "result", "duplicate").increment();
            return;
        }
        notificationRepository.save(
                Notification.builder()
                        .toCustomerId(notificationRequest.toCustomerId())
                        .toCustomerEmail(notificationRequest.toCustomerEmail())
                        .sender("Zim4ik")
                        .message(notificationRequest.message())
                        .sentAt(LocalDateTime.now())
                        .sourceMessageId(sourceMessageId)
                        .build()
        );
        meterRegistry.counter("notifications.processed", "result", "saved").increment();
    }
}
