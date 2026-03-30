package com.employee.loan_system.lacr.audit;

import com.employee.loan_system.lacr.document.LoanClosureAuditEventDocument;
import com.employee.loan_system.lacr.entity.LoanClosureEventEntity;
import com.employee.loan_system.lacr.repository.LoanClosureAuditEventMongoRepository;
import com.employee.loan_system.lacr.repository.LoanClosureEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
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

    public void append(LoanClosureEvent event) {
        fallbackEvents.add(event);
        persistToMongo(event);
        if (eventRepository != null) {
            eventRepository.save(toEntity(event));
        }
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
                entity.getCreatedAt());
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
                document.getCreatedAt());
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
}
