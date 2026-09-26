package com.zim4ik.customer.service;

import com.zim4ik.customer.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxCleanupService {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${outbox.cleanup.retention:7d}")
    private Duration retention;

    @Value("${outbox.cleanup.batch-size:1000}")
    private int batchSize;

    @Scheduled(cron = "${outbox.cleanup.cron:0 0 * * * *}")
    public int deletePublishedEvents() {
        LocalDateTime publishedBefore = LocalDateTime.now().minus(retention);
        int total = 0;
        int deleted;
        do {
            deleted = outboxEventRepository.deletePublishedBefore(publishedBefore, batchSize);
            total += deleted;
        } while (deleted == batchSize);

        if (total > 0) {
            log.info("🧹 Deleted {} outbox events published before {}", total, publishedBefore);
        }
        return total;
    }
}
