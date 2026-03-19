package com.employee.loan_system.lacr.dto;

import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record LoanClosureHistoryResponse(
        LoanClosureStatus fromStatus,
        LoanClosureStatus toStatus,
        String actionName,
        String remarks,
        String changedBy,
        LocalDateTime changedAt
) {
}
