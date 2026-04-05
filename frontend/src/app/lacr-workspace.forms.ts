import { FormBuilder, Validators } from '@angular/forms';

export function buildLacrLoginForm(fb: FormBuilder) {
  return fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });
}

export function buildLacrSearchForm(fb: FormBuilder) {
  return fb.group({
    loanAccountNumber: [''],
    borrowerName: [''],
    closureStatus: [''],
    reconciliationStatus: [''],
    minSettlementAmount: [''],
    maxSettlementAmount: ['']
  });
}

export function buildLacrCreateForm(fb: FormBuilder) {
  return fb.group({
    requestId: ['', [Validators.required]],
    loanAccountNumber: ['', [Validators.required]],
    borrowerName: ['', [Validators.required]],
    closureReason: ['', [Validators.required]],
    outstandingPrincipal: ['', [Validators.required, Validators.min(0)]],
    accruedInterest: ['0.00', [Validators.required, Validators.min(0)]],
    penaltyAmount: ['0.00', [Validators.required, Validators.min(0)]],
    processingFee: ['0.00', [Validators.required, Validators.min(0)]],
    remarks: ['']
  });
}

export function buildLacrSettlementForm(fb: FormBuilder) {
  return fb.group({
    adjustmentAmount: ['0.00', [Validators.required, Validators.min(0)]],
    remarks: ['']
  });
}

export function buildLacrReconcileForm(fb: FormBuilder) {
  return fb.group({
    bankConfirmedAmount: ['0.00', [Validators.required, Validators.min(0)]],
    remarks: ['']
  });
}

export function buildLacrStatusForm(fb: FormBuilder) {
  return fb.group({
    targetStatus: ['APPROVED', [Validators.required]],
    remarks: ['']
  });
}

export function buildLacrEventForm(fb: FormBuilder) {
  return fb.group({
    requestId: [''],
    loanAccountNumber: [''],
    eventType: [''],
    text: ['']
  });
}
