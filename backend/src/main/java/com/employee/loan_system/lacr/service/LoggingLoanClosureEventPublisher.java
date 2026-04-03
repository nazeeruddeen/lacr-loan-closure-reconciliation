package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lacr.outbox.publisher.mode", havingValue = "log", matchIfMissing = true)
public class LoggingLoanClosureEventPublisher implements LoanClosureEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingLoanClosureEventPublisher.class);

    @Override
    public void publish(LoanClosureOutboxEvent event) {
        log.info(
                "LoggingLoanClosureEventPublisher: published outbox event id={} requestId={} type={} aggregate={} key={}",
                event.getId(),
                event.getRequestId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getRequestId());
    }
}
