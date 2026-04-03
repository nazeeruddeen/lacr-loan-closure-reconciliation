package com.employee.loan_system.lacr.document;

import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "loan_closure_audit_events")
public class LoanClosureAuditEventDocument {

    @Id
    private String id;

    private String requestId;
    private Long closureId;
    private String loanAccountNumber;
    private LoanClosureEventType eventType;
    private LoanClosureStatus fromStatus;
    private LoanClosureStatus toStatus;
    private ReconciliationStatus reconciliationStatus;
    private String actor;
    private String details;
    private LocalDateTime createdAt;
    private String previousHash;
    private String recordHash;
}
