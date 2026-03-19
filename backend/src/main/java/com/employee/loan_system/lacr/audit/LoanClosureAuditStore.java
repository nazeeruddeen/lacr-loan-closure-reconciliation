package com.employee.loan_system.lacr.audit;

import com.employee.loan_system.lacr.entity.LoanClosureEventEntity;
import com.employee.loan_system.lacr.repository.LoanClosureEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LoanClosureAuditStore {

    private final CopyOnWriteArrayList<LoanClosureEvent> fallbackEvents = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private LoanClosureEventRepository eventRepository;

    public void append(LoanClosureEvent event) {
        fallbackEvents.add(event);
        if (eventRepository != null) {
            eventRepository.save(toEntity(event));
        }
    }

    public List<LoanClosureEvent> findAll() {
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
}
