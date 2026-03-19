export interface LoanClosureSummary {
  totalRequests: number;
  pendingRequests: number;
  settlementCalculatedRequests: number;
  reconciliationPendingRequests: number;
  reconciledRequests: number;
  approvedRequests: number;
  closedRequests: number;
  rejectedRequests: number;
  onHoldRequests: number;
  matchedReconciliations: number;
  mismatchedReconciliations: number;
  pendingReconciliations: number;
  totalSettlementAmount: string;
  totalOutstandingPrincipal: string;
}

export interface LoanClosureItem {
  id: number;
  requestId: string;
  loanAccountNumber: string;
  borrowerName: string;
  closureReason: string;
  outstandingPrincipal: string;
  accruedInterest: string;
  penaltyAmount: string;
  processingFee: string;
  settlementAdjustment: string;
  settlementAmount: string;
  bankConfirmedAmount?: string | null;
  settlementDifference?: string | null;
  closureStatus: string;
  reconciliationStatus: string;
  remarks?: string | null;
  createdBy?: string | null;
  requestedAt?: string | null;
  calculatedAt?: string | null;
  reconciledAt?: string | null;
  approvedAt?: string | null;
  closedAt?: string | null;
}

export interface LoanClosureEventItem {
  requestId: string;
  closureId: number;
  loanAccountNumber: string;
  eventType: string;
  fromStatus?: string | null;
  toStatus?: string | null;
  reconciliationStatus?: string | null;
  actor?: string | null;
  details?: string | null;
  createdAt: string;
}

export interface ClosureSearchFilters {
  loanAccountNumber?: string;
  borrowerName?: string;
  closureStatus?: string;
  reconciliationStatus?: string;
  minSettlementAmount?: string;
  maxSettlementAmount?: string;
}

export interface LoanClosurePageResponse {
  content: LoanClosureItem[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface CreateLoanClosureRequest {
  requestId: string;
  loanAccountNumber: string;
  borrowerName: string;
  closureReason: string;
  outstandingPrincipal: string;
  accruedInterest: string;
  penaltyAmount: string;
  processingFee: string;
  remarks?: string;
}

export interface CalculateSettlementRequest {
  adjustmentAmount: string;
  remarks?: string;
}

export interface AdvanceClosureStatusRequest {
  targetStatus: string;
  remarks?: string;
}

export interface ReconcileClosureRequest {
  bankConfirmedAmount: string;
  remarks?: string;
}
