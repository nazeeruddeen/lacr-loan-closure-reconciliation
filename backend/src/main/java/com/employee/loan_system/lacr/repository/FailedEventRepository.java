package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {

    /** Find all failed events for a specific loan account — for admin replay UI */
    List<FailedEvent> findByLoanAccountNumberOrderByFailedAtDesc(String loanAccountNumber);

    /** Find all failed events for a given request ID (to check if already stored) */
    List<FailedEvent> findByRequestIdOrderByFailedAtDesc(String requestId);
}
