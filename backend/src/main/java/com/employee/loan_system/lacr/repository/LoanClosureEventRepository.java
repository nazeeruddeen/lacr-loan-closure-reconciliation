package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanClosureEventRepository extends JpaRepository<LoanClosureEventEntity, Long> {
    List<LoanClosureEventEntity> findAllByOrderByCreatedAtDesc();
    Optional<LoanClosureEventEntity> findTopByClosureCaseIdOrderByCreatedAtDesc(Long closureCaseId);
}
