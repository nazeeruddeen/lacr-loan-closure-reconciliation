package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureAuditStore;
import com.employee.loan_system.lacr.audit.LoanClosureEvent;
import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.entity.LoanClosureCase;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoanClosureWorkflowRecorder {

    private final LoanClosureAuditStore auditStore;
    private final LoanClosureOutboxService outboxService;

    public LoanClosureWorkflowRecorder(
            LoanClosureAuditStore auditStore,
            LoanClosureOutboxService outboxService) {
        this.auditStore = auditStore;
        this.outboxService = outboxService;
    }

    public void record(
            LoanClosureCase closureCase,
            LoanClosureEventType eventType,
            LoanClosureStatus fromStatus,
            LoanClosureStatus toStatus,
            String actor,
            String details) {
        LoanClosureEvent event = new LoanClosureEvent(
                closureCase.getRequestId(),
                closureCase.getId(),
                closureCase.getLoanAccountNumber(),
                eventType,
                fromStatus,
                toStatus,
                closureCase.getReconciliationStatus(),
                actor,
                details,
                LocalDateTime.now());
        auditStore.append(event);
        outboxService.appendWorkflowEvent(event);
    }
}
