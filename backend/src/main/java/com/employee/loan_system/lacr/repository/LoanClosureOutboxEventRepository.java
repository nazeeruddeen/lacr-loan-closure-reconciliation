package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureOutboxEvent;
import com.employee.loan_system.lacr.entity.LoanClosureOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanClosureOutboxEventRepository extends JpaRepository<LoanClosureOutboxEvent, Long> {
    List<LoanClosureOutboxEvent> findTop20ByPublishStatusOrderByCreatedAtAsc(LoanClosureOutboxStatus publishStatus);
}
