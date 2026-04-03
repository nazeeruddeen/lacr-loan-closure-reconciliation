package com.employee.loan_system.lacr.controller;

import com.employee.loan_system.lacr.dto.FailedEventResponse;
import com.employee.loan_system.lacr.dto.OutboxHealthResponse;
import com.employee.loan_system.lacr.dto.OutboxRecoveryResponse;
import com.employee.loan_system.lacr.entity.FailedEvent;
import com.employee.loan_system.lacr.service.FailedEventService;
import com.employee.loan_system.lacr.service.LoanClosureOutboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ops")
public class LacrOperationsController {

    private final LoanClosureOutboxService outboxService;
    private final FailedEventService failedEventService;

    public LacrOperationsController(
            LoanClosureOutboxService outboxService,
            FailedEventService failedEventService) {
        this.outboxService = outboxService;
        this.failedEventService = failedEventService;
    }

    @GetMapping("/outbox/health")
    public ResponseEntity<OutboxHealthResponse> outboxHealth() {
        return ResponseEntity.ok(outboxService.outboxHealth());
    }

    @PostMapping("/outbox/recover")
    public ResponseEntity<OutboxRecoveryResponse> recoverOutbox() {
        return ResponseEntity.ok(outboxService.recoverAndPublishStaleProcessingEvents());
    }

    @GetMapping("/failed-events")
    public ResponseEntity<List<FailedEventResponse>> failedEvents(
            @RequestParam(required = false) String loanAccountNumber,
            @RequestParam(required = false) String requestId) {
        List<FailedEvent> events;
        if (loanAccountNumber != null && !loanAccountNumber.isBlank()) {
            events = failedEventService.listByLoanAccount(loanAccountNumber);
        } else if (requestId != null && !requestId.isBlank()) {
            events = failedEventService.listByRequestId(requestId);
        } else {
            events = failedEventService.listAll();
        }
        return ResponseEntity.ok(events.stream().map(this::toResponse).toList());
    }

    private FailedEventResponse toResponse(FailedEvent event) {
        return new FailedEventResponse(
                event.getId(),
                event.getRequestId(),
                event.getLoanAccountNumber(),
                event.getFailureReason(),
                event.getAttemptCount(),
                event.getFailedStage(),
                event.getCreatedBy(),
                event.getFailedAt());
    }
}
