package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoanClosureOutboxEventRepository extends JpaRepository<LoanClosureOutboxEvent, Long> {
    List<LoanClosureOutboxEvent> findTop20ByPublishStatusOrderByCreatedAtAsc(LoanClosureOutboxStatus publishStatus);
    Optional<LoanClosureOutboxEvent> findTopByPublishStatusOrderByCreatedAtAsc(LoanClosureOutboxStatus publishStatus);
    Optional<LoanClosureOutboxEvent> findTopByPublishStatusAndProcessingStartedAtIsNotNullOrderByProcessingStartedAtAsc(LoanClosureOutboxStatus publishStatus);
    Optional<LoanClosureOutboxEvent> findTopByPublishStatusOrderByPublishedAtDesc(LoanClosureOutboxStatus publishStatus);
    long countByPublishStatus(LoanClosureOutboxStatus publishStatus);

    @Query(value = """
            select *
            from loan_closure_outbox_events
            where publish_status = 'PENDING'
               or (publish_status = 'PROCESSING'
                   and processing_started_at is not null
                   and processing_started_at < :reclaimBefore)
            order by created_at asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<LoanClosureOutboxEvent> findClaimableBatch(
            @Param("reclaimBefore") LocalDateTime reclaimBefore,
            @Param("batchSize") int batchSize);

    @Query(value = """
            select *
            from loan_closure_outbox_events
            where publish_status = 'PROCESSING'
              and processing_started_at is not null
              and processing_started_at < :reclaimBefore
            order by processing_started_at asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<LoanClosureOutboxEvent> findStaleProcessingBatch(
            @Param("reclaimBefore") LocalDateTime reclaimBefore,
            @Param("batchSize") int batchSize);

    @Query(value = """
            select count(*)
            from loan_closure_outbox_events
            where publish_status = 'PROCESSING'
              and processing_started_at is not null
              and processing_started_at < :reclaimBefore
            """, nativeQuery = true)
    long countStaleProcessing(@Param("reclaimBefore") LocalDateTime reclaimBefore);
}
