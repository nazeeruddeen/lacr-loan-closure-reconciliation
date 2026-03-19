package com.employee.loan_system.lacr.dto;

import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record LoanClosureResponse(
        Long id,
        String requestId,
        String loanAccountNumber,
        String borrowerName,
        String closureReason,
        BigDecimal outstandingPrincipal,
        BigDecimal accruedInterest,
        BigDecimal penaltyAmount,
        BigDecimal processingFee,
        BigDecimal settlementAdjustment,
        BigDecimal settlementAmount,
        BigDecimal bankConfirmedAmount,
        BigDecimal settlementDifference,
        LoanClosureStatus closureStatus,
        ReconciliationStatus reconciliationStatus,
        String remarks,
        String createdBy,
        LocalDateTime requestedAt,
        LocalDateTime calculatedAt,
        LocalDateTime reconciledAt,
        LocalDateTime approvedAt,
        LocalDateTime closedAt,
        List<LoanClosureHistoryResponse> statusHistory
) {
}
