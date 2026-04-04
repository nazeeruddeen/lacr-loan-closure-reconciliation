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

@Getter
@Setter
@Entity
@Table(name = "loan_closure_consumed_events")
public class LoanClosureConsumedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180)
    private String idempotencyKey;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "closure_case_id")
    private Long closureCaseId;

    @Column(name = "loan_account_number", length = 40)
    private String loanAccountNumber;

    @Column(name = "event_type", length = 60)
    private String eventType;

    @Column(name = "aggregate_type", length = 60)
    private String aggregateType;

    @Column(name = "topic_name", nullable = false, length = 160)
    private String topicName;

    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;

    @Column(name = "record_offset", nullable = false)
    private Long recordOffset;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @CreationTimestamp
    @Column(name = "consumed_at", nullable = false, updatable = false)
    private LocalDateTime consumedAt;
}
