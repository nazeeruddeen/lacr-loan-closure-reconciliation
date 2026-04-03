package com.employee.loan_system.lacr.dto;

public record OutboxRecoveryResponse(
        int recoveredCount,
        int republishedCount
) {
}
