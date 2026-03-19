package com.employee.loan_system.lacr.service;

import com.employee.loan_system.lacr.audit.LoanClosureAuditStore;
import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.cache.LoanClosureIdempotencyStore;
import com.employee.loan_system.lacr.dto.AdvanceClosureStatusRequest;
import com.employee.loan_system.lacr.dto.CalculateSettlementRequest;
import com.employee.loan_system.lacr.dto.CreateLoanClosureRequest;
import com.employee.loan_system.lacr.dto.ReconcileClosureRequest;
import com.employee.loan_system.lacr.entity.LoanClosureCase;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.repository.LoanClosureCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanClosureServiceTest {

    @Mock
    private LoanClosureCaseRepository closureCaseRepository;

    private final LoanClosureAuditStore auditStore = new LoanClosureAuditStore();
    private final LoanClosureIdempotencyStore idempotencyStore = new LoanClosureIdempotencyStore();

    @Test
    void createShouldBeIdempotentOnRequestId() {
        LoanClosureService service = new LoanClosureService(closureCaseRepository, auditStore, idempotencyStore);
        when(closureCaseRepository.findByRequestId("REQ-001")).thenReturn(Optional.empty());
        when(closureCaseRepository.save(any(LoanClosureCase.class))).thenAnswer(invocation -> {
            LoanClosureCase closureCase = invocation.getArgument(0);
            closureCase.setId(1L);
            return closureCase;
        });

        CreateLoanClosureRequest request = new CreateLoanClosureRequest();
        request.setRequestId("REQ-001");
        request.setLoanAccountNumber("LN-1001");
        request.setBorrowerName("Asha Rao");
        request.setClosureReason("Borrower requested pre-closure");
        request.setOutstandingPrincipal(new BigDecimal("100000.00"));
        request.setAccruedInterest(new BigDecimal("1200.00"));
        request.setPenaltyAmount(new BigDecimal("300.00"));
        request.setProcessingFee(new BigDecimal("100.00"));

        var first = service.createClosureRequest(request);
        var second = service.createClosureRequest(request);

        assertThat(first.requestId()).isEqualTo("REQ-001");
        assertThat(second.requestId()).isEqualTo("REQ-001");
        assertThat(first.settlementAmount()).isEqualByComparingTo("101600.00");
        assertThat(service.searchEvents("REQ-001", null, LoanClosureEventType.CREATED, null)).hasSize(1);
    }

    @Test
    void calculateShouldApplyAdjustment() {
        LoanClosureService service = new LoanClosureService(closureCaseRepository, auditStore, idempotencyStore);
        LoanClosureCase closureCase = existingCase();
        closureCase.setClosureStatus(LoanClosureStatus.REQUESTED);
        when(closureCaseRepository.findById(1L)).thenReturn(Optional.of(closureCase));
        when(closureCaseRepository.save(any(LoanClosureCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalculateSettlementRequest request = new CalculateSettlementRequest();
        request.setAdjustmentAmount(new BigDecimal("500.00"));
        request.setRemarks("Waiver approved");

        var response = service.calculateSettlement(1L, request);

        assertThat(response.settlementAmount()).isEqualByComparingTo("101100.00");
        assertThat(response.closureStatus()).isEqualTo(LoanClosureStatus.SETTLEMENT_CALCULATED);
    }

    @Test
    void reconcileShouldMarkMismatchAsOnHold() {
        LoanClosureService service = new LoanClosureService(closureCaseRepository, auditStore, idempotencyStore);
        LoanClosureCase closureCase = existingCase();
        closureCase.setClosureStatus(LoanClosureStatus.RECONCILIATION_PENDING);
        closureCase.setSettlementAmount(new BigDecimal("101600.00"));
        when(closureCaseRepository.findById(1L)).thenReturn(Optional.of(closureCase));
        when(closureCaseRepository.save(any(LoanClosureCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconcileClosureRequest request = new ReconcileClosureRequest();
        request.setBankConfirmedAmount(new BigDecimal("101000.00"));
        request.setRemarks("Mismatch in settlement amount");

        var response = service.reconcile(1L, request);

        assertThat(response.reconciliationStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
        assertThat(response.closureStatus()).isEqualTo(LoanClosureStatus.ON_HOLD);
    }

    @Test
    void approveShouldRejectWithoutMatchedReconciliation() {
        LoanClosureService service = new LoanClosureService(closureCaseRepository, auditStore, idempotencyStore);
        LoanClosureCase closureCase = existingCase();
        closureCase.setClosureStatus(LoanClosureStatus.RECONCILED);
        closureCase.setReconciliationStatus(ReconciliationStatus.MISMATCHED);
        when(closureCaseRepository.findById(1L)).thenReturn(Optional.of(closureCase));

        AdvanceClosureStatusRequest request = new AdvanceClosureStatusRequest();
        request.setTargetStatus(LoanClosureStatus.APPROVED);
        request.setRemarks("Try to approve anyway");

        assertThatThrownBy(() -> service.advanceStatus(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only matched closures can be approved");
    }

    @Test
    void searchShouldFilterClosures() {
        LoanClosureService service = new LoanClosureService(closureCaseRepository, auditStore, idempotencyStore);
        when(closureCaseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(existingCase())));

        var page = service.search("LN-1001", "Asha", LoanClosureStatus.REQUESTED, ReconciliationStatus.PENDING, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).requestId()).isEqualTo("REQ-001");
    }

    private LoanClosureCase existingCase() {
        LoanClosureCase closureCase = new LoanClosureCase();
        closureCase.setId(1L);
        closureCase.setRequestId("REQ-001");
        closureCase.setLoanAccountNumber("LN-1001");
        closureCase.setBorrowerName("Asha Rao");
        closureCase.setClosureReason("Borrower requested pre-closure");
        closureCase.setOutstandingPrincipal(new BigDecimal("100000.00"));
        closureCase.setAccruedInterest(new BigDecimal("1200.00"));
        closureCase.setPenaltyAmount(new BigDecimal("300.00"));
        closureCase.setProcessingFee(new BigDecimal("100.00"));
        closureCase.setSettlementAdjustment(BigDecimal.ZERO.setScale(2));
        closureCase.setSettlementAmount(new BigDecimal("101600.00"));
        closureCase.setReconciliationStatus(ReconciliationStatus.PENDING);
        return closureCase;
    }
}
