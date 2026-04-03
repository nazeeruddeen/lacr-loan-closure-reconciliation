package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.entity.FailedEvent;
import com.employee.loan_system.lacr.repository.FailedEventRepository;
import com.employee.loan_system.lacr.dto.FailedEventResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for recording and querying events that failed after exhausting all retry attempts.
 *
 * Interview story:
 * "When retry exhaustion occurs, instead of silently dropping the event, we persist it to the
 *  failed_events table with the full payload, reason, and attempt count. An admin endpoint allows
 *  the operations team to inspect, triage, and manually replay failed closures.
 *  We use PROPAGATION_REQUIRES_NEW so that recording the failure always succeeds even if the
 *  parent transaction is rolling back — we never want to lose a failure record."
 */
@Service
public class FailedEventService {

    private static final Logger log = LoggerFactory.getLogger(FailedEventService.class);

    private final FailedEventRepository failedEventRepository;
    private final Counter failedEventsTotal;

    public FailedEventService(FailedEventRepository failedEventRepository) {
        this(failedEventRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FailedEventService(FailedEventRepository failedEventRepository, MeterRegistry meterRegistry) {
        this.failedEventRepository = failedEventRepository;
        this.failedEventsTotal = meterRegistry == null ? null : meterRegistry.counter("lacr.failed_events_total");
    }

    /**
     * Records a failed event in a new transaction so it commits even if the caller rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailedEvent recordFailure(
            String requestId,
            String loanAccountNumber,
            String eventPayload,
            String failureReason,
            int attemptCount,
            String failedStage,
            String createdBy) {

        FailedEvent event = new FailedEvent();
        event.setRequestId(requestId);
        event.setLoanAccountNumber(loanAccountNumber);
        event.setEventPayload(eventPayload);
        event.setFailureReason(failureReason != null && failureReason.length() > 500
                ? failureReason.substring(0, 497) + "..."
                : failureReason);
        event.setAttemptCount(attemptCount);
        event.setFailedStage(failedStage);
        event.setCreatedBy(createdBy);

        FailedEvent saved = failedEventRepository.save(event);
        if (failedEventsTotal != null) {
            failedEventsTotal.increment();
        }
        log.error(
            "FailedEventService: recorded failed event id={} requestId={} stage={} reason={}",
            saved.getId(), requestId, failedStage, failureReason
        );
        return saved;
    }

    /**
     * Returns all failed events for admin review and potential manual replay.
     */
    @Transactional(readOnly = true)
    public List<FailedEvent> listAll() {
        return failedEventRepository.findAll();
    }

    /**
     * Returns failed events for a specific loan account — useful for targeted investigation.
     */
    @Transactional(readOnly = true)
    public List<FailedEvent> listByLoanAccount(String loanAccountNumber) {
        return failedEventRepository.findByLoanAccountNumberOrderByFailedAtDesc(loanAccountNumber);
    }

    /**
     * Returns failed events for a specific request ID â€” useful for incident correlation.
     */
    @Transactional(readOnly = true)
    public List<FailedEvent> listByRequestId(String requestId) {
        return failedEventRepository.findByRequestIdOrderByFailedAtDesc(requestId);
    }
}
