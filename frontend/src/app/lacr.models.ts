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
  closureStatusCounts?: Record<string, number>;
  reconciliationStatusCounts?: Record<string, number>;
}

export interface OperatorProfile {
  username: string;
  displayName: string;
  roles: string[];
}

export interface OperatorCredentials {
  username: string;
  password: string;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  validationErrors?: Array<{ field: string; message: string }>;
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
  statusHistory?: LoanClosureHistoryItem[];
}

export interface LoanClosureHistoryItem {
  fromStatus?: string | null;
  toStatus: string;
  actionName: string;
  remarks?: string | null;
  changedBy?: string | null;
  changedAt?: string | null;
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
  previousHash?: string | null;
  recordHash?: string | null;
}

export interface FailedEventItem {
  id: number;
  requestId: string;
  loanAccountNumber?: string | null;
  failureReason?: string | null;
  attemptCount: number;
  failedStage?: string | null;
  createdBy?: string | null;
  failedAt: string;
}

export interface OutboxHealth {
  pendingCount: number;
  processingCount: number;
  publishedCount: number;
  failedCount: number;
  staleProcessingCount: number;
  oldestPendingAt?: string | null;
  oldestProcessingAt?: string | null;
  newestPublishedAt?: string | null;
  reclaimAfter: string;
}

export interface OutboxRecoveryResult {
  recoveredCount: number;
  republishedCount: number;
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
