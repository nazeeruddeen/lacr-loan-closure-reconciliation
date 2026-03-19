package com.employee.loan_system.lacr.entity;

import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "loan_closure_events")
public class LoanClosureEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "closure_case_id", nullable = false)
    private Long closureCaseId;

    @Column(name = "loan_account_number", nullable = false, length = 40)
    private String loanAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private LoanClosureEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private LoanClosureStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 40)
    private LoanClosureStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 30)
    private ReconciliationStatus reconciliationStatus;

    @Column(name = "actor", length = 120)
    private String actor;

    @Column(name = "details", length = 1000)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
