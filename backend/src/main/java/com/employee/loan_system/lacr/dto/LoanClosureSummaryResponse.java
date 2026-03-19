package com.employee.loan_system.lacr.dto;

import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record LoanClosureSummaryResponse(
        long totalRequests,
        long pendingRequests,
        long settlementCalculatedRequests,
        long reconciliationPendingRequests,
        long reconciledRequests,
        long approvedRequests,
        long closedRequests,
        long rejectedRequests,
        long onHoldRequests,
        long matchedReconciliations,
        long mismatchedReconciliations,
        long pendingReconciliations,
        BigDecimal totalSettlementAmount,
        BigDecimal totalOutstandingPrincipal,
        Map<LoanClosureStatus, Long> closureStatusCounts,
        Map<ReconciliationStatus, Long> reconciliationStatusCounts
) {
}
