package com.zim4ik.customer.repository;

import com.zim4ik.customer.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM outbox_event
            WHERE id IN (
                SELECT id FROM outbox_event
                WHERE published_at < :publishedBefore
                ORDER BY id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("publishedBefore") LocalDateTime publishedBefore, @Param("limit") int limit);
}
