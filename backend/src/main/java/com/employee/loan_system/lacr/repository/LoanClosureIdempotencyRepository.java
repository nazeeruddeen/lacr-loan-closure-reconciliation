package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureIdempotencyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanClosureIdempotencyRepository extends JpaRepository<LoanClosureIdempotencyEntry, Long> {
    Optional<LoanClosureIdempotencyEntry> findByIdempotencyKey(String idempotencyKey);
}
