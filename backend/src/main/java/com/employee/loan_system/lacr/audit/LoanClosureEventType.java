package com.employee.loan_system.lacr.audit;

public enum LoanClosureEventType {
    CREATED,
    SETTLEMENT_CALCULATED,
    RECONCILIATION_STARTED,
    RECONCILED_MATCHED,
    RECONCILED_MISMATCHED,
    STATUS_CHANGED,
    APPROVED,
    CLOSED,
    REJECTED
}
