package com.employee.loan_system.lacr.dto;

import java.time.LocalDateTime;

public record FailedEventResponse(
        Long id,
        String requestId,
        String loanAccountNumber,
        String failureReason,
        int attemptCount,
        String failedStage,
        String createdBy,
        LocalDateTime failedAt
) {
}
