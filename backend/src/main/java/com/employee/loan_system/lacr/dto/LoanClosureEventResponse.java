package com.employee.loan_system.lacr.dto;

import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record LoanClosureEventResponse(
        String requestId,
        Long closureId,
        String loanAccountNumber,
        LoanClosureEventType eventType,
        LoanClosureStatus fromStatus,
        LoanClosureStatus toStatus,
        ReconciliationStatus reconciliationStatus,
        String actor,
        String details,
        LocalDateTime createdAt,
        String previousHash,
        String recordHash
) {
}
