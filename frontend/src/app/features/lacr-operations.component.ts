import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FailedEventItem, OutboxHealth } from '../lacr.models';

@Component({
  selector: 'app-lacr-operations',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="panel animated-panel">
      <div class="section-head compact">
        <h2>Operator recovery and health</h2>
        <span class="chip">Recovery controls</span>
      </div>

      <div class="operations-toolbar">
        <div class="hero-status" [class.hero-status--error]="((outboxHealth?.staleProcessingCount ?? 0) > 0)">
          {{ operationsLoading ? 'Refreshing outbox and failed-event state...' : (actionMessage || 'Operator recovery state ready.') }}
        </div>
        <div class="chip-row">
          <button type="button" class="chip active" [disabled]="operationsLoading" (click)="loadOperations.emit()">
            {{ operationsLoading ? 'Refreshing...' : 'Refresh ops state' }}
          </button>
          <button type="button" class="chip" [disabled]="recoveryBusy || (outboxHealth?.staleProcessingCount ?? 0) === 0" (click)="recoverStaleOutbox.emit()">
            {{ recoveryBusy ? 'Recovering...' : 'Recover stale outbox' }}
          </button>
        </div>
      </div>

      <div class="operations-grid">
        <article class="ops-card">
          <div class="ops-card-head">
            <h3>Outbox health</h3>
            <span class="chip">Reclaim after {{ outboxHealth?.reclaimAfter || 'PT15M' }}</span>
          </div>
          <div class="ops-metrics">
            <div><span>Pending</span><strong>{{ outboxHealth?.pendingCount ?? 0 }}</strong></div>
            <div><span>Processing</span><strong>{{ outboxHealth?.processingCount ?? 0 }}</strong></div>
            <div><span>Published</span><strong>{{ outboxHealth?.publishedCount ?? 0 }}</strong></div>
            <div><span>Failed</span><strong>{{ outboxHealth?.failedCount ?? 0 }}</strong></div>
            <div><span>Stale processing</span><strong>{{ outboxHealth?.staleProcessingCount ?? 0 }}</strong></div>
            <div><span>Newest publish</span><strong>{{ outboxHealth?.newestPublishedAt ? (outboxHealth?.newestPublishedAt | date:'short') : 'None yet' }}</strong></div>
          </div>
          <div class="compact-note">
            <div class="compact-note-title">Recovery posture</div>
            <ul>
              <li>Processing rows are reclaimed after the stale window.</li>
              <li>Republish runs through the same outbox contract.</li>
              <li>Recovery stays visible to operators instead of being a hidden background task.</li>
            </ul>
          </div>
        </article>

        <article class="ops-card">
          <div class="ops-card-head">
            <h3>Failed events</h3>
            <span class="chip">{{ failedEvents.length }} records</span>
          </div>

          <div class="failed-event-list" *ngIf="failedEvents.length; else emptyFailedEvents">
            <div class="failed-event-item" *ngFor="let failed of failedEvents; trackBy: trackByIndex">
              <div class="failed-event-top">
                <strong>{{ failed.requestId }}</strong>
                <span>{{ failed.failedAt | date:'short' }}</span>
              </div>
              <div class="failed-event-meta">
                <span>{{ failed.loanAccountNumber || 'No loan account' }}</span>
                <span>{{ failed.failedStage || 'UNKNOWN_STAGE' }}</span>
              </div>
              <div class="failed-event-reason">{{ failed.failureReason || 'Failure reason not recorded.' }}</div>
              <div class="failed-event-meta">
                <span>Attempts: {{ failed.attemptCount }}</span>
                <span>{{ failed.createdBy || 'SYSTEM' }}</span>
              </div>
            </div>
          </div>

          <ng-template #emptyFailedEvents>
            <div class="empty-state">No failed events are currently queued for operator review.</div>
          </ng-template>
        </article>
      </div>
    </section>
  `
})
export class LacrOperationsComponent {
  @Input() outboxHealth: OutboxHealth | null = null;
  @Input({ required: true }) failedEvents!: FailedEventItem[];
  @Input() actionMessage = '';
  @Input() operationsLoading = false;
  @Input() recoveryBusy = false;

  @Output() loadOperations = new EventEmitter<void>();
  @Output() recoverStaleOutbox = new EventEmitter<void>();

  trackByIndex(index: number): number {
    return index;
  }
}
