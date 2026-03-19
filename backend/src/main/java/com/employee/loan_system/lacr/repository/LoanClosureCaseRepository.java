package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureCase;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.util.Optional;

public interface LoanClosureCaseRepository extends JpaRepository<LoanClosureCase, Long>, JpaSpecificationExecutor<LoanClosureCase> {

    Optional<LoanClosureCase> findByRequestId(String requestId);

    long countByClosureStatus(LoanClosureStatus closureStatus);

    long countByReconciliationStatus(ReconciliationStatus reconciliationStatus);

    @Query("select coalesce(sum(c.settlementAmount), 0) from LoanClosureCase c")
    BigDecimal sumSettlementAmount();

    @Query("select coalesce(sum(c.outstandingPrincipal), 0) from LoanClosureCase c")
    BigDecimal sumOutstandingPrincipal();
}
