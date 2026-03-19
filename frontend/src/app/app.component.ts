import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  AdvanceClosureStatusRequest,
  CalculateSettlementRequest,
  ClosureSearchFilters,
  CreateLoanClosureRequest,
  LoanClosureEventItem,
  LoanClosureItem,
  LoanClosurePageResponse,
  LoanClosureSummary,
  ReconcileClosureRequest
} from './lacr.models';
import { LacrApiService } from './lacr-api.service';

type StageCard = {
  title: string;
  count: number;
  note: string;
  state: 'open' | 'active' | 'done' | 'risk';
};

type EventSearchForm = {
  requestId?: string;
  loanAccountNumber?: string;
  eventType?: string;
  text?: string;
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  loading = false;
  heroAction: 'refresh' | 'summary' | 'events' | null = null;
  busyClosureId: number | null = null;
  busyAction: string | null = null;
  lastRefreshed = '';
  actionMessage = 'Ready';
  activeWindow = '7D';
  activeStage = 'Reconciliation Pending';
  pageSize = 6;

  summary: LoanClosureSummary = this.emptySummary();
  closurePage: LoanClosurePageResponse = this.emptyPage();
  closures: LoanClosureItem[] = [];
  events: LoanClosureEventItem[] = [];
  selectedClosure: LoanClosureItem | null = null;

  stages: StageCard[] = [
    { title: 'Requested', count: 0, note: 'Intake completed and awaiting settlement', state: 'open' },
    { title: 'Settlement Calculated', count: 0, note: 'Accrued interest and penalty finalized', state: 'active' },
    { title: 'Reconciliation Pending', count: 0, note: 'Bank confirmation in progress', state: 'active' },
    { title: 'Approved / Closed', count: 0, note: 'Matched and fully closed', state: 'done' }
  ];

  activeTab: string = 'dashboard';

  setTab(tab: string): void {
    this.activeTab = tab;
  }

  readonly searchForm: FormGroup;
  readonly createForm: FormGroup;
  readonly settlementForm: FormGroup;
  readonly reconcileForm: FormGroup;
  readonly statusForm: FormGroup;
  readonly eventForm: FormGroup;

  constructor(private readonly api: LacrApiService, private readonly fb: FormBuilder) {
    this.searchForm = this.fb.group({
      loanAccountNumber: [''],
      borrowerName: [''],
      closureStatus: [''],
      reconciliationStatus: [''],
      minSettlementAmount: [''],
      maxSettlementAmount: ['']
    });

    this.createForm = this.fb.group({
      requestId: [''],
      loanAccountNumber: [''],
      borrowerName: [''],
      closureReason: [''],
      outstandingPrincipal: [''],
      accruedInterest: [''],
      penaltyAmount: [''],
      processingFee: [''],
      remarks: ['']
    });

    this.settlementForm = this.fb.group({
      adjustmentAmount: ['0.00'],
      remarks: ['']
    });

    this.reconcileForm = this.fb.group({
      bankConfirmedAmount: ['0.00'],
      remarks: ['']
    });

    this.statusForm = this.fb.group({
      targetStatus: ['APPROVED'],
      remarks: ['']
    });

    this.eventForm = this.fb.group({
      requestId: [''],
      loanAccountNumber: [''],
      eventType: [''],
      text: ['']
    });
  }

  ngOnInit(): void {
    this.refreshAll();
  }

  refreshAll(): void {
    this.loading = true;
    this.heroAction = 'refresh';
    this.actionMessage = 'Refreshing dashboard data...';
    this.loadSummary();
    this.loadClosures(0);
    this.loadEvents();
  }

  loadSummary(): void {
    this.api.summary().subscribe((summary) => {
      this.summary = summary;
      this.syncStages(summary);
      this.lastRefreshed = new Date().toLocaleString();
      this.actionMessage = `Dashboard refreshed at ${this.lastRefreshed}`;
      this.heroAction = null;
    });
  }

  loadClosures(page = this.closurePage.number || 0): void {
    const filters = this.normalizeClosureFilters(this.searchForm.value);
    this.api.search(filters, page, this.pageSize).pipe(finalize(() => (this.loading = false))).subscribe((pageResponse) => {
      this.closurePage = pageResponse;
      this.closures = pageResponse.content ?? [];
      if (this.selectedClosure) {
        const refreshed = this.closures.find((item) => item.id === this.selectedClosure?.id);
        if (refreshed) {
          this.selectedClosure = refreshed;
          this.patchActionForms(refreshed);
        }
      }
    });
  }

  loadEvents(): void {
    const filters = this.normalizeEventFilters(this.eventForm.value);
    this.api.events(filters).subscribe((events) => {
      this.events = events;
    });
  }

  runSearch(): void {
    this.loading = true;
    this.loadClosures(0);
  }

  nextPage(): void {
    if (!this.closurePage.last) {
      this.loadClosures((this.closurePage.number || 0) + 1);
    }
  }

  previousPage(): void {
    if (!this.closurePage.first) {
      this.loadClosures((this.closurePage.number || 0) - 1);
    }
  }

  createClosure(): void {
    const request = this.createForm.value as CreateLoanClosureRequest;
    this.loading = true;
    this.api.create(request).pipe(finalize(() => (this.loading = false))).subscribe({
      next: (closure) => {
        this.selectedClosure = closure;
        this.patchActionForms(closure);
        this.refreshAfterWrite();
      }
    });
  }

  selectClosure(closure: LoanClosureItem): void {
    this.selectedClosure = closure;
    this.patchActionForms(closure);
    this.actionMessage = `Selected ${closure.requestId} for workflow actions`;
  }

  calculateSettlementFor(closure: LoanClosureItem): void {
    this.runClosureAction(closure, 'settle', () => this.calculateSettlement());
  }

  moveToReconciliationFor(closure: LoanClosureItem): void {
    this.runClosureAction(closure, 'recon', () => this.moveToReconciliation());
  }

  reconcileFor(closure: LoanClosureItem): void {
    this.runClosureAction(closure, 'reconcile', () => this.reconcile());
  }

  advanceStatusFor(closure: LoanClosureItem): void {
    this.runClosureAction(closure, 'advance', () => this.advanceStatus());
  }

  calculateSettlement(): void {
    if (!this.selectedClosure) {
      return;
    }
    const request = this.settlementForm.value as CalculateSettlementRequest;
    this.loading = true;
    this.api.calculateSettlement(this.selectedClosure.id, request).pipe(finalize(() => (this.loading = false))).subscribe({
      next: (closure) => {
        this.selectedClosure = closure;
        this.clearBusy();
        this.refreshAfterWrite();
      },
      error: () => this.clearBusy()
    });
  }

  moveToReconciliation(): void {
    if (!this.selectedClosure) {
      return;
    }
    const request: AdvanceClosureStatusRequest = {
      targetStatus: 'RECONCILIATION_PENDING',
      remarks: this.normalizeString(this.statusForm.value?.['remarks']) ?? 'Moved to reconciliation'
    };
    this.loading = true;
    this.api.moveToReconciliation(this.selectedClosure.id, request).pipe(finalize(() => (this.loading = false))).subscribe({
      next: (closure) => {
        this.selectedClosure = closure;
        this.clearBusy();
        this.refreshAfterWrite();
      },
      error: () => this.clearBusy()
    });
  }

  reconcile(): void {
    if (!this.selectedClosure) {
      return;
    }
    const request = this.reconcileForm.value as ReconcileClosureRequest;
    this.loading = true;
    this.api.reconcile(this.selectedClosure.id, request).pipe(finalize(() => (this.loading = false))).subscribe({
      next: (closure) => {
        this.selectedClosure = closure;
        this.clearBusy();
        this.refreshAfterWrite();
      },
      error: () => this.clearBusy()
    });
  }

  advanceStatus(): void {
    if (!this.selectedClosure) {
      return;
    }
    const request = this.statusForm.value as AdvanceClosureStatusRequest;
    this.loading = true;
    this.api.advanceStatus(this.selectedClosure.id, request).pipe(finalize(() => (this.loading = false))).subscribe({
      next: (closure) => {
        this.selectedClosure = closure;
        this.clearBusy();
        this.refreshAfterWrite();
      },
      error: () => this.clearBusy()
    });
  }

  exportSummaryCsv(): void {
    this.heroAction = 'summary';
    this.actionMessage = 'Preparing summary CSV...';
    this.api.summaryCsv().subscribe((csv) => this.download('closure-summary.csv', csv, 'Summary CSV downloaded'));
  }

  exportEventsCsv(): void {
    const filters = this.normalizeEventFilters(this.eventForm.value);
    this.heroAction = 'events';
    this.actionMessage = 'Preparing event CSV...';
    this.api.eventsCsv(filters).subscribe((csv) => this.download('closure-events.csv', csv, 'Events CSV downloaded'));
  }

  exportClosuresCsv(): void {
    const filters = this.normalizeClosureFilters(this.searchForm.value);
    this.actionMessage = 'Preparing closure search CSV...';
    this.api.closuresCsv(filters).subscribe((csv) => this.download('closure-search.csv', csv, 'Search CSV downloaded'));
  }

  closureTone(status: string): string {
    if (status === 'ON_HOLD' || status === 'MISMATCHED') {
      return 'risk';
    }
    if (status === 'RECONCILIATION_PENDING' || status === 'SETTLEMENT_CALCULATED') {
      return 'active';
    }
    return 'done';
  }

  statusTone(status: string): string {
    return this.closureTone(status);
  }

  trackByIndex(index: number): number {
    return index;
  }

  private refreshAfterWrite(): void {
    this.loadSummary();
    this.loadClosures(0);
    this.loadEvents();
  }

  private clearBusy(): void {
    this.busyClosureId = null;
    this.busyAction = null;
    this.actionMessage = 'Action completed';
  }

  private runClosureAction(closure: LoanClosureItem, action: string, callback: () => void): void {
    this.selectClosure(closure);
    this.busyClosureId = closure.id;
    this.busyAction = action;
    callback();
  }

  private patchActionForms(closure: LoanClosureItem): void {
    this.settlementForm.patchValue({
      adjustmentAmount: closure.settlementAdjustment ?? '0.00',
      remarks: closure.remarks ?? ''
    });
    this.reconcileForm.patchValue({
      bankConfirmedAmount: closure.bankConfirmedAmount ?? closure.settlementAmount ?? '0.00',
      remarks: closure.remarks ?? ''
    });
    this.statusForm.patchValue({
      targetStatus: this.nextStatusFor(closure.closureStatus),
      remarks: closure.remarks ?? ''
    });
  }

  private nextStatusFor(current: string): string {
    switch (current) {
      case 'REQUESTED':
        return 'SETTLEMENT_CALCULATED';
      case 'SETTLEMENT_CALCULATED':
        return 'RECONCILIATION_PENDING';
      case 'RECONCILIATION_PENDING':
      case 'RECONCILED':
        return 'APPROVED';
      case 'APPROVED':
        return 'CLOSED';
      default:
        return 'APPROVED';
    }
  }

  private normalizeClosureFilters(raw: Record<string, unknown>): ClosureSearchFilters {
    return {
      loanAccountNumber: this.normalizeString(raw['loanAccountNumber']),
      borrowerName: this.normalizeString(raw['borrowerName']),
      closureStatus: this.normalizeString(raw['closureStatus']),
      reconciliationStatus: this.normalizeString(raw['reconciliationStatus']),
      minSettlementAmount: this.normalizeString(raw['minSettlementAmount']),
      maxSettlementAmount: this.normalizeString(raw['maxSettlementAmount'])
    };
  }

  private normalizeEventFilters(raw: Record<string, unknown>): EventSearchForm {
    return {
      requestId: this.normalizeString(raw['requestId']),
      loanAccountNumber: this.normalizeString(raw['loanAccountNumber']),
      eventType: this.normalizeString(raw['eventType']),
      text: this.normalizeString(raw['text'])
    };
  }

  private normalizeString(value: unknown): string | undefined {
    if (value === null || value === undefined) {
      return undefined;
    }
    const text = String(value).trim();
    return text.length ? text : undefined;
  }

  private syncStages(summary: LoanClosureSummary): void {
    this.stages = [
      { ...this.stages[0], count: summary.pendingRequests, state: 'open' },
      { ...this.stages[1], count: summary.settlementCalculatedRequests, state: 'active' },
      { ...this.stages[2], count: summary.reconciliationPendingRequests, state: 'active' },
      { ...this.stages[3], count: summary.closedRequests, state: 'done' }
    ];
  }

  private download(filename: string, text: string, successMessage: string): void {
    const blob = new Blob([text], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
    this.busyClosureId = null;
    this.busyAction = null;
    this.heroAction = null;
    this.actionMessage = successMessage;
  }

  private emptySummary(): LoanClosureSummary {
    return {
      totalRequests: 0,
      pendingRequests: 0,
      settlementCalculatedRequests: 0,
      reconciliationPendingRequests: 0,
      reconciledRequests: 0,
      approvedRequests: 0,
      closedRequests: 0,
      rejectedRequests: 0,
      onHoldRequests: 0,
      matchedReconciliations: 0,
      mismatchedReconciliations: 0,
      pendingReconciliations: 0,
      totalSettlementAmount: '0.00',
      totalOutstandingPrincipal: '0.00'
    };
  }

  private emptyPage(): LoanClosurePageResponse {
    return {
      content: [],
      number: 0,
      size: this.pageSize,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true
    };
  }
}
