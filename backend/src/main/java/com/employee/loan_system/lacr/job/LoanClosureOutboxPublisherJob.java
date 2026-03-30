package com.employee.loan_system.lacr.job;

import com.employee.loan_system.lacr.service.LoanClosureOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanClosureOutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureOutboxPublisherJob.class);

    private final LoanClosureOutboxService outboxService;

    public LoanClosureOutboxPublisherJob(LoanClosureOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${lacr.outbox.publish-delay-ms:30000}")
    public void publishPendingEvents() {
        int published = outboxService.publishPendingBatch();
        if (published > 0) {
            log.info("LoanClosureOutboxPublisherJob: published {} pending outbox event(s)", published);
        }
    }
}
