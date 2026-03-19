package com.employee.loan_system.lacr.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "loan_closure_cases")
public class LoanClosureCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "loan_account_number", nullable = false, length = 40)
    private String loanAccountNumber;

    @Column(name = "borrower_name", nullable = false, length = 150)
    private String borrowerName;

    @Column(name = "outstanding_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(name = "accrued_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal accruedInterest;

    @Column(name = "penalty_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "processing_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal processingFee;

    @Column(name = "settlement_adjustment", nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementAdjustment = BigDecimal.ZERO.setScale(2);

    @Column(name = "settlement_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "bank_confirmed_amount", precision = 15, scale = 2)
    private BigDecimal bankConfirmedAmount;

    @Column(name = "settlement_difference", precision = 15, scale = 2)
    private BigDecimal settlementDifference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "closure_status", nullable = false, length = 40)
    private LoanClosureStatus closureStatus = LoanClosureStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @Column(name = "closure_reason", nullable = false, length = 200)
    private String closureReason;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "closureCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LoanClosureStatusHistory> statusHistory = new ArrayList<>();

    public void addHistory(LoanClosureStatusHistory history) {
        history.setClosureCase(this);
        statusHistory.add(history);
    }
}

