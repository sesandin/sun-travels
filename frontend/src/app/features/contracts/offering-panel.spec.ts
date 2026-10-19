import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Contract } from '../../models/contract.model';
import { Offering } from '../../models/offering.model';
const api = '/api';
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
  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });
  it('does not open or submit expired contract maintenance', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
    fixture.componentRef.setInput('contract', {
      ...contract, status: 'APPROVED', validTo: '2026-10-18',
    });
    load([offering]);
    c.open(offering, 'rate');
    expect(c.mode()).toBeNull();
    c.open(offering, 'status');
    expect(c.mode()).toBeNull();
    c.mode.set('rate');
    c.selected.set(offering);
    c.rateForm.setValue({ ratePerPersonPerNight: 125 });
    c.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Offering editor"]')).toBeNull();
    http.expectNone((r) => r.method !== 'GET');
  });
  // Completes the pending API request with test fixture data.
  function load(rows: Offering[] = []) {
    http.expectOne(api + '/contracts/7/offerings').flush(rows);
    fixture.detectChanges();
    for (const row of rows)
      http.expectOne(api + '/offerings/' + row.offeringId + '/seasonal-rates').flush([]);
    fixture.detectChanges();
  }
  // Opens the offering editor and supplies room-type choices.
  function open() {
    c.open();
    http
      .expectOne(api + '/room-types')
      .flush([{ roomTypeId: 3, name: 'Double', description: null }]);
  }
  // Populates the form with valid test input.
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
  it('shows accessible field guidance after invalid submission and clears it as fields are corrected', () => {
    load();
    open();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.validation-notice')).toBeNull();

    c.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'A few details need your attention',
    );
    for (const id of ['offering-room', 'offering-rate', 'offering-rooms', 'offering-adults']) {
      const input: HTMLElement = fixture.nativeElement.querySelector('#' + id);
      expect(input.getAttribute('aria-invalid')).toBe('true');
      expect(input.getAttribute('aria-describedby')).toBe(id + '-error');
      expect(fixture.nativeElement.querySelector('#' + id + '-error').textContent.trim()).not.toBe('');
    }
    fill();
    c.form.controls.ratePerPersonPerNight.setValue(1.001);
    c.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#offering-rate-error').textContent).toContain(
      'Use up to 2 decimal places',
    );
    expect(fixture.nativeElement.querySelector('#offering-room-error')).toBeNull();

    c.form.controls.ratePerPersonPerNight.setValue(125.25);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.validation-notice')).toBeNull();
    expect(fixture.nativeElement.querySelector('#offering-rate').getAttribute('aria-invalid')).toBe('false');
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
  for (const status of ['TERMINATED'] as const)
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

  it('re-enables an offering under a suspended contract without implying bookability', () => {
    fixture.componentRef.setInput('contract', { ...contract, status: 'SUSPENDED' });
    load([{ ...offering, status: 'SUSPENDED' }]);
    expect(fixture.nativeElement.textContent).toContain('Change offering status');
    expect(fixture.nativeElement.textContent).not.toContain('Edit base rate');
    expect(fixture.nativeElement.textContent).toContain('remain unavailable for new sales');
    c.open({ ...offering, status: 'SUSPENDED' }, 'status');
    c.statusForm.controls.status.setValue('ENABLED');
    c.save();
    const req = http.expectOne(api + '/offerings/8/status');
    expect(req.request.body).toEqual({ status: 'ENABLED' });
    req.flush({ ...offering, status: 'ENABLED' });
    expect(c.rows()[0].status).toBe('ENABLED');
    expect(c.contract().status).toBe('SUSPENDED');
  });
});
