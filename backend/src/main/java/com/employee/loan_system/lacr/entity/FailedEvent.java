package com.employee.loan_system.lacr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persistent store for events that have exhausted all retry attempts.
 * Acts as an application-level Dead Letter Table (DLT).
 *
 * Interview answer:
 * "We don't want to silently discard events when all retries are exhausted.
 *  Instead we persist them to a 'failed_events' table with the full payload and failure reason.
 *  An admin endpoint allows inspection and potential manual replay.
 *  This is the application-level equivalent of a Kafka Dead Letter Topic — testable without Kafka infra."
 */
@Getter
@Setter
@Entity
@Table(name = "failed_events")
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The idempotency key of the original request. Used for deduplication
     * when replaying events.
     */
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    /**
     * The loan account number associated with the failed event.
     */
    @Column(name = "loan_account_number", length = 40)
    private String loanAccountNumber;

    /**
     * JSON payload of the event — full closure request or reconciliation trigger.
     * Stored as TEXT for maximum flexibility; schema can evolve without migration.
     */
    @Column(name = "event_payload", columnDefinition = "TEXT")
    private String eventPayload;

    /** Human-readable reason for the failure (last exception message). */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Number of attempts made before giving up. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** The processing stage at which the failure occurred (e.g. SETTLEMENT_CALCULATION). */
    @Column(name = "failed_stage", length = 60)
    private String failedStage;

    /** Actor who triggered the original request. */
    @Column(name = "created_by", length = 120)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "failed_at", nullable = false, updatable = false)
    private LocalDateTime failedAt;
}
