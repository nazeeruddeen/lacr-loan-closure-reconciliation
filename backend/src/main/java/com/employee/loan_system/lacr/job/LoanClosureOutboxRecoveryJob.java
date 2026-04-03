package com.employee.loan_system.lacr.job;

import com.employee.loan_system.lacr.service.LoanClosureOutboxService;
import com.employee.loan_system.lacr.dto.OutboxRecoveryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanClosureOutboxRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureOutboxRecoveryJob.class);

    private final LoanClosureOutboxService outboxService;

    public LoanClosureOutboxRecoveryJob(LoanClosureOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${lacr.outbox.recovery-delay-ms:60000}")
    public void recoverStaleOutboxEvents() {
        OutboxRecoveryResponse response = outboxService.recoverAndPublishStaleProcessingEvents();
        if (response.recoveredCount() > 0) {
            log.warn("LoanClosureOutboxRecoveryJob: reclaimed {} stale outbox event(s)", response.recoveredCount());
        }
        if (response.republishedCount() > 0) {
            log.info("LoanClosureOutboxRecoveryJob: republished {} recovered outbox event(s)", response.republishedCount());
        }
    }
}
