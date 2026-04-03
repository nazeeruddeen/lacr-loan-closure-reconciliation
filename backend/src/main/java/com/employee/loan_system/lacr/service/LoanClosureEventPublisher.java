package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;

public interface LoanClosureEventPublisher {
    void publish(LoanClosureOutboxEvent event);
}
