import { directoryPage } from '../../testing/directory-page';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ContractList } from './contract-list';
import { Contract } from '../../models/contract.model';
const api = '/api';
const contract: Contract = {
  contractId: 4,
  contractCode: 'C004',
  hotelId: 7,
  hotelName: 'Ocean Hotel',
  validFrom: '2026-01-01',
  validTo: '2026-12-31',
  markupPercentage: 15,
  currencyCode: 'USD',
  status: 'DRAFT',
};
const offering = {
  offeringId: 8,
  contractId: 4,
  roomTypeId: 3,
  roomTypeName: 'Double',
  ratePerPersonPerNight: 100,
  contractedRoomCount: 12,
  maxAdults: 2,
  status: 'ENABLED',
  currencyCode: 'USD',
};
describe('Contract offering directory links', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<ContractList>>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({}));
    TestBed.configureTestingModule({
      imports: [ContractList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { queryParamMap: params.asObservable() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ContractList);
    fixture.detectChanges();
    http.expectOne((r) => r.url === api + '/contracts/page').flush(directoryPage([contract]));
    http.expectOne(api + '/hotels').flush([]);
  });
  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });
  // Opens linked contract details and supplies their API response.
  function open(status: Contract['status'] = 'DRAFT', offeringId = '8', validTo = contract.validTo) {
    params.next(convertToParamMap({ contractId: '4', offeringId }));
    http.expectOne(api + '/contracts/4').flush({ ...contract, status, validTo });
    fixture.detectChanges();
    http.expectOne(api + '/contracts/4/offerings').flush([offering]);
    fixture.detectChanges();
    http.expectOne(api + '/offerings/8/seasonal-rates').flush([]);
  }
  it('opens existing details and focuses the relevant offering', async () => {
    open();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.mode()).toBe('details');
    expect(fixture.componentInstance.selected()?.contractId).toBe(4);
    const card: HTMLElement = fixture.nativeElement.querySelector('#offering-8');
    expect(card.classList.contains('linked-offering')).toBe(true);
    expect(document.activeElement).toBe(card);
    expect(card.textContent).toContain('Edit offering');
  });
  for (const status of ['SUSPENDED', 'TERMINATED'] as const)
    it('preserves ' + status + ' read-only restrictions through the same panel', () => {
      open(status);
      fixture.detectChanges();
      const card: HTMLElement = fixture.nativeElement.querySelector('#offering-8');
      expect(card.textContent).not.toContain('Edit offering');
      expect(card.textContent).not.toContain('Edit base rate');
      expect(card.textContent).not.toContain('Add Seasonal Rate');
    });
  it('preserves approved controlled maintenance without a structural editor', () => {
    open('APPROVED');
    fixture.detectChanges();
    const card: HTMLElement = fixture.nativeElement.querySelector('#offering-8');
    expect(card.textContent).toContain('Edit base rate');
    expect(card.textContent).not.toContain('Edit offering');
  });
  it.each([
    ['2026-10-18', true],
    ['2026-10-19', false],
    ['2026-10-20', false],
  ] as const)('shows expiration and appropriate detail actions for %s', (validTo, expired) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
    open('APPROVED', '8', validTo);
    fixture.detectChanges();
    const panel: HTMLElement = fixture.nativeElement.querySelector('app-offering-panel');
    const details = panel.parentElement!;
    expect(details.querySelector('.expired-badge')?.textContent?.trim() ?? null)
      .toBe(expired ? 'Expired' : null);
    expect(details.textContent).toContain('APPROVED');
    for (const label of ['Edit base rate', 'Change offering status', 'Add Seasonal Rate']) {
      expect(panel.textContent?.includes(label)).toBe(!expired);
    }
    expect(panel.textContent).toContain('Double');
    expect(panel.textContent).toContain('100');
    expect(panel.textContent).toContain('Seasonal Rates');
    http.expectNone((r) => r.method !== 'GET');
  });
  it('reports an offering that does not belong to the selected contract', () => {
    open('DRAFT', '99');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'linked offering is not part of this contract',
    );
    expect(fixture.nativeElement.querySelector('.linked-offering')).toBeNull();
  });
  it('rejects malformed or out-of-range query IDs without issuing requests', () => {
    for (const contractId of ['bad', '0', '-1', '2147483648'])
      params.next(convertToParamMap({ contractId, offeringId: '8' }));
    params.next(convertToParamMap({ contractId: '4', offeringId: 'bad' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.linkError()).toContain('Invalid contract or offering link');
    http.expectNone(api + '/contracts/4');
  });
  it('shows a missing-contract response without creating a fake detail', () => {
    params.next(convertToParamMap({ contractId: '99', offeringId: '8' }));
    http
      .expectOne(api + '/contracts/99')
      .flush({ message: 'Contract not found' }, { status: 404, statusText: 'Not found' });
    fixture.detectChanges();
    expect(fixture.componentInstance.submissionError()).toBe('Contract not found');
    expect(fixture.nativeElement.querySelector('app-offering-panel')).toBeNull();
  });
  it('reloads offerings when a reused detail view receives a different contract', () => {
    open();
    fixture.detectChanges();
    params.next(convertToParamMap({ contractId: '5', offeringId: '9' }));
    http
      .expectOne(api + '/contracts/5')
      .flush({ ...contract, contractId: 5, contractCode: 'C005', status: 'TERMINATED' });
    fixture.detectChanges();
    http
      .expectOne(api + '/contracts/5/offerings')
      .flush([{ ...offering, offeringId: 9, contractId: 5 }]);
    fixture.detectChanges();
    http.expectOne(api + '/offerings/9/seasonal-rates').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#offering-8')).toBeNull();
    expect(fixture.nativeElement.querySelector('#offering-9').textContent).not.toContain(
      'Edit offering',
    );
  });
  it('cancels an earlier detail read when the query changes', () => {
    params.next(convertToParamMap({ contractId: '4', offeringId: '8' }));
    const previous = http.expectOne(api + '/contracts/4');
    params.next(convertToParamMap({ contractId: '5', offeringId: '9' }));
    expect(previous.cancelled).toBe(true);
    http
      .expectOne(api + '/contracts/5')
      .flush({ message: 'Contract not found' }, { status: 404, statusText: 'Not found' });
  });
});
