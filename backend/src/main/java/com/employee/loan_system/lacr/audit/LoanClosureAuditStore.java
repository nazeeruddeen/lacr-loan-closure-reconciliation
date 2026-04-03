package com.employee.loan_system.lacr.audit;

import com.employee.loan_system.lacr.document.LoanClosureAuditEventDocument;
import com.employee.loan_system.lacr.entity.LoanClosureEventEntity;
import com.employee.loan_system.lacr.repository.LoanClosureAuditEventMongoRepository;
import com.employee.loan_system.lacr.repository.LoanClosureEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LoanClosureAuditStore {

    private static final Logger log = LoggerFactory.getLogger(LoanClosureAuditStore.class);
    private final CopyOnWriteArrayList<LoanClosureEvent> fallbackEvents = new CopyOnWriteArrayList<>();
    private final LoanClosureEventRepository eventRepository;
    private final LoanClosureAuditEventMongoRepository mongoRepository;

    public LoanClosureAuditStore() {
        this(null, null);
    }

    public LoanClosureAuditStore(
            LoanClosureEventRepository eventRepository,
            LoanClosureAuditEventMongoRepository mongoRepository) {
        this.eventRepository = eventRepository;
        this.mongoRepository = mongoRepository;
    }

    public LoanClosureEvent append(LoanClosureEvent event) {
        LoanClosureEvent normalized = normalize(event);
        String previousHash = resolvePreviousHash(normalized.closureId());
        LoanClosureEvent enriched = withHashes(normalized, previousHash);
        fallbackEvents.add(enriched);
        persistToMongo(enriched);
        if (eventRepository != null) {
            eventRepository.save(toEntity(enriched));
        }
        return enriched;
    }

    public List<LoanClosureEvent> findAll() {
        if (mongoRepository != null) {
            try {
                return mongoRepository.findAllByOrderByCreatedAtDesc().stream()
                        .map(this::toEvent)
                        .toList();
            } catch (Exception ex) {
                log.warn("Mongo audit read failed. Falling back to relational or in-memory audit store.", ex);
            }
        }
        if (eventRepository == null) {
            return List.copyOf(fallbackEvents);
        }
        return eventRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEvent)
                .toList();
    }

    public List<LoanClosureEvent> search(String requestId, String loanAccountNumber, LoanClosureEventType type) {
        return findAll().stream()
                .filter(event -> requestId == null || event.requestId().equalsIgnoreCase(requestId))
                .filter(event -> loanAccountNumber == null || event.loanAccountNumber().equalsIgnoreCase(loanAccountNumber))
                .filter(event -> type == null || event.eventType() == type)
                .toList();
    }

    public List<LoanClosureEvent> searchByText(String text) {
        if (text == null || text.isBlank()) {
            return findAll();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return findAll().stream()
                .filter(event -> matches(event, normalized))
                .toList();
    }

    private LoanClosureEventEntity toEntity(LoanClosureEvent event) {
        LoanClosureEventEntity entity = new LoanClosureEventEntity();
        entity.setRequestId(event.requestId());
        entity.setClosureCaseId(event.closureId());
        entity.setLoanAccountNumber(event.loanAccountNumber());
        entity.setEventType(event.eventType());
        entity.setFromStatus(event.fromStatus());
        entity.setToStatus(event.toStatus());
        entity.setReconciliationStatus(event.reconciliationStatus());
        entity.setActor(event.actor());
        entity.setDetails(event.details());
        entity.setPreviousHash(event.previousHash());
        entity.setRecordHash(event.recordHash());
        return entity;
    }

    private LoanClosureAuditEventDocument toDocument(LoanClosureEvent event) {
        LoanClosureAuditEventDocument document = new LoanClosureAuditEventDocument();
        document.setRequestId(event.requestId());
        document.setClosureId(event.closureId());
        document.setLoanAccountNumber(event.loanAccountNumber());
        document.setEventType(event.eventType());
        document.setFromStatus(event.fromStatus());
        document.setToStatus(event.toStatus());
        document.setReconciliationStatus(event.reconciliationStatus());
        document.setActor(event.actor());
        document.setDetails(event.details());
        document.setCreatedAt(event.createdAt());
        document.setPreviousHash(event.previousHash());
        document.setRecordHash(event.recordHash());
        return document;
    }

    private LoanClosureEvent toEvent(LoanClosureEventEntity entity) {
        return new LoanClosureEvent(
                entity.getRequestId(),
                entity.getClosureCaseId(),
                entity.getLoanAccountNumber(),
                entity.getEventType(),
                entity.getFromStatus(),
                entity.getToStatus(),
                entity.getReconciliationStatus(),
                entity.getActor(),
                entity.getDetails(),
                entity.getCreatedAt(),
                entity.getPreviousHash(),
                entity.getRecordHash());
    }

    private LoanClosureEvent toEvent(LoanClosureAuditEventDocument document) {
        return new LoanClosureEvent(
                document.getRequestId(),
                document.getClosureId(),
                document.getLoanAccountNumber(),
                document.getEventType(),
                document.getFromStatus(),
                document.getToStatus(),
                document.getReconciliationStatus(),
                document.getActor(),
                document.getDetails(),
                document.getCreatedAt(),
                document.getPreviousHash(),
                document.getRecordHash());
    }

    private boolean matches(LoanClosureEvent event, String normalized) {
        return safe(event.requestId()).contains(normalized)
                || safe(event.loanAccountNumber()).contains(normalized)
                || safe(event.details()).contains(normalized)
                || safe(event.actor()).contains(normalized)
                || safe(event.eventType() == null ? null : event.eventType().name()).contains(normalized);
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void persistToMongo(LoanClosureEvent event) {
        if (mongoRepository == null) {
            return;
        }

        try {
            mongoRepository.save(toDocument(event));
        } catch (Exception ex) {
            log.warn("Mongo audit write failed. Falling back to relational or in-memory audit store.", ex);
        }
    }

    private LoanClosureEvent normalize(LoanClosureEvent event) {
        if (event.createdAt() != null) {
            return event;
        }
        return new LoanClosureEvent(
                event.requestId(),
                event.closureId(),
                event.loanAccountNumber(),
                event.eventType(),
                event.fromStatus(),
                event.toStatus(),
                event.reconciliationStatus(),
                event.actor(),
                event.details(),
                LocalDateTime.now(),
                event.previousHash(),
                event.recordHash());
    }

    private String resolvePreviousHash(Long closureId) {
        if (closureId == null) {
            return null;
        }

        List<LoanClosureEvent> candidates = new ArrayList<>();
        candidates.addAll(fallbackEvents.stream()
                .filter(event -> closureId.equals(event.closureId()))
                .toList());

        if (mongoRepository != null) {
            mongoRepository.findTopByClosureIdOrderByCreatedAtDesc(closureId)
                    .ifPresent(document -> candidates.add(toEvent(document)));
        }

        if (eventRepository != null) {
            eventRepository.findTopByClosureCaseIdOrderByCreatedAtDesc(closureId)
                    .ifPresent(entity -> candidates.add(toEvent(entity)));
        }

        return candidates.stream()
                .max(Comparator.comparing(LoanClosureEvent::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(LoanClosureEvent::recordHash)
                .orElse(null);
    }

    private LoanClosureEvent withHashes(LoanClosureEvent event, String previousHash) {
        return new LoanClosureEvent(
                event.requestId(),
                event.closureId(),
                event.loanAccountNumber(),
                event.eventType(),
                event.fromStatus(),
                event.toStatus(),
                event.reconciliationStatus(),
                event.actor(),
                event.details(),
                event.createdAt(),
                previousHash,
                computeRecordHash(event, previousHash));
    }

    private String computeRecordHash(LoanClosureEvent event, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = String.join("|",
                    safe(previousHash),
                    safe(event.requestId()),
                    safe(event.closureId() == null ? null : event.closureId().toString()),
                    safe(event.loanAccountNumber()),
                    safe(event.eventType() == null ? null : event.eventType().name()),
                    safe(event.fromStatus() == null ? null : event.fromStatus().name()),
                    safe(event.toStatus() == null ? null : event.toStatus().name()),
                    safe(event.reconciliationStatus() == null ? null : event.reconciliationStatus().name()),
                    safe(event.actor()),
                    safe(event.details()),
                    safe(event.createdAt() == null ? null : event.createdAt().toString()));
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to compute audit hash", ex);
        }
    }
}
