import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthSessionService } from './auth-session.service';
import {
  AdvanceClosureStatusRequest,
  ApiErrorResponse,
  CalculateSettlementRequest,
  ClosureSearchFilters,
  CreateLoanClosureRequest,
  LoanClosureEventItem,
  LoanClosureItem,
  LoanClosurePageResponse,
  LoanClosureSummary,
  OperatorCredentials,
  OperatorProfile,
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

type WorkflowAction = 'settle' | 'recon-start' | 'reconcile' | 'advance';

type ActionOption = {
  value: string;
  label: string;
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  readonly stageCatalog = [
    { title: 'Requested', note: 'Fresh intake waiting for settlement calculation', state: 'open' as const },
    { title: 'Settlement Calculated', note: 'Charges and waivers finalized', state: 'active' as const },
    { title: 'Reconciliation Pending', note: 'Awaiting bank confirmation', state: 'active' as const },
    { title: 'Approved / Closed', note: 'Operationally complete', state: 'done' as const }
  ];

  readonly statusActions: ActionOption[] = [
    { value: 'APPROVED', label: 'Approve closure' },
    { value: 'CLOSED', label: 'Close account' },
    { value: 'REJECTED', label: 'Reject request' },
    { value: 'ON_HOLD', label: 'Place on hold' }
  ];

  readonly quickCredentials = [
    { username: 'closureops', password: 'Closure@123', label: 'Closure Ops' },
    { username: 'reconlead', password: 'Recon@123', label: 'Recon Lead' },
    { username: 'auditor', password: 'Auditor@123', label: 'Audit Analyst' },
    { username: 'opsadmin', password: 'Ops@123', label: 'Ops Admin' }
  ];

  operatorProfile: OperatorProfile | null = null;
  authenticated = false;
  initializing = true;
  loginBusy = false;
  loading = false;
  heroAction: 'refresh' | 'summary' | 'events' | 'closures' | null = null;
  busyClosureId: number | null = null;
  busyAction: WorkflowAction | null = null;
  lastRefreshed = '';
  actionMessage = 'Sign in to access closure operations.';
  errorMessage = '';
  activeTab = 'dashboard';
  pageSize = 6;

  summary: LoanClosureSummary = this.emptySummary();
  closurePage: LoanClosurePageResponse = this.emptyPage();
  closures: LoanClosureItem[] = [];
  events: LoanClosureEventItem[] = [];
  selectedClosure: LoanClosureItem | null = null;
  stages: StageCard[] = [];

  readonly loginForm: FormGroup;
  readonly searchForm: FormGroup;
  readonly createForm: FormGroup;
  readonly settlementForm: FormGroup;
  readonly reconcileForm: FormGroup;
  readonly statusForm: FormGroup;
  readonly eventForm: FormGroup;

  constructor(
    private readonly api: LacrApiService,
    private readonly authSession: AuthSessionService,
    private readonly fb: FormBuilder
  ) {
    this.loginForm = this.fb.group({
      username: ['closureops', [Validators.required]],
      password: ['Closure@123', [Validators.required]]
    });

    this.searchForm = this.fb.group({
      loanAccountNumber: [''],
      borrowerName: [''],
      closureStatus: [''],
      reconciliationStatus: [''],
      minSettlementAmount: [''],
      maxSettlementAmount: ['']
    });

    this.createForm = this.fb.group({
      requestId: ['', [Validators.required]],
      loanAccountNumber: ['', [Validators.required]],
      borrowerName: ['', [Validators.required]],
      closureReason: ['', [Validators.required]],
      outstandingPrincipal: ['', [Validators.required, Validators.min(0)]],
      accruedInterest: ['0.00', [Validators.required, Validators.min(0)]],
      penaltyAmount: ['0.00', [Validators.required, Validators.min(0)]],
      processingFee: ['0.00', [Validators.required, Validators.min(0)]],
      remarks: ['']
    });

    this.settlementForm = this.fb.group({
      adjustmentAmount: ['0.00', [Validators.required, Validators.min(0)]],
      remarks: ['']
    });

    this.reconcileForm = this.fb.group({
      bankConfirmedAmount: ['0.00', [Validators.required, Validators.min(0)]],
      remarks: ['']
    });

    this.statusForm = this.fb.group({
      targetStatus: ['APPROVED', [Validators.required]],
      remarks: ['']
    });

    this.eventForm = this.fb.group({
      requestId: [''],
      loanAccountNumber: [''],
      eventType: [''],
      text: ['']
    });

    this.stages = this.stageCatalog.map((stage) => ({ ...stage, count: 0 }));
  }

  ngOnInit(): void {
    const profile = this.authSession.restoreProfile();
    if (profile && this.authSession.hasSession()) {
      this.operatorProfile = profile;
      this.authenticated = true;
      this.actionMessage = `Welcome back, ${profile.displayName}.`;
      this.refreshAll();
      return;
    }
    this.initializing = false;
  }

  setTab(tab: string): void {
    this.activeTab = tab;
  }

  useQuickCredentials(username: string, password: string): void {
    this.loginForm.patchValue({ username, password });
  }

  login(): void {
    if (this.loginForm.invalid) {
      this.errorMessage = 'Enter a valid operator username and password.';
      return;
    }
    this.loginBusy = true;
    this.errorMessage = '';
    this.actionMessage = 'Authenticating operator session...';
    const credentials = this.loginForm.getRawValue() as OperatorCredentials;
    this.authSession
      .login(credentials)
      .pipe(finalize(() => (this.loginBusy = false)))
      .subscribe({
        next: (profile) => {
          this.operatorProfile = profile;
          this.authenticated = true;
          this.activeTab = 'dashboard';
          this.actionMessage = `Signed in as ${profile.displayName}.`; 
          this.refreshAll();
        },
        error: (error) => {
          this.authenticated = false;
          this.operatorProfile = null;
          this.errorMessage = this.extractErrorMessage(error, 'Authentication failed.');
          this.actionMessage = 'Sign in failed. Check the credentials and try again.';
          this.initializing = false;
        }
      });
  }

  logout(): void {
    this.authSession.clear();
    this.operatorProfile = null;
    this.authenticated = false;
    this.selectedClosure = null;
    this.closures = [];
    this.events = [];
    this.summary = this.emptySummary();
    this.closurePage = this.emptyPage();
    this.stages = this.stageCatalog.map((stage) => ({ ...stage, count: 0 }));
    this.errorMessage = '';
    this.actionMessage = 'Signed out. Sign in to continue.';
    this.initializing = false;
  }

  refreshAll(): void {
    if (!this.authenticated) {
      return;
    }
    this.loading = true;
    this.heroAction = 'refresh';
    this.errorMessage = '';
    this.actionMessage = 'Refreshing summary, queue, and audit stream...';
    this.loadSummary();
    this.loadClosures(0);
    this.loadEvents();
  }

  loadSummary(): void {
    this.api.summary().subscribe({
      next: (summary) => {
        this.summary = summary;
        this.syncStages(summary);
        this.lastRefreshed = new Date().toLocaleString();
        this.actionMessage = `Dashboard refreshed at ${this.lastRefreshed}`;
        this.heroAction = null;
      },
      error: (error) => {
        this.heroAction = null;
        this.loading = false;
        this.handleApiError(error, 'Unable to load the LACR summary.');
      }
    });
  }

  loadClosures(page = this.closurePage.number || 0): void {
    const filters = this.normalizeClosureFilters(this.searchForm.value);
    this.api
      .search(filters, page, this.pageSize)
      .pipe(finalize(() => {
        this.loading = false;
        this.initializing = false;
      }))
      .subscribe({
        next: (pageResponse) => {
          this.closurePage = pageResponse;
          this.closures = pageResponse.content ?? [];
          if (this.selectedClosure) {
            const refreshed = this.closures.find((item) => item.id === this.selectedClosure?.id);
            if (refreshed) {
              this.selectedClosure = refreshed;
              this.patchActionForms(refreshed);
            }
          }
          if (!this.selectedClosure && this.closures.length) {
            this.selectClosure(this.closures[0]);
          }
        },
        error: (error) => this.handleApiError(error, 'Unable to load the closure queue.')
      });
  }

  loadEvents(): void {
    const filters = this.normalizeEventFilters(this.eventForm.value);
    this.api.events(filters).subscribe({
      next: (events) => {
        this.events = events;
      },
      error: (error) => this.handleApiError(error, 'Unable to load audit events.')
    });
  }

  runSearch(): void {
    this.loading = true;
    this.errorMessage = '';
    this.actionMessage = 'Running closure search...';
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
    if (this.createForm.invalid) {
      this.errorMessage = 'Complete the intake form before creating a closure request.';
      return;
    }
    const request = this.createForm.getRawValue() as CreateLoanClosureRequest;
    this.loading = true;
    this.errorMessage = '';
    this.actionMessage = 'Creating closure request...';
    this.api
      .create(request)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (closure) => {
          this.selectedClosure = closure;
          this.patchActionForms(closure);
          this.createForm.patchValue({ remarks: '' });
          this.actionMessage = `Created closure ${closure.requestId}.`;
          this.refreshAfterWrite();
        },
        error: (error) => this.handleApiError(error, 'Unable to create the closure request.')
      });
  }

  selectClosure(closure: LoanClosureItem): void {
    this.selectedClosure = closure;
    this.patchActionForms(closure);
    this.errorMessage = '';
    this.actionMessage = `Selected ${closure.requestId} for workflow actions.`;
  }

  calculateSettlementFor(closure: LoanClosureItem): void {
    if (!this.canCalculateSettlement(closure)) {
      return;
    }
    this.runClosureAction(closure, 'settle', () => this.calculateSettlement());
  }

  moveToReconciliationFor(closure: LoanClosureItem): void {
    if (!this.canStartReconciliation(closure)) {
      return;
    }
    this.runClosureAction(closure, 'recon-start', () => this.moveToReconciliation());
  }

  reconcileFor(closure: LoanClosureItem): void {
    if (!this.canReconcile(closure)) {
      return;
    }
    this.runClosureAction(closure, 'reconcile', () => this.reconcile());
  }

  advanceStatusFor(closure: LoanClosureItem): void {
    if (!this.canAdvance(closure)) {
      return;
    }
    this.runClosureAction(closure, 'advance', () => this.advanceStatus());
  }

  calculateSettlement(): void {
    if (!this.selectedClosure || this.settlementForm.invalid) {
      return;
    }
    const request = this.settlementForm.getRawValue() as CalculateSettlementRequest;
    this.loading = true;
    this.errorMessage = '';
    this.actionMessage = `Calculating settlement for ${this.selectedClosure.requestId}...`;
    this.api
      .calculateSettlement(this.selectedClosure.id, request)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (closure) => this.onWorkflowSuccess(closure, 'Settlement recalculated successfully.'),
        error: (error) => this.onWorkflowError(error, 'Unable to calculate settlement.')
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
    this.errorMessage = '';
    this.actionMessage = `Starting reconciliation for ${this.selectedClosure.requestId}...`;
    this.api
      .moveToReconciliation(this.selectedClosure.id, request)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (closure) => this.onWorkflowSuccess(closure, 'Closure moved to reconciliation.'),
        error: (error) => this.onWorkflowError(error, 'Unable to start reconciliation.')
      });
  }

  reconcile(): void {
    if (!this.selectedClosure || this.reconcileForm.invalid) {
      return;
    }
    const request = this.reconcileForm.getRawValue() as ReconcileClosureRequest;
    this.loading = true;
    this.errorMessage = '';
    this.actionMessage = `Reconciling ${this.selectedClosure.requestId} against bank confirmation...`;
    this.api
      .reconcile(this.selectedClosure.id, request)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (closure) => this.onWorkflowSuccess(closure, 'Reconciliation completed.'),
        error: (error) => this.onWorkflowError(error, 'Unable to reconcile the closure.')
      });
  }

  advanceStatus(): void {
    if (!this.selectedClosure || this.statusForm.invalid || !this.canAdvance(this.selectedClosure)) {
      return;
    }
    const request = this.statusForm.getRawValue() as AdvanceClosureStatusRequest;
    this.loading = true;
    this.errorMessage = '';
    this.actionMessage = `Applying ${request.targetStatus} to ${this.selectedClosure.requestId}...`;
    this.api
      .advanceStatus(this.selectedClosure.id, request)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (closure) => this.onWorkflowSuccess(closure, `Status updated to ${closure.closureStatus}.`),
        error: (error) => this.onWorkflowError(error, 'Unable to advance the closure status.')
      });
  }

  exportSummaryCsv(): void {
    this.heroAction = 'summary';
    this.errorMessage = '';
    this.actionMessage = 'Preparing summary CSV...';
    this.api.summaryCsv().subscribe({
      next: (csv) => this.download('closure-summary.csv', csv, 'Summary CSV downloaded.'),
      error: (error) => {
        this.heroAction = null;
        this.handleApiError(error, 'Unable to export summary CSV.');
      }
    });
  }

  exportEventsCsv(): void {
    const filters = this.normalizeEventFilters(this.eventForm.value);
    this.heroAction = 'events';
    this.errorMessage = '';
    this.actionMessage = 'Preparing events CSV...';
    this.api.eventsCsv(filters).subscribe({
      next: (csv) => this.download('closure-events.csv', csv, 'Events CSV downloaded.'),
      error: (error) => {
        this.heroAction = null;
        this.handleApiError(error, 'Unable to export audit events CSV.');
      }
    });
  }

  exportClosuresCsv(): void {
    const filters = this.normalizeClosureFilters(this.searchForm.value);
    this.heroAction = 'closures';
    this.errorMessage = '';
    this.actionMessage = 'Preparing closure queue CSV...';
    this.api.closuresCsv(filters).subscribe({
      next: (csv) => this.download('closure-search.csv', csv, 'Filtered closure queue exported.'),
      error: (error) => {
        this.heroAction = null;
        this.handleApiError(error, 'Unable to export closure queue CSV.');
      }
    });
  }

  closureTone(status: string): string {
    if (status === 'ON_HOLD' || status === 'REJECTED' || status === 'MISMATCHED') {
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

  canCalculateSettlement(closure: LoanClosureItem): boolean {
    return closure.closureStatus === 'REQUESTED' || closure.closureStatus === 'ON_HOLD';
  }

  canStartReconciliation(closure: LoanClosureItem): boolean {
    return closure.closureStatus === 'SETTLEMENT_CALCULATED';
  }

  canReconcile(closure: LoanClosureItem): boolean {
    return closure.closureStatus === 'RECONCILIATION_PENDING';
  }

  canAdvance(closure: LoanClosureItem): boolean {
    return this.availableStatusOptions(closure).length > 0;
  }

  availableStatusOptions(closure: LoanClosureItem | null): ActionOption[] {
    if (!closure) {
      return [];
    }
    switch (closure.closureStatus) {
      case 'REQUESTED':
        return [{ value: 'REJECTED', label: 'Reject request' }];
      case 'SETTLEMENT_CALCULATED':
        return [{ value: 'REJECTED', label: 'Reject request' }];
      case 'RECONCILIATION_PENDING':
        return [{ value: 'ON_HOLD', label: 'Place on hold' }, { value: 'REJECTED', label: 'Reject request' }];
      case 'RECONCILED':
        return [{ value: 'APPROVED', label: 'Approve closure' }, { value: 'REJECTED', label: 'Reject request' }];
      case 'APPROVED':
        return [{ value: 'CLOSED', label: 'Close account' }];
      case 'ON_HOLD':
        return [{ value: 'REJECTED', label: 'Reject request' }];
      default:
        return [];
    }
  }

  recommendedActionLabel(closure: LoanClosureItem): string {
    if (this.canCalculateSettlement(closure)) {
      return 'Settle';
    }
    if (this.canStartReconciliation(closure)) {
      return 'Start recon';
    }
    if (this.canReconcile(closure)) {
      return 'Reconcile';
    }
    if (this.canAdvance(closure)) {
      return 'Advance';
    }
    return 'View';
  }

  stageFilter(stageTitle: string): void {
    const normalized = stageTitle.toUpperCase().replace(/\s+\/\s+/g, '_').replace(/\s+/g, '_');
    const closureStatus =
      normalized === 'APPROVED_CLOSED'
        ? ''
        : normalized === 'REQUESTED'
          ? 'REQUESTED'
          : normalized === 'SETTLEMENT_CALCULATED'
            ? 'SETTLEMENT_CALCULATED'
            : 'RECONCILIATION_PENDING';
    this.searchForm.patchValue({ closureStatus });
    this.activeTab = 'closures';
    this.runSearch();
  }

  private refreshAfterWrite(): void {
    this.loadSummary();
    this.loadClosures(0);
    this.loadEvents();
  }

  private onWorkflowSuccess(closure: LoanClosureItem, message: string): void {
    this.selectedClosure = closure;
    this.patchActionForms(closure);
    this.clearBusy(message);
    this.refreshAfterWrite();
  }

  private onWorkflowError(error: unknown, fallback: string): void {
    this.clearBusy();
    this.handleApiError(error, fallback);
  }

  private clearBusy(message = 'Action completed.'): void {
    this.busyClosureId = null;
    this.busyAction = null;
    this.actionMessage = message;
  }

  private runClosureAction(closure: LoanClosureItem, action: WorkflowAction, callback: () => void): void {
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
    const options = this.availableStatusOptions(closure);
    this.statusForm.patchValue({
      targetStatus: options[0]?.value ?? '',
      remarks: closure.remarks ?? ''
    });
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
      { ...this.stageCatalog[0], count: summary.pendingRequests },
      { ...this.stageCatalog[1], count: summary.settlementCalculatedRequests },
      { ...this.stageCatalog[2], count: summary.reconciliationPendingRequests },
      { ...this.stageCatalog[3], count: summary.approvedRequests + summary.closedRequests }
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
    this.heroAction = null;
    this.clearBusy(successMessage);
  }

  private handleApiError(error: unknown, fallbackMessage: string): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.authSession.clear();
      this.operatorProfile = null;
      this.authenticated = false;
      this.errorMessage = 'Your operator session expired. Sign in again.';
      this.actionMessage = 'Session expired.';
      return;
    }
    this.errorMessage = this.extractErrorMessage(error, fallbackMessage);
    this.actionMessage = this.errorMessage;
  }

  private extractErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiErrorResponse | string | null;
      if (typeof body === 'string' && body.trim()) {
        return body;
      }
      if (body && typeof body === 'object') {
        if (body.validationErrors?.length) {
          return body.validationErrors.map((item) => `${item.field}: ${item.message}`).join(' | ');
        }
        if (body.message) {
          return body.message;
        }
      }
    }
    return fallbackMessage;
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
      totalOutstandingPrincipal: '0.00',
      closureStatusCounts: {},
      reconciliationStatusCounts: {}
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
