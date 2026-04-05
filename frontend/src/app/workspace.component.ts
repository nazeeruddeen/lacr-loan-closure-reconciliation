import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSessionService } from './auth-session.service';
import { LacrActionsComponent } from './features/lacr-actions.component';
import { LacrAuditComponent } from './features/lacr-audit.component';
import { LacrClosuresComponent } from './features/lacr-closures.component';
import { LacrDashboardComponent } from './features/lacr-dashboard.component';
import { LacrOperationsComponent } from './features/lacr-operations.component';
import {
  buildLacrCreateForm,
  buildLacrEventForm,
  buildLacrLoginForm,
  buildLacrReconcileForm,
  buildLacrSearchForm,
  buildLacrSettlementForm,
  buildLacrStatusForm
} from './lacr-workspace.forms';
import {
  AdvanceClosureStatusRequest,
  ApiErrorResponse,
  CalculateSettlementRequest,
  ClosureSearchFilters,
  CreateLoanClosureRequest,
  FailedEventItem,
  LoanClosureEventItem,
  LoanClosureItem,
  LoanClosurePageResponse,
  LoanClosureSummary,
  OperatorCredentials,
  OperatorProfile,
  OutboxHealth,
  OutboxRecoveryResult,
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

type AppTab = 'dashboard' | 'closures' | 'actions' | 'audit' | 'operations';

@Component({
  selector: 'app-lacr-workspace',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LacrActionsComponent, LacrAuditComponent, LacrClosuresComponent, LacrDashboardComponent, LacrOperationsComponent],
  templateUrl: './workspace.component.html',
  styleUrl: './workspace.component.scss'
})
export class LacrWorkspaceComponent implements OnInit {
  readonly stageCatalog = [
    { title: 'Requested', note: 'Fresh intake waiting for settlement calculation', state: 'open' as const },
    { title: 'Settlement Calculated', note: 'Charges and waivers finalized', state: 'active' as const },
    { title: 'Reconciliation Pending', note: 'Awaiting bank confirmation', state: 'active' as const },
    { title: 'Approved', note: 'Ready for final account closure', state: 'done' as const },
    { title: 'Closed', note: 'Operationally complete', state: 'done' as const }
  ];

  readonly statusActions: ActionOption[] = [
    { value: 'APPROVED', label: 'Approve closure' },
    { value: 'CLOSED', label: 'Close account' },
    { value: 'REJECTED', label: 'Reject request' },
    { value: 'ON_HOLD', label: 'Place on hold' }
  ];

  readonly operatorRoles = [
    { username: 'closureops', label: 'Closure Ops' },
    { username: 'reconlead', label: 'Recon Lead' },
    { username: 'auditor', label: 'Audit Analyst' },
    { username: 'opsadmin', label: 'Ops Admin' }
  ];

  operatorProfile: OperatorProfile | null = null;
  authenticated = false;
  initializing = true;
  loginBusy = false;
  loading = false;
  operationsLoading = false;
  recoveryBusy = false;
  heroAction: 'refresh' | 'summary' | 'events' | 'closures' | null = null;
  busyClosureId: number | null = null;
  busyAction: WorkflowAction | null = null;
  lastRefreshed = '';
  actionMessage = 'Sign in to access closure operations.';
  errorMessage = '';
  activeTab: AppTab = 'dashboard';
  pageSize = 6;

  summary: LoanClosureSummary = this.emptySummary();
  closurePage: LoanClosurePageResponse = this.emptyPage();
  closures: LoanClosureItem[] = [];
  events: LoanClosureEventItem[] = [];
  failedEvents: FailedEventItem[] = [];
  outboxHealth: OutboxHealth | null = null;
  selectedClosure: LoanClosureItem | null = null;
  stages: StageCard[] = [];
  private routeSelectedClosureId: number | null = null;

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
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    this.loginForm = buildLacrLoginForm(this.fb);
    this.searchForm = buildLacrSearchForm(this.fb);
    this.createForm = buildLacrCreateForm(this.fb);
    this.settlementForm = buildLacrSettlementForm(this.fb);
    this.reconcileForm = buildLacrReconcileForm(this.fb);
    this.statusForm = buildLacrStatusForm(this.fb);
    this.eventForm = buildLacrEventForm(this.fb);

