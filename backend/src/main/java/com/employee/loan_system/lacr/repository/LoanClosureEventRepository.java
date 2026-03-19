package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanClosureEventRepository extends JpaRepository<LoanClosureEventEntity, Long> {
    List<LoanClosureEventEntity> findAllByOrderByCreatedAtDesc();
}
