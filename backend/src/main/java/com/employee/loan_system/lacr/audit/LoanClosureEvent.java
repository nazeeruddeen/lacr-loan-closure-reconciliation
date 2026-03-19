package com.employee.loan_system.lacr.audit;

import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;

import java.time.LocalDateTime;

public record LoanClosureEvent(
        String requestId,
        Long closureId,
        String loanAccountNumber,
        LoanClosureEventType eventType,
        LoanClosureStatus fromStatus,
        LoanClosureStatus toStatus,
        ReconciliationStatus reconciliationStatus,
        String actor,
        String details,
        LocalDateTime createdAt
) {
}
