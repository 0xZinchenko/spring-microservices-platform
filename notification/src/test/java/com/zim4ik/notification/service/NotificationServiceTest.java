package com.zim4ik.notification.service;

import com.zim4ik.clients.notification.NotificationRequest;
import com.zim4ik.notification.entity.Notification;
import com.zim4ik.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void send_savesNotification() {
        notificationService.send(new NotificationRequest(1, "yan@example.com", "Welcome, Yan!"));

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertThat(notification.getValue().getToCustomerId()).isEqualTo(1);
        assertThat(notification.getValue().getToCustomerEmail()).isEqualTo("yan@example.com");
        assertThat(notification.getValue().getMessage()).isEqualTo("Welcome, Yan!");
        assertThat(notification.getValue().getSender()).isEqualTo("Zim4ik");
        assertThat(notification.getValue().getSentAt()).isNotNull();
    }

    @Test
    void send_storesSourceMessageId() {
        notificationService.send(new NotificationRequest(1, "yan@example.com", "Welcome, Yan!"), "customer-outbox-1");

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertThat(notification.getValue().getSourceMessageId()).isEqualTo("customer-outbox-1");
    }

    @Test
    void send_skipsDuplicateMessage() {
        when(notificationRepository.existsBySourceMessageId("customer-outbox-1")).thenReturn(true);

        notificationService.send(new NotificationRequest(1, "yan@example.com", "Welcome, Yan!"), "customer-outbox-1");

        verify(notificationRepository, never()).save(any());
    }
}
