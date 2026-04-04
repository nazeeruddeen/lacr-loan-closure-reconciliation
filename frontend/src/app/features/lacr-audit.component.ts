import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { LoanClosureEventItem } from '../lacr.models';

@Component({
  selector: 'app-lacr-audit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="panel animated-panel">
      <div class="section-head compact">
        <h2>Audit event stream</h2>
        <span class="chip">Searchable events</span>
      </div>

      <form class="filters" [formGroup]="eventForm">
        <label><span>Request ID</span><input formControlName="requestId" /></label>
        <label><span>Loan account</span><input formControlName="loanAccountNumber" /></label>
        <label><span>Event type</span><input formControlName="eventType" placeholder="RECONCILED_MATCHED" /></label>
        <label><span>Text search</span><input formControlName="text" placeholder="remarks / actor / request" /></label>
      </form>

      <div class="chip-row">
        <button type="button" class="chip active" (click)="refreshEvents.emit()">Refresh events</button>
        <button type="button" class="chip" (click)="exportEvents.emit()">Export events CSV</button>
      </div>

      <div class="event-list" *ngIf="events.length; else emptyEvents">
        <div class="event-card" *ngFor="let event of events; trackBy: trackByIndex">
          <div class="event-time">{{ event.createdAt | date:'shortTime' }}</div>
          <div class="event-body">
            <div class="event-title">{{ event.eventType }} - {{ event.loanAccountNumber }}</div>
            <div class="event-details">{{ event.details || 'Workflow transition recorded in the audit stream.' }}</div>
            <div class="event-details event-details--meta">{{ event.actor || 'SYSTEM' }} | {{ event.requestId }}</div>
            <div class="event-hash" *ngIf="event.previousHash || event.recordHash">
              <span *ngIf="event.previousHash">prev {{ event.previousHash }}</span>
              <span *ngIf="event.recordHash">hash {{ event.recordHash }}</span>
            </div>
          </div>
          <div class="event-badge">{{ event.reconciliationStatus || 'AUDIT' }}</div>
        </div>
      </div>

      <ng-template #emptyEvents>
        <div class="empty-state">No audit events matched the current event filters.</div>
      </ng-template>
    </section>
  `
})
export class LacrAuditComponent {
  @Input({ required: true }) eventForm!: FormGroup;
  @Input({ required: true }) events!: LoanClosureEventItem[];
  @Output() refreshEvents = new EventEmitter<void>();
  @Output() exportEvents = new EventEmitter<void>();

  trackByIndex(index: number): number {
    return index;
  }
}