    this.stages = this.stageCatalog.map((stage) => ({ ...stage, count: 0 }));
  }

  ngOnInit(): void {
    this.route.data.subscribe((data) => {
      this.activeTab = (data['tab'] as AppTab | undefined) ?? 'dashboard';
    });
    this.route.queryParamMap.subscribe((params) => {
      const selectedClosureId = Number(params.get('selectedClosureId'));
      this.routeSelectedClosureId = Number.isFinite(selectedClosureId) && selectedClosureId > 0
        ? selectedClosureId
        : null;
    });
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

  setTab(tab: AppTab): void {
    const path: Record<AppTab, string> = {
      dashboard: '/dashboard',
      closures: '/closures',
      actions: '/actions',
      audit: '/audit',
      operations: '/operations'
    };
    void this.router.navigate([path[tab]], { queryParams: this.selectionQueryParams() });
  }

  useQuickCredentials(username: string): void {
    this.loginForm.patchValue({ username });
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
          this.setTab('dashboard');
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
    this.failedEvents = [];
    this.outboxHealth = null;
    this.summary = this.emptySummary();
    this.closurePage = this.emptyPage();
    this.operationsLoading = false;
    this.recoveryBusy = false;
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
    this.loadOperations();
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
            const preferred = this.routeSelectedClosureId
              ? this.closures.find((item) => item.id === this.routeSelectedClosureId) ?? this.closures[0]
              : this.closures[0];
            this.selectClosure(preferred);
          } else if (!this.selectedClosure && this.routeSelectedClosureId) {
            this.api.get(this.routeSelectedClosureId).subscribe({
              next: (closure) => this.selectClosure(closure),
              error: () => {
                this.routeSelectedClosureId = null;
                this.syncSelectionQueryParams();
              }
            });
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

  loadOperations(): void {
    if (!this.authenticated) {
      return;
    }
    this.operationsLoading = true;
    this.api.outboxHealth().pipe(finalize(() => (this.operationsLoading = false))).subscribe({
      next: (health) => {
        this.outboxHealth = health;
        this.actionMessage = this.composeOpsMessage(health);
        this.errorMessage = '';
      },
      error: (error) => this.handleApiError(error, 'Unable to load outbox health.')
    });

    this.api.failedEvents().subscribe({
      next: (events) => {
        this.failedEvents = events;
      },
      error: (error) => this.handleApiError(error, 'Unable to load failed events.')
    });
  }

  recoverStaleOutbox(): void {
    if (this.recoveryBusy) {
      return;
    }
    this.recoveryBusy = true;
    this.errorMessage = '';
    this.actionMessage = 'Reclaiming stale outbox rows and republishing...';
    this.api
      .recoverOutbox()
      .pipe(finalize(() => (this.recoveryBusy = false)))
      .subscribe({
        next: (result: OutboxRecoveryResult) => {
          this.actionMessage = `Recovered ${result.recoveredCount} stale event(s) and republished ${result.republishedCount}.`;
          this.loadOperations();
          this.loadEvents();
        },
        error: (error) => this.handleApiError(error, 'Unable to recover stale outbox events.')
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
    this.routeSelectedClosureId = closure.id;
    this.syncSelectionQueryParams();
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
    let closureStatus = '';
    switch (normalized) {
      case 'REQUESTED':
        closureStatus = 'REQUESTED';
        break;
      case 'SETTLEMENT_CALCULATED':
        closureStatus = 'SETTLEMENT_CALCULATED';
        break;
      case 'RECONCILIATION_PENDING':
        closureStatus = 'RECONCILIATION_PENDING';
        break;
      case 'APPROVED':
        closureStatus = 'APPROVED';
        break;
      case 'CLOSED':
        closureStatus = 'CLOSED';
        break;
      default:
        closureStatus = '';
        break;
    }
    this.searchForm.patchValue({ closureStatus });
    this.setTab('closures');
    this.runSearch();
  }

  private refreshAfterWrite(): void {
    this.loadSummary();
    this.loadClosures(0);
    this.loadEvents();
    this.loadOperations();
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
      { ...this.stageCatalog[3], count: summary.approvedRequests },
      { ...this.stageCatalog[4], count: summary.closedRequests }
    ];
  }

  private composeOpsMessage(health: OutboxHealth): string {
    const parts = [
      `${health.pendingCount} pending`,
      `${health.processingCount} processing`,
      `${health.staleProcessingCount} stale`,
      `${health.failedCount} failed`
    ];
    return `Outbox: ${parts.join(' · ')}`;
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

  private syncSelectionQueryParams(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.selectionQueryParams(),
      replaceUrl: true
    });
  }

  private selectionQueryParams(): Record<string, number> | {} {
    return this.routeSelectedClosureId ? { selectedClosureId: this.routeSelectedClosureId } : {};
  }
}


