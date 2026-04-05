import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { LoanClosureItem, LoanClosurePageResponse } from '../lacr.models';

@Component({
  selector: 'app-lacr-closures',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="panel queue-panel animated-panel">
      <div class="section-head compact">
        <h2>Closure queue</h2>
        <span class="chip">Live search</span>
      </div>

      <form class="filters" [formGroup]="searchForm" data-testid="lacr-search-form">
        <label><span>Loan account</span><input formControlName="loanAccountNumber" placeholder="LN-1001" /></label>
        <label><span>Borrower</span><input formControlName="borrowerName" placeholder="Borrower name" /></label>
        <label>
          <span>Closure status</span>
          <select formControlName="closureStatus">
            <option value="">Any</option>
            <option value="REQUESTED">Requested</option>
            <option value="SETTLEMENT_CALCULATED">Settlement Calculated</option>
            <option value="RECONCILIATION_PENDING">Reconciliation Pending</option>
            <option value="RECONCILED">Reconciled</option>
            <option value="APPROVED">Approved</option>
            <option value="CLOSED">Closed</option>
            <option value="REJECTED">Rejected</option>
            <option value="ON_HOLD">On Hold</option>
          </select>
        </label>
        <label>
          <span>Reconciliation status</span>
          <select formControlName="reconciliationStatus">
            <option value="">Any</option>
            <option value="PENDING">Pending</option>
            <option value="MATCHED">Matched</option>
            <option value="MISMATCHED">Mismatched</option>
          </select>
        </label>
        <label><span>Min settlement</span><input type="number" formControlName="minSettlementAmount" min="0" step="0.01" placeholder="100000" /></label>
        <label><span>Max settlement</span><input type="number" formControlName="maxSettlementAmount" min="0" step="0.01" placeholder="150000" /></label>
      </form>

      <div class="chip-row">
        <button type="button" class="chip active" (click)="runSearch.emit()" data-testid="lacr-run-search">Run search</button>
        <button type="button" class="chip" (click)="exportClosures.emit()">Export CSV</button>
        <span class="chip">Page {{ (closurePage.number || 0) + 1 }} / {{ closurePage.totalPages || 1 }}</span>
        <button type="button" class="chip" [disabled]="closurePage.first" (click)="previousPage.emit()">Prev</button>
        <button type="button" class="chip" [disabled]="closurePage.last" (click)="nextPage.emit()">Next</button>
      </div>

      <div class="closure-table" *ngIf="closures.length; else emptyClosures">
        <div class="table-row table-head">
          <div>Request</div>
          <div>Borrower</div>
          <div>Settlement</div>
          <div>Status</div>
          <div>Actions</div>
        </div>

        <div class="table-row" *ngFor="let closure of closures; trackBy: trackByIndex" [attr.data-testid]="'lacr-closure-row-' + closure.id">
          <div class="table-cell primary">
            <strong>{{ closure.requestId }}</strong>
            <span>{{ closure.loanAccountNumber }}</span>
          </div>
          <div class="table-cell">
            <strong>{{ closure.borrowerName }}</strong>
            <span>{{ closure.closureReason }}</span>
          </div>
          <div class="table-cell numeric">
            <strong>{{ closure.settlementAmount }}</strong>
            <span>Difference {{ closure.settlementDifference || '0.00' }}</span>
          </div>
          <div class="table-cell">
            <span class="status" [class.risk]="statusTone(closure.closureStatus) === 'risk'">{{ closure.closureStatus }}</span>
            <span class="status status-secondary">{{ closure.reconciliationStatus }}</span>
          </div>
          <div class="table-cell actions">
            <button type="button" class="ghost" (click)="open.emit(closure)" [attr.data-testid]="'lacr-open-' + closure.id">Open</button>
            <button type="button" class="ghost" [disabled]="!canCalculateSettlement(closure) || isBusy(closure.id, 'settle')" (click)="calculateSettlement.emit(closure)">
              {{ isBusy(closure.id, 'settle') ? 'Working...' : 'Settle' }}
            </button>
            <button type="button" class="ghost" [disabled]="!canStartReconciliation(closure) || isBusy(closure.id, 'recon-start')" (click)="moveToReconciliation.emit(closure)">
              {{ isBusy(closure.id, 'recon-start') ? 'Working...' : 'Start recon' }}
            </button>
            <button type="button" class="ghost" [disabled]="!canReconcile(closure) || isBusy(closure.id, 'reconcile')" (click)="reconcile.emit(closure)">
              {{ isBusy(closure.id, 'reconcile') ? 'Working...' : 'Reconcile' }}
            </button>
            <button type="button" [disabled]="!canAdvance(closure) || isBusy(closure.id, 'advance')" (click)="advance.emit(closure)">
              {{ isBusy(closure.id, 'advance') ? 'Working...' : recommendedActionLabel(closure) }}
            </button>
          </div>
        </div>
      </div>

      <ng-template #emptyClosures>
        <div class="empty-state">No closure requests matched the current search filters.</div>
      </ng-template>
    </section>
  `
})
export class LacrClosuresComponent {
  @Input({ required: true }) searchForm!: FormGroup;
  @Input({ required: true }) closurePage!: LoanClosurePageResponse;
  @Input({ required: true }) closures!: LoanClosureItem[];
  @Input({ required: true }) loading = false;
  @Input() busyClosureId: number | null = null;
  @Input() busyAction: 'settle' | 'recon-start' | 'reconcile' | 'advance' | null = null;
  @Input({ required: true }) statusTone!: (status: string) => string;
  @Input({ required: true }) canCalculateSettlement!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) canStartReconciliation!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) canReconcile!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) canAdvance!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) recommendedActionLabel!: (closure: LoanClosureItem) => string;

  @Output() runSearch = new EventEmitter<void>();
  @Output() exportClosures = new EventEmitter<void>();
  @Output() previousPage = new EventEmitter<void>();
  @Output() nextPage = new EventEmitter<void>();
  @Output() open = new EventEmitter<LoanClosureItem>();
  @Output() calculateSettlement = new EventEmitter<LoanClosureItem>();
  @Output() moveToReconciliation = new EventEmitter<LoanClosureItem>();
  @Output() reconcile = new EventEmitter<LoanClosureItem>();
  @Output() advance = new EventEmitter<LoanClosureItem>();

  trackByIndex(index: number): number {
    return index;
  }

  isBusy(closureId: number, action: 'settle' | 'recon-start' | 'reconcile' | 'advance'): boolean {
    return this.loading && this.busyClosureId === closureId && this.busyAction === action;
  }
}




