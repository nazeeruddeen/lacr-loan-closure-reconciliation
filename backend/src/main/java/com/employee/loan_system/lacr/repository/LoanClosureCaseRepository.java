package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.entity.LoanClosureCase;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LoanClosureCaseRepository extends JpaRepository<LoanClosureCase, Long>, JpaSpecificationExecutor<LoanClosureCase> {

    interface ClosureStatusCountRow {
        LoanClosureStatus getClosureStatus();

        long getTotal();
    }

    interface ReconciliationStatusCountRow {
        ReconciliationStatus getReconciliationStatus();

        long getTotal();
    }

    Optional<LoanClosureCase> findByRequestId(String requestId);

    long countByClosureStatus(LoanClosureStatus closureStatus);

    long countByReconciliationStatus(ReconciliationStatus reconciliationStatus);

    @Query("select coalesce(sum(c.settlementAmount), 0) from LoanClosureCase c")
    BigDecimal sumSettlementAmount();

    @Query("select coalesce(sum(c.outstandingPrincipal), 0) from LoanClosureCase c")
    BigDecimal sumOutstandingPrincipal();

    @Query("select c.closureStatus as closureStatus, count(c) as total from LoanClosureCase c group by c.closureStatus")
    List<ClosureStatusCountRow> findClosureStatusCounts();

    @Query("select c.reconciliationStatus as reconciliationStatus, count(c) as total from LoanClosureCase c group by c.reconciliationStatus")
    List<ReconciliationStatusCountRow> findReconciliationStatusCounts();
}
