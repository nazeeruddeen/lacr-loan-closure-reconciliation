package com.employee.loan_system.lacr.controller;

import com.employee.loan_system.lacr.audit.LoanClosureEventType;
import com.employee.loan_system.lacr.dto.AdvanceClosureStatusRequest;
import com.employee.loan_system.lacr.dto.CalculateSettlementRequest;
import com.employee.loan_system.lacr.dto.CreateLoanClosureRequest;
import com.employee.loan_system.lacr.dto.LoanClosureEventResponse;
import com.employee.loan_system.lacr.dto.LoanClosureResponse;
import com.employee.loan_system.lacr.dto.LoanClosureSummaryResponse;
import com.employee.loan_system.lacr.dto.ReconcileClosureRequest;
import com.employee.loan_system.lacr.entity.LoanClosureStatus;
import com.employee.loan_system.lacr.entity.ReconciliationStatus;
import com.employee.loan_system.lacr.service.LoanClosureService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/closure-requests")
public class LoanClosureController {

    private final LoanClosureService closureService;

    public LoanClosureController(LoanClosureService closureService) {
        this.closureService = closureService;
    }

    @PostMapping
    public ResponseEntity<LoanClosureResponse> create(@Valid @RequestBody CreateLoanClosureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(closureService.createClosureRequest(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanClosureResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(closureService.getClosureRequest(id));
    }

    @PostMapping("/{id}/settlement")
    public ResponseEntity<LoanClosureResponse> calculateSettlement(
            @PathVariable Long id,
            @Valid @RequestBody CalculateSettlementRequest request) {
        return ResponseEntity.ok(closureService.calculateSettlement(id, request));
    }

    @PostMapping("/{id}/reconciliation")
    public ResponseEntity<LoanClosureResponse> moveToReconciliation(
            @PathVariable Long id,
            @Valid @RequestBody AdvanceClosureStatusRequest request) {
        return ResponseEntity.ok(closureService.moveToReconciliation(id, request));
    }

    @PostMapping("/{id}/reconcile")
    public ResponseEntity<LoanClosureResponse> reconcile(
            @PathVariable Long id,
            @Valid @RequestBody ReconcileClosureRequest request) {
        return ResponseEntity.ok(closureService.reconcile(id, request));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<LoanClosureResponse> advanceStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdvanceClosureStatusRequest request) {
        return ResponseEntity.ok(closureService.advanceStatus(id, request));
    }

    @GetMapping("/summary")
    public ResponseEntity<LoanClosureSummaryResponse> summary() {
        return ResponseEntity.ok(closureService.summary());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<LoanClosureResponse>> search(
            @RequestParam(required = false) String loanAccountNumber,
            @RequestParam(required = false) String borrowerName,
            @RequestParam(required = false) LoanClosureStatus closureStatus,
            @RequestParam(required = false) ReconciliationStatus reconciliationStatus,
            @RequestParam(required = false) BigDecimal minSettlementAmount,
            @RequestParam(required = false) BigDecimal maxSettlementAmount,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                closureService.search(loanAccountNumber, borrowerName, closureStatus, reconciliationStatus, minSettlementAmount, maxSettlementAmount, pageable));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<LoanClosureSummaryResponse> reportSummary() {
        return ResponseEntity.ok(closureService.summary());
    }

    @GetMapping(value = "/reports/summary.csv", produces = "text/csv")
    public ResponseEntity<String> reportSummaryCsv() {
        LoanClosureSummaryResponse summary = closureService.summary();
        String csv = new StringBuilder()
                .append("metric,value\n")
                .append("totalRequests,").append(summary.totalRequests()).append('\n')
                .append("pendingRequests,").append(summary.pendingRequests()).append('\n')
                .append("settlementCalculatedRequests,").append(summary.settlementCalculatedRequests()).append('\n')
                .append("reconciliationPendingRequests,").append(summary.reconciliationPendingRequests()).append('\n')
                .append("reconciledRequests,").append(summary.reconciledRequests()).append('\n')
                .append("approvedRequests,").append(summary.approvedRequests()).append('\n')
                .append("closedRequests,").append(summary.closedRequests()).append('\n')
                .append("rejectedRequests,").append(summary.rejectedRequests()).append('\n')
                .append("onHoldRequests,").append(summary.onHoldRequests()).append('\n')
                .append("matchedReconciliations,").append(summary.matchedReconciliations()).append('\n')
                .append("mismatchedReconciliations,").append(summary.mismatchedReconciliations()).append('\n')
                .append("pendingReconciliations,").append(summary.pendingReconciliations()).append('\n')
                .append("totalSettlementAmount,").append(summary.totalSettlementAmount()).append('\n')
                .append("totalOutstandingPrincipal,").append(summary.totalOutstandingPrincipal()).append('\n')
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=closure-summary.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/reports/events")
    public ResponseEntity<List<LoanClosureEventResponse>> reportEvents(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String loanAccountNumber,
            @RequestParam(required = false) LoanClosureEventType eventType,
            @RequestParam(required = false) String text) {
        return ResponseEntity.ok(closureService.searchEvents(requestId, loanAccountNumber, eventType, text));
    }

    @GetMapping(value = "/reports/events.csv", produces = "text/csv")
    public ResponseEntity<String> reportEventsCsv(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String loanAccountNumber,
            @RequestParam(required = false) LoanClosureEventType eventType,
            @RequestParam(required = false) String text) {
        String csv = closureService.exportEventsCsv(requestId, loanAccountNumber, eventType, text);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=closure-events.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping(value = "/reports/closures.csv", produces = "text/csv")
    public ResponseEntity<String> reportClosuresCsv(
            @RequestParam(required = false) String loanAccountNumber,
            @RequestParam(required = false) String borrowerName,
            @RequestParam(required = false) LoanClosureStatus closureStatus,
            @RequestParam(required = false) ReconciliationStatus reconciliationStatus,
            @RequestParam(required = false) BigDecimal minSettlementAmount,
            @RequestParam(required = false) BigDecimal maxSettlementAmount) {
        String csv = closureService.exportClosuresCsv(loanAccountNumber, borrowerName, closureStatus, reconciliationStatus, minSettlementAmount, maxSettlementAmount);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=closure-search.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
