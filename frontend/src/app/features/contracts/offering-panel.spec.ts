import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Contract } from '../../models/contract.model';
import { Offering } from '../../models/offering.model';
const api = 'http://localhost:8080/api';
const contract: Contract = {
  contractId: 7,
  contractCode: 'C7',
  hotelId: 9,
  hotelName: 'Hotel',
  validFrom: '2026-01-01',
  validTo: '2026-12-31',
  markupPercentage: 15,
  currencyCode: 'USD',
  status: 'DRAFT',
};
const offering: Offering = {
  offeringId: 8,
  contractId: 7,
  roomTypeId: 3,
  roomTypeName: 'Double',
  ratePerPersonPerNight: 100,
  contractedRoomCount: 10,
  maxAdults: 2,
  status: 'ENABLED',
  currencyCode: 'USD',
};
import { OfferingPanel } from './offering-panel';
describe('Contract offerings', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<OfferingPanel>>;
  let c: OfferingPanel;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OfferingPanel],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OfferingPanel);
    c = fixture.componentInstance;
    fixture.componentRef.setInput('contract', contract);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  function load(rows: Offering[] = []) {
    http.expectOne(api + '/contracts/7/offerings').flush(rows);
    fixture.detectChanges();
    for (const row of rows)
      http.expectOne(api + '/offerings/' + row.offeringId + '/seasonal-rates').flush([]);
    fixture.detectChanges();
  }
  function open() {
    c.open();
    http
      .expectOne(api + '/room-types')
      .flush([{ roomTypeId: 3, name: 'Double', description: null }]);
  }
  function fill() {
    c.form.setValue({
      roomTypeId: 3,
      ratePerPersonPerNight: 125.25,
      contractedRoomCount: 12,
      maxAdults: 3,
      status: 'ENABLED',
    });
  }
  it('shows loading, empty, currency and real offering data', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading offerings');
    load([offering]);
    expect(fixture.nativeElement.textContent).toContain('Double');
    expect(fixture.nativeElement.textContent).toContain('100 USD');
    expect(fixture.nativeElement.textContent).toContain('No seasonal periods');
  });
  it('has empty state and draft add action', () => {
    load();
    expect(fixture.nativeElement.textContent).toContain('No offerings for this contract');
    expect(fixture.nativeElement.textContent).toContain('Add Offering');
  });
  it('retries failed listing', () => {
    http.expectOne(api + '/contracts/7/offerings').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(c.error()).toContain('Cannot reach');
    c.load();
    http.expectOne(api + '/contracts/7/offerings').flush([]);
    fixture.detectChanges();
    expect(c.error()).toBe('');
  });
  it('posts a typed offering without foreign parent IDs and blocks duplicate submission', () => {
    load();
    open();
    fill();
    c.save();
    c.save();
    const r = http.expectOne(api + '/contracts/7/offerings');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({
      roomTypeId: 3,
      ratePerPersonPerNight: 125.25,
      contractedRoomCount: 12,
      maxAdults: 3,
      status: 'ENABLED',
    });
    r.flush(offering);
    expect(c.mode()).toBeNull();
    expect(c.notice()).toContain('Offering saved');
  });
  it('edits drafts and preserves fields on duplicate conflict', () => {
    load([offering]);
    c.open(offering);
    http.expectOne(api + '/room-types').flush([{ roomTypeId: 3, name: 'Double' }]);
    fill();
    c.save();
    const r = http.expectOne(api + '/offerings/8');
    expect(r.request.method).toBe('PUT');
    r.flush(
      { message: 'Room type already appears in this contract' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(c.failure()).toContain('already appears');
    expect(c.form.controls.contractedRoomCount.value).toBe(12);
    expect(c.rows()[0].contractedRoomCount).toBe(10);
  });
  it('rejects invalid money, fractional counts and missing room type', () => {
    load();
    open();
    c.save();
    expect(c.form.invalid).toBe(true);
    fill();
    for (const n of [0, -1, 100000000, 1.001]) {
      c.form.controls.ratePerPersonPerNight.setValue(n);
      c.save();
      expect(c.form.invalid).toBe(true);
    }
    fill();
    c.form.controls.contractedRoomCount.setValue(1.5);
    c.save();
    expect(c.form.invalid).toBe(true);
    http.expectNone(api + '/contracts/7/offerings');
  });
  it('retries room type loading and prevents writes while unavailable', () => {
    load();
    c.open();
    http.expectOne(api + '/room-types').error(new ProgressEvent('error'));
    fill();
    c.save();
    http.expectNone(api + '/contracts/7/offerings');
    c.loadRooms();
    http.expectOne(api + '/room-types').flush([{ roomTypeId: 3, name: 'Double' }]);
    expect(c.roomsError()).toBe('');
  });
  it('approved UI hides structure and sends only base rate', () => {
    fixture.componentRef.setInput('contract', { ...contract, status: 'APPROVED' });
    load([offering]);
    const text = fixture.nativeElement.textContent;
    expect(text).not.toContain('Add Offering');
    expect(text).not.toContain('Edit offering');
    expect(text).toContain('Edit base rate');
    c.open(offering);
    expect(c.mode()).toBeNull();
    c.open(offering, 'rate');
    c.rateForm.controls.ratePerPersonPerNight.setValue(135.5);
    c.save();
    const r = http.expectOne(api + '/offerings/8/rate');
    expect(r.request.method).toBe('PATCH');
    expect(r.request.body).toEqual({ ratePerPersonPerNight: 135.5 });
    r.flush({ ...offering, ratePerPersonPerNight: 135.5 });
  });
  it('uses explicit offering-status confirmation and a status-only payload', () => {
    load([offering]);
    c.open(offering, 'status');
    c.statusForm.controls.status.setValue('SUSPENDED');
    c.save();
    const r = http.expectOne(api + '/offerings/8/status');
    expect(r.request.method).toBe('PATCH');
    expect(r.request.body).toEqual({ status: 'SUSPENDED' });
    r.flush({ ...offering, status: 'SUSPENDED' });
    expect(c.rows()[0].status).toBe('SUSPENDED');
  });
  for (const status of ['SUSPENDED', 'TERMINATED'] as const)
    it(status + ' exposes history with no maintenance actions', () => {
      fixture.componentRef.setInput('contract', { ...contract, status });
      load([offering]);
      expect(fixture.nativeElement.textContent).toContain('Historical offerings are preserved');
      expect(fixture.nativeElement.textContent).not.toContain('Add Offering');
      expect(fixture.nativeElement.textContent).not.toContain('Edit base rate');
      expect(fixture.nativeElement.textContent).not.toContain('Add Seasonal Rate');
      c.open(offering, 'status');
      c.save();
      expect(c.mode()).toBeNull();
      http.expectNone(api + '/offerings/8/status');
    });
});
