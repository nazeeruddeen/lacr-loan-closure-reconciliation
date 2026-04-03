package com.employee.loan_system.lacr.dto;

import java.time.LocalDateTime;

public record OutboxHealthResponse(
        long pendingCount,
        long processingCount,
        long publishedCount,
        long failedCount,
        long staleProcessingCount,
        LocalDateTime oldestPendingAt,
        LocalDateTime oldestProcessingAt,
        LocalDateTime newestPublishedAt,
        String reclaimAfter
) {
}
