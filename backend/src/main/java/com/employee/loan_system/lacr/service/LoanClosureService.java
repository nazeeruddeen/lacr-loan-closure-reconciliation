package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureAuditStore;
import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.cache.LoanClosureIdempotencyStore;
import com.employee.loan_system.lacr.dto.AdvanceClosureStatusRequest;
import com.employee.loan_system.lacr.dto.CalculateSettlementRequest;
import com.employee.loan_system.lacr.dto.CreateLoanClosureRequest;
import com.employee.loan_system.lacr.dto.LoanClosureEventResponse;
import com.employee.loan_system.lacr.dto.LoanClosureHistoryResponse;
import com.employee.loan_system.lacr.dto.LoanClosureResponse;
import com.employee.loan_system.lacr.dto.LoanClosureSummaryResponse;
import com.employee.loan_system.lacr.dto.ReconcileClosureRequest;
import com.employee.loan_system.lacr.entity.LoanClosureCase;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.LoanClosureStatusHistory;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.exception.BusinessRuleException;
import com.employee.loan_system.lacr.exception.ResourceNotFoundException;
import com.employee.loan_system.lacr.repository.LoanClosureCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LoanClosureService {

    private final LoanClosureCaseRepository closureCaseRepository;
    private final LoanClosureAuditStore auditStore;
    private final LoanClosureIdempotencyStore idempotencyStore;
    private final Map<LoanClosureStatus, List<LoanClosureStatus>> allowedTransitions = new EnumMap<>(LoanClosureStatus.class);

    public LoanClosureService(
            LoanClosureCaseRepository closureCaseRepository,
            LoanClosureAuditStore auditStore,
            LoanClosureIdempotencyStore idempotencyStore) {
        this.closureCaseRepository = closureCaseRepository;
        this.auditStore = auditStore;
        this.idempotencyStore = idempotencyStore;
        allowedTransitions.put(LoanClosureStatus.REQUESTED, List.of(LoanClosureStatus.SETTLEMENT_CALCULATED, LoanClosureStatus.REJECTED));
        allowedTransitions.put(LoanClosureStatus.SETTLEMENT_CALCULATED, List.of(LoanClosureStatus.RECONCILIATION_PENDING, LoanClosureStatus.REJECTED));
        allowedTransitions.put(LoanClosureStatus.RECONCILIATION_PENDING, List.of(LoanClosureStatus.APPROVED, LoanClosureStatus.REJECTED));
        allowedTransitions.put(LoanClosureStatus.RECONCILED, List.of(LoanClosureStatus.APPROVED, LoanClosureStatus.REJECTED));
        allowedTransitions.put(LoanClosureStatus.ON_HOLD, List.of(LoanClosureStatus.SETTLEMENT_CALCULATED, LoanClosureStatus.REJECTED));
        allowedTransitions.put(LoanClosureStatus.APPROVED, List.of(LoanClosureStatus.CLOSED));
    }

    @Transactional
    public LoanClosureResponse createClosureRequest(CreateLoanClosureRequest request) {
        String requestId = normalize(request.getRequestId());
        String idempotencyKey = "CREATE:" + requestId;
        Optional<Object> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent() && cached.get() instanceof LoanClosureResponse response) {
            return response;
        }

        LoanClosureCase existing = closureCaseRepository.findByRequestId(requestId).orElse(null);
        if (existing != null) {
            LoanClosureResponse response = toResponse(existing);
            idempotencyStore.put(idempotencyKey, response);
            return response;
        }

        LoanClosureCase closureCase = new LoanClosureCase();
        closureCase.setRequestId(requestId);
        closureCase.setLoanAccountNumber(request.getLoanAccountNumber().trim());
        closureCase.setBorrowerName(request.getBorrowerName().trim());
        closureCase.setClosureReason(request.getClosureReason().trim());
        closureCase.setOutstandingPrincipal(scale(request.getOutstandingPrincipal()));
        closureCase.setAccruedInterest(scale(request.getAccruedInterest()));
        closureCase.setPenaltyAmount(scale(request.getPenaltyAmount()));
        closureCase.setProcessingFee(scale(request.getProcessingFee()));
        closureCase.setSettlementAdjustment(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        closureCase.setSettlementAmount(calculateSettlement(
                closureCase.getOutstandingPrincipal(),
                closureCase.getAccruedInterest(),
                closureCase.getPenaltyAmount(),
                closureCase.getProcessingFee(),
                BigDecimal.ZERO));
        closureCase.setRemarks(trimToNull(request.getRemarks()));
        closureCase.setClosureStatus(LoanClosureStatus.REQUESTED);
        closureCase.setReconciliationStatus(ReconciliationStatus.PENDING);
        closureCase.setCreatedBy(currentActor());
        closureCase.setRequestedAt(LocalDateTime.now());
        closureCase.addHistory(history(null, LoanClosureStatus.REQUESTED, "CREATE", request.getRemarks()));
        LoanClosureCase saved = closureCaseRepository.save(closureCase);
        recordEvent(saved, LoanClosureEventType.CREATED, null, LoanClosureStatus.REQUESTED, request.getRemarks());
        LoanClosureResponse response = toResponse(saved);
        idempotencyStore.put(idempotencyKey, response);
        return response;
    }

    @Transactional(readOnly = true)
    public LoanClosureResponse getClosureRequest(Long id) {
        return toResponse(findClosureCase(id));
    }

    @Transactional
    public LoanClosureResponse calculateSettlement(Long id, CalculateSettlementRequest request) {
        LoanClosureCase closureCase = findClosureCase(id);
        String idempotencyKey = "SETTLEMENT:" + id + ":" + scale(request.getAdjustmentAmount());
        Optional<Object> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent() && cached.get() instanceof LoanClosureResponse response) {
            return response;
        }
        if (closureCase.getClosureStatus() != LoanClosureStatus.REQUESTED
                && closureCase.getClosureStatus() != LoanClosureStatus.ON_HOLD) {
            throw new BusinessRuleException("Settlement can only be calculated for requested or on-hold cases");
        }

        BigDecimal adjustment = scale(request.getAdjustmentAmount());
        closureCase.setSettlementAdjustment(adjustment);
        closureCase.setSettlementAmount(calculateSettlement(
                closureCase.getOutstandingPrincipal(),
                closureCase.getAccruedInterest(),
                closureCase.getPenaltyAmount(),
                closureCase.getProcessingFee(),
                adjustment));
        closureCase.setCalculatedAt(LocalDateTime.now());
        closureCase.setRemarks(appendRemarks(closureCase.getRemarks(), request.getRemarks()));
        transition(closureCase, LoanClosureStatus.SETTLEMENT_CALCULATED, "SETTLEMENT", request.getRemarks());
        LoanClosureCase saved = closureCaseRepository.save(closureCase);
        recordEvent(saved, LoanClosureEventType.SETTLEMENT_CALCULATED, LoanClosureStatus.REQUESTED, LoanClosureStatus.SETTLEMENT_CALCULATED, request.getRemarks());
        LoanClosureResponse response = toResponse(saved);
        idempotencyStore.put(idempotencyKey, response);
        return response;
    }

    @Transactional
    public LoanClosureResponse moveToReconciliation(Long id, AdvanceClosureStatusRequest request) {
        LoanClosureCase closureCase = findClosureCase(id);
        String idempotencyKey = "RECONCILE_START:" + id + ":" + request.getTargetStatus();
        Optional<Object> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent() && cached.get() instanceof LoanClosureResponse response) {
            return response;
        }
        if (closureCase.getClosureStatus() != LoanClosureStatus.SETTLEMENT_CALCULATED) {
            throw new BusinessRuleException("Reconciliation can only start after settlement calculation");
        }
        if (request.getTargetStatus() != LoanClosureStatus.RECONCILIATION_PENDING) {
            throw new BusinessRuleException("Target status must be RECONCILIATION_PENDING");
        }
        transition(closureCase, LoanClosureStatus.RECONCILIATION_PENDING, "MOVE_TO_RECONCILIATION", request.getRemarks());
        closureCase.setRemarks(appendRemarks(closureCase.getRemarks(), request.getRemarks()));
        LoanClosureCase saved = closureCaseRepository.save(closureCase);
        recordEvent(saved, LoanClosureEventType.RECONCILIATION_STARTED, LoanClosureStatus.SETTLEMENT_CALCULATED, LoanClosureStatus.RECONCILIATION_PENDING, request.getRemarks());
        LoanClosureResponse response = toResponse(saved);
        idempotencyStore.put(idempotencyKey, response);
        return response;
    }

    @Transactional
    public LoanClosureResponse reconcile(Long id, ReconcileClosureRequest request) {
        LoanClosureCase closureCase = findClosureCase(id);
        String idempotencyKey = "RECONCILE:" + id + ":" + scale(request.getBankConfirmedAmount());
        Optional<Object> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent() && cached.get() instanceof LoanClosureResponse response) {
            return response;
        }
        if (closureCase.getClosureStatus() != LoanClosureStatus.RECONCILIATION_PENDING) {
            throw new BusinessRuleException("Reconciliation is only allowed in reconciliation pending state");
        }

        BigDecimal bankConfirmedAmount = scale(request.getBankConfirmedAmount());
        BigDecimal difference = bankConfirmedAmount.subtract(closureCase.getSettlementAmount()).setScale(2, RoundingMode.HALF_UP);
        closureCase.setBankConfirmedAmount(bankConfirmedAmount);
        closureCase.setSettlementDifference(difference);
        closureCase.setReconciledAt(LocalDateTime.now());
        closureCase.setRemarks(appendRemarks(closureCase.getRemarks(), request.getRemarks()));
        closureCase.setReconciliationStatus(difference.compareTo(BigDecimal.ZERO) == 0
                ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.MISMATCHED);

        if (closureCase.getReconciliationStatus() == ReconciliationStatus.MATCHED) {
            transition(closureCase, LoanClosureStatus.RECONCILED, "RECONCILE_MATCHED", request.getRemarks());
        } else {
            transition(closureCase, LoanClosureStatus.ON_HOLD, "RECONCILE_MISMATCHED", request.getRemarks());
        }
        LoanClosureCase saved = closureCaseRepository.save(closureCase);
        recordEvent(saved,
                closureCase.getReconciliationStatus() == ReconciliationStatus.MATCHED ? LoanClosureEventType.RECONCILED_MATCHED : LoanClosureEventType.RECONCILED_MISMATCHED,
                LoanClosureStatus.RECONCILIATION_PENDING,
                saved.getClosureStatus(),
                request.getRemarks());
        LoanClosureResponse response = toResponse(saved);
        idempotencyStore.put(idempotencyKey, response);
        return response;
    }

    @Transactional
    public LoanClosureResponse advanceStatus(Long id, AdvanceClosureStatusRequest request) {
        LoanClosureCase closureCase = findClosureCase(id);
        String idempotencyKey = "STATUS:" + id + ":" + request.getTargetStatus();
        Optional<Object> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent() && cached.get() instanceof LoanClosureResponse response) {
            return response;
        }
        LoanClosureStatus current = closureCase.getClosureStatus();
        LoanClosureStatus target = request.getTargetStatus();

        if (current == target) {
            return toResponse(closureCase);
        }
        if (!allowedTransitions.getOrDefault(current, List.of()).contains(target)) {
            throw new BusinessRuleException("Invalid transition from " + current + " to " + target);
        }
        if (target == LoanClosureStatus.APPROVED && closureCase.getReconciliationStatus() != ReconciliationStatus.MATCHED) {
            throw new BusinessRuleException("Only matched closures can be approved");
        }
        if (target == LoanClosureStatus.CLOSED && current != LoanClosureStatus.APPROVED) {
            throw new BusinessRuleException("Only approved closures can be closed");
        }

        if (target == LoanClosureStatus.APPROVED) {
            closureCase.setApprovedAt(LocalDateTime.now());
        }
        if (target == LoanClosureStatus.CLOSED) {
            closureCase.setClosedAt(LocalDateTime.now());
        }

        closureCase.setRemarks(appendRemarks(closureCase.getRemarks(), request.getRemarks()));
        transition(closureCase, target, "STATUS_CHANGE", request.getRemarks());
        LoanClosureCase saved = closureCaseRepository.save(closureCase);
        recordEvent(saved, eventTypeFor(target), current, target, request.getRemarks());
        LoanClosureResponse response = toResponse(saved);
        idempotencyStore.put(idempotencyKey, response);
        return response;
    }

    @Transactional(readOnly = true)
    public LoanClosureSummaryResponse summary() {
        List<LoanClosureCase> closureCases = closureCaseRepository.findAll();
        Map<LoanClosureStatus, Long> closureStatusCounts = new EnumMap<>(LoanClosureStatus.class);
        for (LoanClosureStatus status : LoanClosureStatus.values()) {
            closureStatusCounts.put(status, closureCaseRepository.countByClosureStatus(status));
        }
        Map<ReconciliationStatus, Long> reconciliationStatusCounts = new EnumMap<>(ReconciliationStatus.class);
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            reconciliationStatusCounts.put(status, closureCaseRepository.countByReconciliationStatus(status));
        }

        return LoanClosureSummaryResponse.builder()
                .totalRequests(closureCases.size())
                .pendingRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.REQUESTED, 0L))
                .settlementCalculatedRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.SETTLEMENT_CALCULATED, 0L))
                .reconciliationPendingRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.RECONCILIATION_PENDING, 0L))
                .reconciledRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.RECONCILED, 0L))
                .approvedRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.APPROVED, 0L))
                .closedRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.CLOSED, 0L))
                .rejectedRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.REJECTED, 0L))
                .onHoldRequests(closureStatusCounts.getOrDefault(LoanClosureStatus.ON_HOLD, 0L))
                .matchedReconciliations(reconciliationStatusCounts.getOrDefault(ReconciliationStatus.MATCHED, 0L))
                .mismatchedReconciliations(reconciliationStatusCounts.getOrDefault(ReconciliationStatus.MISMATCHED, 0L))
                .pendingReconciliations(reconciliationStatusCounts.getOrDefault(ReconciliationStatus.PENDING, 0L))
                .totalSettlementAmount(scale(closureCaseRepository.sumSettlementAmount()))
                .totalOutstandingPrincipal(scale(closureCaseRepository.sumOutstandingPrincipal()))
                .closureStatusCounts(closureStatusCounts)
                .reconciliationStatusCounts(reconciliationStatusCounts)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<LoanClosureResponse> search(
            String loanAccountNumber,
            String borrowerName,
            LoanClosureStatus closureStatus,
            ReconciliationStatus reconciliationStatus,
            BigDecimal minSettlementAmount,
            BigDecimal maxSettlementAmount,
            Pageable pageable) {
        Specification<LoanClosureCase> spec = Specification.where(null);
        if (loanAccountNumber != null && !loanAccountNumber.isBlank()) {
            String normalized = loanAccountNumber.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("loanAccountNumber")), "%" + normalized + "%"));
        }
        if (borrowerName != null && !borrowerName.isBlank()) {
            String normalized = borrowerName.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("borrowerName")), "%" + normalized + "%"));
        }
        if (closureStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("closureStatus"), closureStatus));
        }
        if (reconciliationStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("reconciliationStatus"), reconciliationStatus));
        }
        if (minSettlementAmount != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("settlementAmount"), scale(minSettlementAmount)));
        }
        if (maxSettlementAmount != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("settlementAmount"), scale(maxSettlementAmount)));
        }

        return closureCaseRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<LoanClosureEventResponse> searchEvents(String requestId, String loanAccountNumber, LoanClosureEventType eventType, String text) {
        return auditStore.search(requestId, loanAccountNumber, eventType).stream()
                .filter(event -> text == null || text.isBlank() || matchesText(event, text))
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportClosuresCsv(
            String loanAccountNumber,
            String borrowerName,
            LoanClosureStatus closureStatus,
            ReconciliationStatus reconciliationStatus,
            BigDecimal minSettlementAmount,
            BigDecimal maxSettlementAmount) {
        List<LoanClosureResponse> closures = search(
                loanAccountNumber,
                borrowerName,
                closureStatus,
                reconciliationStatus,
                minSettlementAmount,
                maxSettlementAmount,
                Pageable.unpaged()).getContent();
        StringBuilder csv = new StringBuilder();
        csv.append("id,requestId,loanAccountNumber,borrowerName,closureReason,outstandingPrincipal,accruedInterest,penaltyAmount,processingFee,settlementAdjustment,settlementAmount,bankConfirmedAmount,settlementDifference,closureStatus,reconciliationStatus,remarks,createdBy,requestedAt,calculatedAt,reconciledAt,approvedAt,closedAt\n");
        for (LoanClosureResponse closure : closures) {
            csv.append(csvCell(closure.id())).append(',')
                    .append(csvCell(closure.requestId())).append(',')
                    .append(csvCell(closure.loanAccountNumber())).append(',')
                    .append(csvCell(closure.borrowerName())).append(',')
                    .append(csvCell(closure.closureReason())).append(',')
                    .append(csvCell(closure.outstandingPrincipal())).append(',')
                    .append(csvCell(closure.accruedInterest())).append(',')
                    .append(csvCell(closure.penaltyAmount())).append(',')
                    .append(csvCell(closure.processingFee())).append(',')
                    .append(csvCell(closure.settlementAdjustment())).append(',')
                    .append(csvCell(closure.settlementAmount())).append(',')
                    .append(csvCell(closure.bankConfirmedAmount())).append(',')
                    .append(csvCell(closure.settlementDifference())).append(',')
                    .append(csvCell(closure.closureStatus())).append(',')
                    .append(csvCell(closure.reconciliationStatus())).append(',')
                    .append(csvCell(closure.remarks())).append(',')
                    .append(csvCell(closure.createdBy())).append(',')
                    .append(csvCell(closure.requestedAt())).append(',')
                    .append(csvCell(closure.calculatedAt())).append(',')
                    .append(csvCell(closure.reconciledAt())).append(',')
                    .append(csvCell(closure.approvedAt())).append(',')
                    .append(csvCell(closure.closedAt()))
                    .append('\n');
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public String exportEventsCsv(String requestId, String loanAccountNumber, LoanClosureEventType eventType, String text) {
        List<LoanClosureEventResponse> events = searchEvents(requestId, loanAccountNumber, eventType, text);
        StringBuilder csv = new StringBuilder();
        csv.append("requestId,closureId,loanAccountNumber,eventType,fromStatus,toStatus,reconciliationStatus,actor,details,createdAt\n");
        for (LoanClosureEventResponse event : events) {
            csv.append(csvCell(event.requestId())).append(',')
                    .append(csvCell(event.closureId())).append(',')
                    .append(csvCell(event.loanAccountNumber())).append(',')
                    .append(csvCell(event.eventType())).append(',')
                    .append(csvCell(event.fromStatus())).append(',')
                    .append(csvCell(event.toStatus())).append(',')
                    .append(csvCell(event.reconciliationStatus())).append(',')
                    .append(csvCell(event.actor())).append(',')
                    .append(csvCell(event.details())).append(',')
                    .append(csvCell(event.createdAt()))
                    .append('\n');
        }
        return csv.toString();
    }

    private LoanClosureCase findClosureCase(Long id) {
        return closureCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan closure case not found with id: " + id));
    }

    private void transition(LoanClosureCase closureCase, LoanClosureStatus target, String action, String remarks) {
        LoanClosureStatus current = closureCase.getClosureStatus();
        closureCase.setClosureStatus(target);
        closureCase.addHistory(history(current, target, action, remarks));
    }

    private void recordEvent(
            LoanClosureCase closureCase,
            LoanClosureEventType eventType,
            LoanClosureStatus fromStatus,
            LoanClosureStatus toStatus,
            String details) {
        auditStore.append(new LoanClosureEvent(
                closureCase.getRequestId(),
                closureCase.getId(),
                closureCase.getLoanAccountNumber(),
                eventType,
                fromStatus,
                toStatus,
                closureCase.getReconciliationStatus(),
                currentActor(),
                trimToNull(details),
                LocalDateTime.now()));
    }

    private LoanClosureStatusHistory history(LoanClosureStatus from, LoanClosureStatus to, String action, String remarks) {
        LoanClosureStatusHistory history = new LoanClosureStatusHistory();
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setActionName(action);
        history.setRemarks(trimToNull(remarks));
        history.setChangedBy(currentActor());
        return history;
    }

    private LoanClosureResponse toResponse(LoanClosureCase closureCase) {
        List<LoanClosureHistoryResponse> historyResponses = closureCase.getStatusHistory().stream()
                .map(history -> LoanClosureHistoryResponse.builder()
                        .fromStatus(history.getFromStatus())
                        .toStatus(history.getToStatus())
                        .actionName(history.getActionName())
                        .remarks(history.getRemarks())
                        .changedBy(history.getChangedBy())
                        .changedAt(history.getChangedAt())
                        .build())
                .toList();

        return LoanClosureResponse.builder()
                .id(closureCase.getId())
                .requestId(closureCase.getRequestId())
                .loanAccountNumber(closureCase.getLoanAccountNumber())
                .borrowerName(closureCase.getBorrowerName())
                .closureReason(closureCase.getClosureReason())
                .outstandingPrincipal(closureCase.getOutstandingPrincipal())
                .accruedInterest(closureCase.getAccruedInterest())
                .penaltyAmount(closureCase.getPenaltyAmount())
                .processingFee(closureCase.getProcessingFee())
                .settlementAdjustment(closureCase.getSettlementAdjustment())
                .settlementAmount(closureCase.getSettlementAmount())
                .bankConfirmedAmount(closureCase.getBankConfirmedAmount())
                .settlementDifference(closureCase.getSettlementDifference())
                .closureStatus(closureCase.getClosureStatus())
                .reconciliationStatus(closureCase.getReconciliationStatus())
                .remarks(closureCase.getRemarks())
                .createdBy(closureCase.getCreatedBy())
                .requestedAt(closureCase.getRequestedAt())
                .calculatedAt(closureCase.getCalculatedAt())
                .reconciledAt(closureCase.getReconciledAt())
                .approvedAt(closureCase.getApprovedAt())
                .closedAt(closureCase.getClosedAt())
                .statusHistory(historyResponses)
                .build();
    }

    private String currentActor() {
        return "SYSTEM";
    }

    private BigDecimal calculateSettlement(BigDecimal outstandingPrincipal, BigDecimal accruedInterest, BigDecimal penaltyAmount, BigDecimal processingFee, BigDecimal adjustmentAmount) {
        return outstandingPrincipal
                .add(accruedInterest)
                .add(penaltyAmount)
                .add(processingFee)
                .subtract(adjustmentAmount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String appendRemarks(String current, String additional) {
        String currentRemarks = trimToNull(current);
        String newRemarks = trimToNull(additional);
        if (currentRemarks == null) {
            return newRemarks;
        }
        if (newRemarks == null) {
            return currentRemarks;
        }
        return currentRemarks + " | " + newRemarks;
    }

    private LoanClosureEventType eventTypeFor(LoanClosureStatus status) {
        return switch (status) {
            case APPROVED -> LoanClosureEventType.APPROVED;
            case CLOSED -> LoanClosureEventType.CLOSED;
            case REJECTED -> LoanClosureEventType.REJECTED;
            default -> LoanClosureEventType.STATUS_CHANGED;
        };
    }

    private boolean matchesText(LoanClosureEvent event, String text) {
        String normalized = text.toLowerCase();
        return contains(event.requestId(), normalized)
                || contains(event.loanAccountNumber(), normalized)
                || contains(event.actor(), normalized)
                || contains(event.details(), normalized)
                || contains(event.eventType() == null ? null : event.eventType().name(), normalized);
    }

    private boolean contains(String value, String text) {
        return value != null && value.toLowerCase().contains(text);
    }

    private LoanClosureEventResponse toEventResponse(LoanClosureEvent event) {
        return LoanClosureEventResponse.builder()
                .requestId(event.requestId())
                .closureId(event.closureId())
                .loanAccountNumber(event.loanAccountNumber())
                .eventType(event.eventType())
                .fromStatus(event.fromStatus())
                .toStatus(event.toStatus())
                .reconciliationStatus(event.reconciliationStatus())
                .actor(event.actor())
                .details(event.details())
                .createdAt(event.createdAt())
                .build();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
