package com.zim4ik.fraud.service;

import com.zim4ik.fraud.entity.FraudCheckHistory;
import com.zim4ik.fraud.repository.FraudCheckHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FraudCheckServiceTest {

    @Mock
    private FraudCheckHistoryRepository fraudCheckHistoryRepository;

    @InjectMocks
    private FraudCheckService fraudCheckService;

    @Test
    void isFraudulentCustomer_returnsFalseAndSavesHistory() {
        boolean result = fraudCheckService.isFraudulentCustomer(42);

        assertThat(result).isFalse();

        ArgumentCaptor<FraudCheckHistory> history = ArgumentCaptor.forClass(FraudCheckHistory.class);
        verify(fraudCheckHistoryRepository).save(history.capture());
        assertThat(history.getValue().getCustomerId()).isEqualTo(42);
        assertThat(history.getValue().getIsFraudster()).isFalse();
        assertThat(history.getValue().getCreatedAt()).isNotNull();
    }
}
