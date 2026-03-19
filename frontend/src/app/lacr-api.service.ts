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
    const params = this.buildParams(filters).set('page', String(page)).set('size', String(size));
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
    const params = this.buildParams(filters);
    return this.http.get(`${environment.apiBaseUrl}/reports/closures.csv`, { params, responseType: 'text' }).pipe(
      catchError(() => of(this.mockClosuresCsv()))
    );
  }

  private buildParams(filters: Record<string, unknown>): HttpParams {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, value);
      }
    });
    return params;
  }

  private mockSummary(): LoanClosureSummary {
    return {
      totalRequests: 128,
      pendingRequests: 36,
      settlementCalculatedRequests: 27,
      reconciliationPendingRequests: 19,
      reconciledRequests: 46,
      approvedRequests: 18,
      closedRequests: 28,
      rejectedRequests: 8,
      onHoldRequests: 4,
      matchedReconciliations: 41,
      mismatchedReconciliations: 8,
      pendingReconciliations: 19,
      totalSettlementAmount: '18456000.00',
      totalOutstandingPrincipal: '17344000.00'
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
    return [
      {
        id: 1,
        requestId: 'REQ-1029',
        loanAccountNumber: 'LN-1001',
        borrowerName: 'Asha Rao',
        closureReason: 'Borrower requested pre-closure',
        outstandingPrincipal: '100000.00',
        accruedInterest: '1200.00',
        penaltyAmount: '300.00',
        processingFee: '100.00',
        settlementAdjustment: '500.00',
        settlementAmount: '101100.00',
        bankConfirmedAmount: '101100.00',
        settlementDifference: '0.00',
        closureStatus: 'RECONCILIATION_PENDING',
        reconciliationStatus: 'PENDING',
        remarks: 'Waiting for bank confirmation',
        createdBy: 'ops.user',
        requestedAt: new Date().toISOString()
      },
      {
        id: 2,
        requestId: 'REQ-1041',
        loanAccountNumber: 'LN-1041',
        borrowerName: 'Rohan Patel',
        closureReason: 'Settlement against loan closure',
        outstandingPrincipal: '88000.00',
        accruedInterest: '980.00',
        penaltyAmount: '250.00',
        processingFee: '100.00',
        settlementAdjustment: '0.00',
        settlementAmount: '89330.00',
        bankConfirmedAmount: '89570.00',
        settlementDifference: '240.00',
        closureStatus: 'ON_HOLD',
        reconciliationStatus: 'MISMATCHED',
        remarks: 'Difference detected in bank confirmation',
        createdBy: 'ops.user',
        requestedAt: new Date().toISOString()
      }
    ];
  }

  private mockEvents(): LoanClosureEventItem[] {
    return [
      {
        requestId: 'REQ-1029',
        closureId: 1,
        loanAccountNumber: 'LN-1001',
        eventType: 'SETTLEMENT_CALCULATED',
        fromStatus: 'REQUESTED',
        toStatus: 'SETTLEMENT_CALCULATED',
        reconciliationStatus: 'PENDING',
        actor: 'ops.user',
        details: 'Settlement computed with 500 adjustment',
        createdAt: new Date().toISOString()
      },
      {
        requestId: 'REQ-1041',
        closureId: 2,
        loanAccountNumber: 'LN-1041',
        eventType: 'RECONCILED_MISMATCHED',
        fromStatus: 'RECONCILIATION_PENDING',
        toStatus: 'ON_HOLD',
        reconciliationStatus: 'MISMATCHED',
        actor: 'ops.user',
        details: 'Mismatch recorded in audit stream',
        createdAt: new Date().toISOString()
      }
    ];
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
