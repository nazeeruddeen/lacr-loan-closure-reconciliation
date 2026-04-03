package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.dto.OutboxHealthResponse;
import com.employee.loan_system.lacr.dto.OutboxRecoveryResponse;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxStatus;
import com.employee.loan_system.lacr.repository.LoanClosureOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

@Service
public class LoanClosureOutboxService {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureOutboxService.class);
    private static final Duration DEFAULT_RECLAIM_AFTER = Duration.ofMinutes(15);

    private final LoanClosureOutboxEventRepository outboxRepository;
    private final FailedEventService failedEventService;
    private final LoanClosureEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration reclaimAfter;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter queuedCounter;
    private final Counter claimedCounter;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter recoveredCounter;
    private final Timer batchTimer;

    public LoanClosureOutboxService(
            LoanClosureOutboxEventRepository outboxRepository,
            FailedEventService failedEventService,
            LoanClosureEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            @Value("${lacr.outbox.max-attempts:3}") int maxAttempts) {
        this(outboxRepository, failedEventService, eventPublisher, objectMapper, maxAttempts, DEFAULT_RECLAIM_AFTER, null, null);
    }

    @Autowired
    public LoanClosureOutboxService(
            LoanClosureOutboxEventRepository outboxRepository,
            FailedEventService failedEventService,
            LoanClosureEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            @Value("${lacr.outbox.max-attempts:3}") int maxAttempts,
            @Value("${lacr.outbox.reclaim-after:PT15M}") Duration reclaimAfter,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.failedEventService = failedEventService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.reclaimAfter = reclaimAfter == null ? DEFAULT_RECLAIM_AFTER : reclaimAfter;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
        this.queuedCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.outbox_events_queued_total");
        this.claimedCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.outbox_events_claimed_total");
        this.publishedCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.outbox_events_published_total");
        this.failedCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.outbox_events_failed_total");
        this.recoveredCounter = meterRegistry == null ? null : meterRegistry.counter("lacr.outbox_events_recovered_total");
        this.batchTimer = meterRegistry == null ? null : meterRegistry.timer("lacr.outbox_publish_duration_seconds");
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
        increment(queuedCounter);
    }

    public int publishPendingBatch() {
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        List<LoanClosureOutboxEvent> pending = claimBatch();
        int published = 0;
        for (LoanClosureOutboxEvent event : pending) {
            if (publish(event)) {
                published++;
            }
        }
        if (sample != null && batchTimer != null) {
            sample.stop(batchTimer);
        }
        return published;
    }

    @Transactional
    public int recoverStaleProcessingEvents() {
        LocalDateTime reclaimBefore = LocalDateTime.now().minus(reclaimAfter);
        List<LoanClosureOutboxEvent> stale = outboxRepository.findStaleProcessingBatch(reclaimBefore, 50);
        if (stale.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        stale.forEach(event -> {
            event.setPublishStatus(LoanClosureOutboxStatus.PENDING);
            event.setProcessingStartedAt(null);
            event.setErrorMessage("Recovered stale processing after " + reclaimAfter);
            event.setLastAttemptAt(now);
        });
        outboxRepository.saveAll(stale);
        increment(recoveredCounter, stale.size());
        log.warn("LoanClosureOutboxService: recovered {} stale processing outbox event(s)", stale.size());
        return stale.size();
    }

    @Transactional
    public OutboxRecoveryResponse recoverAndPublishStaleProcessingEvents() {
        int recovered = recoverStaleProcessingEvents();
        int republished = recovered > 0 ? publishPendingBatch() : 0;
        return new OutboxRecoveryResponse(recovered, republished);
    }

    @Transactional(readOnly = true)
    public OutboxHealthResponse outboxHealth() {
        LocalDateTime reclaimBefore = LocalDateTime.now().minus(reclaimAfter);
        long pending = outboxRepository.countByPublishStatus(LoanClosureOutboxStatus.PENDING);
        long processing = outboxRepository.countByPublishStatus(LoanClosureOutboxStatus.PROCESSING);
        long published = outboxRepository.countByPublishStatus(LoanClosureOutboxStatus.PUBLISHED);
        long failed = outboxRepository.countByPublishStatus(LoanClosureOutboxStatus.FAILED);
        long staleProcessing = outboxRepository.countStaleProcessing(reclaimBefore);
        return new OutboxHealthResponse(
                pending,
                processing,
                published,
                failed,
                staleProcessing,
                oldestCreatedAt(LoanClosureOutboxStatus.PENDING),
                oldestProcessingStartedAt(),
                newestPublishedAt(),
                reclaimAfter.toString());
    }

    private List<LoanClosureOutboxEvent> claimBatch() {
        LocalDateTime reclaimBefore = LocalDateTime.now().minus(reclaimAfter);
        if (transactionTemplate == null) {
            return claimBatchWithoutTransaction(reclaimBefore);
        }
        return transactionTemplate.execute(status -> claimAndMarkProcessing(reclaimBefore));
    }

    private List<LoanClosureOutboxEvent> claimBatchWithoutTransaction(LocalDateTime reclaimBefore) {
        return claimAndMarkProcessing(reclaimBefore);
    }

    private List<LoanClosureOutboxEvent> claimAndMarkProcessing(LocalDateTime reclaimBefore) {
        List<LoanClosureOutboxEvent> claimable = outboxRepository.findClaimableBatch(reclaimBefore, 20);
        if (claimable.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        claimable.forEach(event -> {
            event.setPublishStatus(LoanClosureOutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setErrorMessage(null);
        });
        outboxRepository.saveAll(claimable);
        increment(claimedCounter, claimable.size());
        return new ArrayList<>(claimable);
    }

    private boolean publish(LoanClosureOutboxEvent event) {
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastAttemptAt(LocalDateTime.now());
        try {
            eventPublisher.publish(event);
            event.setPublishStatus(LoanClosureOutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setProcessingStartedAt(null);
            event.setErrorMessage(null);
            outboxRepository.save(event);
            increment(publishedCounter);
            return true;
        } catch (Exception ex) {
            event.setErrorMessage(truncate(ex.getMessage()));
            if (event.getAttemptCount() >= maxAttempts) {
                event.setPublishStatus(LoanClosureOutboxStatus.FAILED);
                event.setProcessingStartedAt(null);
                failedEventService.recordFailure(
                        event.getRequestId(),
                        event.getLoanAccountNumber(),
                        event.getPayloadJson(),
                        ex.getMessage(),
                        event.getAttemptCount(),
                        "OUTBOX_PUBLISH",
                        "SYSTEM");
                increment(failedCounter);
            } else {
                event.setPublishStatus(LoanClosureOutboxStatus.PENDING);
                event.setProcessingStartedAt(null);
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

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private void increment(Counter counter, int amount) {
        if (counter != null && amount > 0) {
            counter.increment(amount);
        }
    }

    private LocalDateTime oldestCreatedAt(LoanClosureOutboxStatus status) {
        return outboxRepository.findTopByPublishStatusOrderByCreatedAtAsc(status)
                .map(LoanClosureOutboxEvent::getCreatedAt)
                .orElse(null);
    }

    private LocalDateTime oldestProcessingStartedAt() {
        return outboxRepository.findTopByPublishStatusAndProcessingStartedAtIsNotNullOrderByProcessingStartedAtAsc(LoanClosureOutboxStatus.PROCESSING)
                .map(LoanClosureOutboxEvent::getProcessingStartedAt)
                .orElse(null);
    }

    private LocalDateTime newestPublishedAt() {
        return outboxRepository.findTopByPublishStatusOrderByPublishedAtDesc(LoanClosureOutboxStatus.PUBLISHED)
                .map(LoanClosureOutboxEvent::getPublishedAt)
                .orElse(null);
    }
}
