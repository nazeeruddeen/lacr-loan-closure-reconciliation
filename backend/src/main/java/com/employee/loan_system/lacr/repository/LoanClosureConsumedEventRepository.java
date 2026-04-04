package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanClosureConsumedEventRepository extends JpaRepository<LoanClosureConsumedEvent, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
