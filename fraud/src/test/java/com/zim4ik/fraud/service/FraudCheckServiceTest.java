package com.zim4ik.fraud.service;

import com.zim4ik.fraud.dto.FraudCheckRequest;
import com.zim4ik.fraud.dto.FraudCheckResponse;
import com.zim4ik.fraud.entity.FraudCheckHistory;
import com.zim4ik.fraud.model.FraudReason;
import com.zim4ik.fraud.repository.BlockedEmailDomainRepository;
import com.zim4ik.fraud.repository.BlockedEmailRepository;
import com.zim4ik.fraud.repository.FraudCheckHistoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudCheckServiceTest {

    @Mock
    private FraudCheckHistoryRepository fraudCheckHistoryRepository;

    @Mock
    private BlockedEmailRepository blockedEmailRepository;

    @Mock
    private BlockedEmailDomainRepository blockedEmailDomainRepository;

    private SimpleMeterRegistry meterRegistry;

    private FraudCheckService fraudCheckService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        fraudCheckService = new FraudCheckService(fraudCheckHistoryRepository, blockedEmailRepository,
                blockedEmailDomainRepository, meterRegistry);
    }

    @Test
    void check_passesCleanCustomerAndSavesHistory() {
        FraudCheckResponse response = fraudCheckService.check(new FraudCheckRequest(42, "yan@example.com"));

        assertThat(response).isEqualTo(new FraudCheckResponse(false, null));
        FraudCheckHistory history = savedHistory();
        assertThat(history.getCustomerId()).isEqualTo(42);
        assertThat(history.getIsFraudster()).isFalse();
        assertThat(history.getReason()).isNull();
        assertThat(history.getCreatedAt()).isNotNull();
        assertThat(meterRegistry.counter("fraud.checks", "result", "clean").count()).isEqualTo(1);
    }

    @Test
    void check_rejectsBlockedEmail_caseInsensitively() {
        when(blockedEmailRepository.existsById("bad@example.com")).thenReturn(true);

        FraudCheckResponse response = fraudCheckService.check(new FraudCheckRequest(42, " Bad@Example.COM "));

        assertThat(response).isEqualTo(new FraudCheckResponse(true, FraudReason.BLOCKED_EMAIL));
        assertThat(savedHistory().getReason()).isEqualTo(FraudReason.BLOCKED_EMAIL);
    }

    @Test
    void check_rejectsDisposableEmailDomain() {
        when(blockedEmailDomainRepository.existsById("mailinator.com")).thenReturn(true);

        FraudCheckResponse response = fraudCheckService.check(new FraudCheckRequest(42, "someone@mailinator.com"));

        assertThat(response).isEqualTo(new FraudCheckResponse(true, FraudReason.DISPOSABLE_EMAIL_DOMAIN));
        assertThat(savedHistory().getIsFraudster()).isTrue();
        assertThat(meterRegistry.counter("fraud.checks", "result", "disposable_email_domain").count()).isEqualTo(1);
    }

    private FraudCheckHistory savedHistory() {
        ArgumentCaptor<FraudCheckHistory> history = ArgumentCaptor.forClass(FraudCheckHistory.class);
        verify(fraudCheckHistoryRepository).save(history.capture());
        return history.getValue();
    }
}
