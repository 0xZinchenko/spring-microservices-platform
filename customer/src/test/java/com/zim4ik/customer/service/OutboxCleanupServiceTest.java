package com.zim4ik.customer.service;

import com.zim4ik.customer.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxCleanupService outboxCleanupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxCleanupService, "retention", Duration.ofDays(7));
        ReflectionTestUtils.setField(outboxCleanupService, "batchSize", 2);
    }

    @Test
    void deletePublishedEvents_deletesInBatchesUntilBatchIsNotFull() {
        when(outboxEventRepository.deletePublishedBefore(any(LocalDateTime.class), eq(2)))
                .thenReturn(2, 2, 1);

        int deleted = outboxCleanupService.deletePublishedEvents();

        assertThat(deleted).isEqualTo(5);
        verify(outboxEventRepository, times(3)).deletePublishedBefore(any(LocalDateTime.class), eq(2));
    }

    @Test
    void deletePublishedEvents_usesRetentionForCutoff() {
        LocalDateTime before = LocalDateTime.now().minusDays(7);

        outboxCleanupService.deletePublishedEvents();

        LocalDateTime after = LocalDateTime.now().minusDays(7);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventRepository).deletePublishedBefore(cutoff.capture(), eq(2));
        assertThat(cutoff.getValue()).isBetween(before, after);
    }
}
