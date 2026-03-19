import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { environment } from '../environments/environment';
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

@Injectable({ providedIn: 'root' })
export class LacrApiService {
  constructor(private readonly http: HttpClient) {}

  summary(): Observable<LoanClosureSummary> {
    return this.http.get<LoanClosureSummary>(`${environment.apiBaseUrl}/summary`).pipe(
      catchError(() => of(this.mockSummary()))
    );
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
    return this.http.get<LoanClosurePageResponse>(`${environment.apiBaseUrl}/search`, { params }).pipe(
      catchError(() => of(this.mockPage(page, size)))
    );
  }

  events(filters: { requestId?: string; loanAccountNumber?: string; eventType?: string; text?: string } = {}): Observable<LoanClosureEventItem[]> {
    const params = this.buildParams(filters);
    return this.http.get<LoanClosureEventItem[]>(`${environment.apiBaseUrl}/reports/events`, { params }).pipe(
      catchError(() => of(this.mockEvents()))
    );
  }

  summaryCsv(): Observable<string> {
    return this.http.get(`${environment.apiBaseUrl}/reports/summary.csv`, { responseType: 'text' }).pipe(
      catchError(() => of(this.mockSummaryCsv()))
    );
  }

  eventsCsv(filters: { requestId?: string; loanAccountNumber?: string; eventType?: string; text?: string } = {}): Observable<string> {
    const params = this.buildParams(filters);
    return this.http.get(`${environment.apiBaseUrl}/reports/events.csv`, { params, responseType: 'text' }).pipe(
      catchError(() => of(this.mockEventsCsv()))
    );
  }

  closuresCsv(filters: ClosureSearchFilters): Observable<string> {
    const params = this.buildParams(filters as Partial<Record<string, string | number | boolean | null | undefined>>);
    return this.http.get(`${environment.apiBaseUrl}/reports/closures.csv`, { params, responseType: 'text' }).pipe(
      catchError(() => of(this.mockClosuresCsv()))
    );
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

  private mockSummary(): LoanClosureSummary {
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

  private mockPage(page = 0, size = 6): LoanClosurePageResponse {
    const content = this.mockClosures();
    const start = page * size;
    const slice = content.slice(start, start + size);
    return {
      content: slice,
      number: page,
      size,
      totalElements: content.length,
      totalPages: Math.max(1, Math.ceil(content.length / size)),
      first: page === 0,
      last: start + size >= content.length,
      empty: slice.length === 0
    };
  }

  private mockClosures(): LoanClosureItem[] {
    return [];
  }

  private mockEvents(): LoanClosureEventItem[] {
    return [];
  }

  private mockSummaryCsv(): string {
    return 'metric,value\n' + `totalRequests,${this.mockSummary().totalRequests}\n`;
  }

  private mockEventsCsv(): string {
    return 'requestId,closureId,loanAccountNumber,eventType,fromStatus,toStatus,reconciliationStatus,actor,details,createdAt\n';
  }

  private mockClosuresCsv(): string {
    return 'id,requestId,loanAccountNumber,borrowerName,closureReason,outstandingPrincipal,accruedInterest,penaltyAmount,processingFee,settlementAdjustment,settlementAmount,bankConfirmedAmount,settlementDifference,closureStatus,reconciliationStatus,remarks,createdBy,requestedAt,calculatedAt,reconciledAt,approvedAt,closedAt\n';
  }
}
