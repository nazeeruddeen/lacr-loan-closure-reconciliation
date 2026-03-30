package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxStatus;
import com.employee.loan_system.lacr.repository.LoanClosureOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoanClosureOutboxService {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureOutboxService.class);

    private final LoanClosureOutboxEventRepository outboxRepository;
    private final FailedEventService failedEventService;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public LoanClosureOutboxService(
            LoanClosureOutboxEventRepository outboxRepository,
            FailedEventService failedEventService,
            ObjectMapper objectMapper,
            @Value("${lacr.outbox.max-attempts:3}") int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.failedEventService = failedEventService;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void appendWorkflowEvent(LoanClosureEvent event) {
        LoanClosureOutboxEvent outboxEvent = new LoanClosureOutboxEvent();
        outboxEvent.setRequestId(event.requestId());
        outboxEvent.setClosureCaseId(event.closureId());
        outboxEvent.setLoanAccountNumber(event.loanAccountNumber());
        outboxEvent.setAggregateType("LoanClosureCase");
        outboxEvent.setEventType(event.eventType() == null ? "UNKNOWN" : event.eventType().name());
        outboxEvent.setPayloadJson(serialize(event));
        outboxEvent.setPublishStatus(LoanClosureOutboxStatus.PENDING);
        outboxEvent.setAttemptCount(0);
        outboxRepository.save(outboxEvent);
    }

    @Transactional
    public int publishPendingBatch() {
        List<LoanClosureOutboxEvent> pending = outboxRepository.findTop20ByPublishStatusOrderByCreatedAtAsc(LoanClosureOutboxStatus.PENDING);
        int published = 0;
        for (LoanClosureOutboxEvent event : pending) {
            if (publish(event)) {
                published++;
            }
        }
        return published;
    }

    private boolean publish(LoanClosureOutboxEvent event) {
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastAttemptAt(LocalDateTime.now());
        try {
            log.info(
                    "LoanClosureOutboxService: published outbox event id={} requestId={} type={} aggregate={}",
                    event.getId(),
                    event.getRequestId(),
                    event.getEventType(),
                    event.getAggregateType());
            event.setPublishStatus(LoanClosureOutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setErrorMessage(null);
            outboxRepository.save(event);
            return true;
        } catch (Exception ex) {
            event.setErrorMessage(truncate(ex.getMessage()));
            if (event.getAttemptCount() >= maxAttempts) {
                event.setPublishStatus(LoanClosureOutboxStatus.FAILED);
                failedEventService.recordFailure(
                        event.getRequestId(),
                        event.getLoanAccountNumber(),
                        event.getPayloadJson(),
                        ex.getMessage(),
                        event.getAttemptCount(),
                        "OUTBOX_PUBLISH",
                        "SYSTEM");
            }
            outboxRepository.save(event);
            log.error("LoanClosureOutboxService: failed to publish outbox event id={}", event.getId(), ex);
            return false;
        }
    }

    private String serialize(LoanClosureEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize outbox payload", ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 497) + "..." : value;
    }
}
