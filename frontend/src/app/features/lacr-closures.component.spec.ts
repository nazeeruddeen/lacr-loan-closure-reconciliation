import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { LacrClosuresComponent } from './lacr-closures.component';

describe('LacrClosuresComponent', () => {
  let fixture: ComponentFixture<LacrClosuresComponent>;
  let component: LacrClosuresComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LacrClosuresComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(LacrClosuresComponent);
    component = fixture.componentInstance;
    component.searchForm = new FormGroup({
      loanAccountNumber: new FormControl('LN-1001'),
      borrowerName: new FormControl('Borrower'),
      closureStatus: new FormControl('REQUESTED'),
      reconciliationStatus: new FormControl(''),
      minSettlementAmount: new FormControl(''),
      maxSettlementAmount: new FormControl('')
    });
    component.closurePage = {
      content: [],
      number: 0,
      size: 6,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false
    } as any;
    component.closures = [
      {
        id: 11,
        requestId: 'LACR-11',
        loanAccountNumber: 'LN-1001',
        borrowerName: 'Meera Nair',
        closureReason: 'Early closure',
        settlementAmount: '100000.00',
        settlementDifference: '0.00',
        closureStatus: 'REQUESTED',
        reconciliationStatus: 'PENDING'
      }
    ] as any;
    component.loading = true;
    component.busyClosureId = 11;
    component.busyAction = 'settle';
    component.statusTone = () => 'active';
    component.canCalculateSettlement = () => true;
    component.canStartReconciliation = () => false;
    component.canReconcile = () => false;
    component.canAdvance = () => false;
    component.recommendedActionLabel = () => 'Settle';
    fixture.detectChanges();
  });

  it('shows busy action text for the active queue item', () => {
    expect(fixture.nativeElement.textContent).toContain('Working...');
  });

  it('emits open when the operator opens a closure', () => {
    spyOn(component.open, 'emit');

    const openButton = Array.from(fixture.nativeElement.querySelectorAll('button'))
      .find((element) => (element as HTMLButtonElement).textContent?.trim() === 'Open') as HTMLButtonElement;

    openButton.click();

    expect(component.open.emit).toHaveBeenCalledWith(component.closures[0]);
  });
});
