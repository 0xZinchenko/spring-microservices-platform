package com.zim4ik.customer.repository;

import com.zim4ik.customer.entity.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxEventRepositoryTest {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void deletePublishedBefore_deletesOnlyOldPublishedEvents() {
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent oldPublished = save(now.minusDays(10), now.minusDays(8));
        OutboxEvent recentPublished = save(now.minusDays(2), now.minusDays(1));
        OutboxEvent oldUnpublished = save(now.minusDays(10), null);

        int deleted = outboxEventRepository.deletePublishedBefore(now.minusDays(7), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getId)
                .containsExactlyInAnyOrder(recentPublished.getId(), oldUnpublished.getId())
                .doesNotContain(oldPublished.getId());
    }

    @Test
    void deletePublishedBefore_respectsBatchLimit() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            save(now.minusDays(10), now.minusDays(8));
        }

        assertThat(outboxEventRepository.deletePublishedBefore(now.minusDays(7), 2)).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(3);
    }

    @Test
    void lockUnpublished_returnsOnlyUnpublishedEventsInOrder() {
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent first = save(now.minusMinutes(3), null);
        save(now.minusMinutes(2), now.minusMinutes(1));
        OutboxEvent second = save(now.minusMinutes(1), null);

        assertThat(outboxEventRepository.lockUnpublished(10))
                .extracting(OutboxEvent::getId)
                .containsExactly(first.getId(), second.getId());
    }

    private OutboxEvent save(LocalDateTime createdAt, LocalDateTime publishedAt) {
        return outboxEventRepository.save(OutboxEvent.builder()
                .exchange("internal.exchange")
                .routingKey("internal.notification.routing-key")
                .payloadType("com.example.Payload")
                .payload("{}")
                .createdAt(createdAt)
                .publishedAt(publishedAt)
                .build());
    }
}
