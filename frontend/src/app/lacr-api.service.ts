import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';
import {
  AdvanceClosureStatusRequest,
  CalculateSettlementRequest,
  ClosureSearchFilters,
  FailedEventItem,
  CreateLoanClosureRequest,
  LoanClosureEventItem,
  LoanClosureItem,
  LoanClosurePageResponse,
  LoanClosureSummary,
  OutboxHealth,
  OutboxRecoveryResult,
  ReconcileClosureRequest
} from './lacr.models';

@Injectable({ providedIn: 'root' })
export class LacrApiService {
  constructor(private readonly http: HttpClient) {}

  summary(): Observable<LoanClosureSummary> {
    return this.http.get<LoanClosureSummary>(`${environment.apiBaseUrl}/summary`);
  }

  get(id: number): Observable<LoanClosureItem> {
    return this.http.get<LoanClosureItem>(`${environment.apiBaseUrl}/${id}`);
  }

  create(request: CreateLoanClosureRequest): Observable<LoanClosureItem> {
    return this.http.post<LoanClosureItem>(`${environment.apiBaseUrl}`, request);
  }

  calculateSettlement(id: number, request: CalculateSettlementRequest): Observable<LoanClosureItem> {
    return this.http.post<LoanClosureItem>(`${environment.apiBaseUrl}/${id}/settlement`, request);
  }

  moveToReconciliation(id: number, request: AdvanceClosureStatusRequest): Observable<LoanClosureItem> {
    return this.http.post<LoanClosureItem>(`${environment.apiBaseUrl}/${id}/reconciliation`, request);
  }

  reconcile(id: number, request: ReconcileClosureRequest): Observable<LoanClosureItem> {
    return this.http.post<LoanClosureItem>(`${environment.apiBaseUrl}/${id}/reconcile`, request);
  }

  advanceStatus(id: number, request: AdvanceClosureStatusRequest): Observable<LoanClosureItem> {
    return this.http.post<LoanClosureItem>(`${environment.apiBaseUrl}/${id}/status`, request);
  }

  search(filters: ClosureSearchFilters, page = 0, size = 6): Observable<LoanClosurePageResponse> {
    const params = this.buildParams(filters as Partial<Record<string, string | number | boolean | null | undefined>>).set('page', String(page)).set('size', String(size));
    return this.http.get<LoanClosurePageResponse>(`${environment.apiBaseUrl}/search`, { params });
  }

  events(filters: { requestId?: string; loanAccountNumber?: string; eventType?: string; text?: string } = {}): Observable<LoanClosureEventItem[]> {
    const params = this.buildParams(filters);
    return this.http.get<LoanClosureEventItem[]>(`${environment.apiBaseUrl}/reports/events`, { params });
  }

  outboxHealth(): Observable<OutboxHealth> {
    return this.http.get<OutboxHealth>(`${environment.opsApiBaseUrl}/outbox/health`);
  }

  recoverOutbox(): Observable<OutboxRecoveryResult> {
    return this.http.post<OutboxRecoveryResult>(`${environment.opsApiBaseUrl}/outbox/recover`, {});
  }

  failedEvents(filters: { requestId?: string; loanAccountNumber?: string } = {}): Observable<FailedEventItem[]> {
    const params = this.buildParams(filters);
    return this.http.get<FailedEventItem[]>(`${environment.opsApiBaseUrl}/failed-events`, { params });
  }

  summaryCsv(): Observable<string> {
    return this.http.get(`${environment.apiBaseUrl}/reports/summary.csv`, { responseType: 'text' });
  }

  eventsCsv(filters: { requestId?: string; loanAccountNumber?: string; eventType?: string; text?: string } = {}): Observable<string> {
    const params = this.buildParams(filters);
    return this.http.get(`${environment.apiBaseUrl}/reports/events.csv`, { params, responseType: 'text' });
  }

  closuresCsv(filters: ClosureSearchFilters): Observable<string> {
    const params = this.buildParams(filters as Partial<Record<string, string | number | boolean | null | undefined>>);
    return this.http.get(`${environment.apiBaseUrl}/reports/closures.csv`, { params, responseType: 'text' });
  }

  private buildParams(filters: Partial<Record<string, string | number | boolean | null | undefined>>): HttpParams {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }
}
