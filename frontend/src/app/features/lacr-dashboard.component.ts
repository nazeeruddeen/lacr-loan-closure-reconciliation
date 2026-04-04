import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LoanClosureSummary, FailedEventItem, OutboxHealth } from '../lacr.models';

type StageCard = {
  title: string;
  count: number;
  note: string;
  state: 'open' | 'active' | 'done' | 'risk';
};

@Component({
  selector: 'app-lacr-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="hero animated-panel">
      <div class="hero-copy">
        <div class="eyebrow">Loan Account Closure and Reconciliation</div>
        <h1>Control settlement accuracy, reconciliation quality, and audit visibility from one console.</h1>
        <p>
          Run queue operations, inspect reconciliation mismatches, and export operational data without leaving the closure desk.
        </p>

        <div class="hero-actions">
          <button type="button" [disabled]="heroAction === 'refresh' || loading" (click)="refresh.emit()">
            {{ heroAction === 'refresh' ? 'Refreshing...' : 'Refresh dashboard' }}
          </button>
          <button type="button" class="ghost" [disabled]="heroAction === 'summary'" (click)="exportSummary.emit()">
            {{ heroAction === 'summary' ? 'Downloading...' : 'Export summary' }}
          </button>
          <button type="button" class="ghost" [disabled]="heroAction === 'events'" (click)="exportEvents.emit()">
            {{ heroAction === 'events' ? 'Downloading...' : 'Export events' }}
          </button>
          <button type="button" class="ghost" [disabled]="heroAction === 'closures'" (click)="exportClosures.emit()">
            {{ heroAction === 'closures' ? 'Downloading...' : 'Export queue' }}
          </button>
        </div>

        <div class="hero-status">{{ actionMessage }}</div>
      </div>

      <div class="hero-panel">
        <div class="panel-title">Current control room</div>
        <div class="mini-grid">
          <div><span>Total requests</span><strong>{{ summary.totalRequests }}</strong></div>
          <div><span>Pending reconciliation</span><strong>{{ summary.reconciliationPendingRequests }}</strong></div>
          <div><span>Mismatched</span><strong>{{ summary.mismatchedReconciliations }}</strong></div>
          <div><span>Last refreshed</span><strong>{{ lastRefreshed || 'Waiting for first sync' }}</strong></div>
          <div><span>Outbox stale</span><strong>{{ outboxHealth?.staleProcessingCount ?? 0 }}</strong></div>
          <div><span>Failed events</span><strong>{{ failedEvents.length }}</strong></div>
          <div><span>Outbox pending</span><strong>{{ outboxHealth?.pendingCount ?? 0 }}</strong></div>
          <div><span>Outbox processing</span><strong>{{ outboxHealth?.processingCount ?? 0 }}</strong></div>
        </div>
      </div>
    </section>

    <section class="stats animated-panel">
      <article class="stat-card" *ngFor="let stage of stages; trackBy: trackByIndex" (click)="stageFilter.emit(stage.title)">
        <div class="stat-label">{{ stage.title }}</div>
        <div class="stat-value">{{ stage.count }}</div>
        <div class="stat-delta" [class.warn]="stage.state === 'risk'">{{ stage.note }}</div>
      </article>
    </section>
  `
})
export class LacrDashboardComponent {
  @Input({ required: true }) summary!: LoanClosureSummary;
  @Input({ required: true }) stages!: StageCard[];
  @Input({ required: true }) failedEvents!: FailedEventItem[];
  @Input() outboxHealth: OutboxHealth | null = null;
  @Input() lastRefreshed = '';
  @Input() actionMessage = '';
  @Input() heroAction: 'refresh' | 'summary' | 'events' | 'closures' | null = null;
  @Input() loading = false;

  @Output() refresh = new EventEmitter<void>();
  @Output() exportSummary = new EventEmitter<void>();
  @Output() exportEvents = new EventEmitter<void>();
  @Output() exportClosures = new EventEmitter<void>();
  @Output() stageFilter = new EventEmitter<string>();

  trackByIndex(index: number): number {
    return index;
  }
}
