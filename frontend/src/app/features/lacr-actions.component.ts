import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { LoanClosureItem } from '../lacr.models';

type ActionOption = {
  value: string;
  label: string;
};

@Component({
  selector: 'app-lacr-actions',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="view-container">
      <article class="panel animated-panel action-layout">
        <div class="section-head compact">
          <h2>Selected closure actions</h2>
          <span class="chip">Workflow controls</span>
        </div>

        <div class="selected-box" *ngIf="selectedClosure; else noSelection">
          <div class="result-head">
            <div>
              <div class="result-title">{{ selectedClosure.requestId }}</div>
              <div class="result-sub">{{ selectedClosure.loanAccountNumber }} - {{ selectedClosure.borrowerName }}</div>
            </div>
            <span class="status" [class.risk]="statusTone(selectedClosure.closureStatus) === 'risk'" data-testid="lacr-selected-status">{{ selectedClosure.closureStatus }}</span>
          </div>

          <div class="result-metrics">
            <div><span>Settlement</span><strong>{{ selectedClosure.settlementAmount }}</strong></div>
            <div><span>Bank confirmed</span><strong>{{ selectedClosure.bankConfirmedAmount || 'Not confirmed' }}</strong></div>
            <div><span>Difference</span><strong>{{ selectedClosure.settlementDifference || '0.00' }}</strong></div>
          </div>
        </div>

        <ng-template #noSelection>
          <div class="empty-state">Select a closure from the queue to run settlement, reconciliation, or approval actions.</div>
        </ng-template>

        <div class="workflow-actions">
          <form [formGroup]="settlementForm" class="action-form" data-testid="lacr-settlement-form">
            <label><span>Adjustment amount</span><input type="number" formControlName="adjustmentAmount" min="0" step="0.01" data-testid="lacr-adjustment-amount" /></label>
            <label><span>Remarks</span><input formControlName="remarks" placeholder="Waiver / exception note" data-testid="lacr-settlement-remarks" /></label>
            <button type="button" [disabled]="!selectedClosure || !canCalculateSettlement(selectedClosure)" (click)="calculateSettlement.emit()" data-testid="lacr-calculate-settlement">
              Calculate settlement
            </button>
          </form>

          <form [formGroup]="reconcileForm" class="action-form" data-testid="lacr-reconcile-form">
            <label><span>Bank confirmed amount</span><input type="number" formControlName="bankConfirmedAmount" min="0" step="0.01" data-testid="lacr-bank-confirmed-amount" /></label>
            <label><span>Remarks</span><input formControlName="remarks" placeholder="Bank file / recon note" data-testid="lacr-reconcile-remarks" /></label>
            <div class="button-row">
              <button type="button" class="ghost" [disabled]="!selectedClosure || !canStartReconciliation(selectedClosure)" (click)="moveToReconciliation.emit()" data-testid="lacr-start-reconciliation">
                Start reconciliation
              </button>
              <button type="button" [disabled]="!selectedClosure || !canReconcile(selectedClosure)" (click)="reconcile.emit()" data-testid="lacr-reconcile-now">
                Reconcile now
              </button>
            </div>
          </form>

          <form [formGroup]="statusForm" class="action-form" data-testid="lacr-status-form">
            <label>
              <span>Target status</span>
              <select formControlName="targetStatus" data-testid="lacr-target-status">
                <option *ngFor="let option of availableStatusOptions(selectedClosure); trackBy: trackByIndex" [value]="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label><span>Remarks</span><input formControlName="remarks" placeholder="Operational decision note" data-testid="lacr-status-remarks" /></label>
            <button type="button" class="ghost" [disabled]="!selectedClosure || !canAdvance(selectedClosure)" (click)="advanceStatus.emit()" data-testid="lacr-advance-status">
              Advance status
            </button>
          </form>
        </div>
      </article>

      <article class="panel animated-panel">
        <div class="section-head compact">
          <h2>Status history</h2>
          <span class="chip">Audit trail</span>
        </div>

        <div class="history-list" *ngIf="selectedClosure?.statusHistory?.length; else emptyHistory">
          <div class="history-item" *ngFor="let item of selectedClosure?.statusHistory; trackBy: trackByIndex">
            <div class="history-top">
              <strong>{{ item.actionName }}</strong>
              <span>{{ item.changedAt | date:'short' }}</span>
            </div>
            <div class="history-meta">
              <span>{{ item.fromStatus || 'NEW' }} -> {{ item.toStatus }}</span>
              <span>{{ item.changedBy || 'SYSTEM' }}</span>
            </div>
            <div class="history-remarks" *ngIf="item.remarks">{{ item.remarks }}</div>
          </div>
        </div>

        <ng-template #emptyHistory>
          <div class="empty-state">Select a closure with workflow activity to inspect the status history.</div>
        </ng-template>
      </article>
    </section>
  `
})
export class LacrActionsComponent {
  @Input() selectedClosure: LoanClosureItem | null = null;
  @Input({ required: true }) settlementForm!: FormGroup;
  @Input({ required: true }) reconcileForm!: FormGroup;
  @Input({ required: true }) statusForm!: FormGroup;
  @Input({ required: true }) statusTone!: (status: string) => string;
  @Input({ required: true }) canCalculateSettlement!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) canStartReconciliation!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) canReconcile!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) canAdvance!: (closure: LoanClosureItem) => boolean;
  @Input({ required: true }) availableStatusOptions!: (closure: LoanClosureItem | null) => ActionOption[];

  @Output() calculateSettlement = new EventEmitter<void>();
  @Output() moveToReconciliation = new EventEmitter<void>();
  @Output() reconcile = new EventEmitter<void>();
  @Output() advanceStatus = new EventEmitter<void>();

  trackByIndex(index: number): number {
    return index;
  }
}













