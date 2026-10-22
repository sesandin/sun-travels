import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Contract } from '../../models/contract.model';
import { Offering } from '../../models/offering.model';
import { SeasonalRates } from './seasonal-rates';

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
const seasonal = {
  seasonalRateId: 12,
  offeringId: 8,
  validFrom: '2026-06-01',
  validTo: '2026-06-30',
  ratePerPersonPerNight: 150,
};
describe('Seasonal rate management', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<SeasonalRates>>;
  let c: SeasonalRates;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SeasonalRates],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SeasonalRates);
    c = fixture.componentInstance;
    fixture.componentRef.setInput('contract', contract);
    fixture.componentRef.setInput('offering', offering);
    fixture.detectChanges();
    http.expectNone(api + '/offerings/8/seasonal-rates');
    c.toggle();
    fixture.detectChanges();
  });
  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });
  it('preserves expired seasonal history and prevents opening or submitting maintenance', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
    fixture.componentRef.setInput('contract', { ...contract, validTo: '2026-10-18' });
    load();
    expect(fixture.nativeElement.textContent).toContain('150');
    expect(fixture.nativeElement.textContent).not.toContain('Add Seasonal Rate');
    expect(fixture.nativeElement.textContent).not.toContain('Edit seasonal rate');
    c.open(seasonal);
    expect(c.editing()).toBe(false);
    c.editing.set(true);
    c.form.setValue({
      validFrom: seasonal.validFrom,
      validTo: seasonal.validTo,
      ratePerPersonPerNight: 165,
    });
    c.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Seasonal rate editor"]')).toBeNull();
    http.expectNone((r) => r.method !== 'GET');
  });
  const url = api + '/offerings/8/seasonal-rates';
  it('loads only on expansion and reuses successful results across repeated toggles', () => {
    c.toggle();
    c.toggle();
    const request = http.expectOne(url);
    request.flush([seasonal]);
    c.toggle();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.season')).toBeNull();
    c.toggle();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('150 USD');
    http.expectNone(url);
  });
  // Completes the pending API request with test fixture data.
  function load(rows = [seasonal]) {
    http.expectOne(url).flush(rows);
    fixture.detectChanges();
  }
  // Populates the form with valid test input.
  function fill() {
    c.form.setValue({
      validFrom: seasonal.validFrom,
      validTo: seasonal.validTo,
      ratePerPersonPerNight: 165.5,
    });
  }
  it('shows loading, dated periods, currency and override explanation', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading seasonal rates');
    load();
    expect(fixture.nativeElement.textContent).toContain('01 Jun 2026');
    expect(fixture.nativeElement.textContent).toContain('30 Jun 2026');
    expect(fixture.nativeElement.textContent).toContain('150 USD');
    expect(fixture.nativeElement.textContent).toContain('Override the base rate');
  });
  it('handles list failure, retry and no periods', () => {
    http.expectOne(url).error(new ProgressEvent('error'));
    expect(c.error()).toContain('Cannot reach');
    c.load();
    load([]);
    expect(fixture.nativeElement.textContent).toContain('No seasonal periods');
  });
  it('creates the expected payload and preserves inventory', () => {
    load([]);
    c.open();
    fill();
    c.save();
    c.save();
    const r = http.expectOne(url);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({
      validFrom: seasonal.validFrom,
      validTo: seasonal.validTo,
      ratePerPersonPerNight: 165.5,
    });
    r.flush(seasonal);
    expect(c.rows()).toHaveLength(1);
    expect(c.editing()).toBe(false);
    expect(c.offering().contractedRoomCount).toBe(10);
  });
  it('edits a seasonal record using its existing ID', () => {
    load();
    c.open(seasonal);
    fill();
    c.save();
    const r = http.expectOne(url + '/12');
    expect(r.request.method).toBe('PUT');
    r.flush({ ...seasonal, ratePerPersonPerNight: 165.5 });
    expect(c.rows()[0].ratePerPersonPerNight).toBe(165.5);
  });
  it('displays overlap conflict and preserves period and entered values', () => {
    load();
    c.open(seasonal);
    fill();
    c.save();
    http
      .expectOne(url + '/12')
      .flush(
        { message: 'Seasonal period overlaps another period' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Seasonal period overlaps');
    expect(c.form.controls.ratePerPersonPerNight.value).toBe(165.5);
    expect(c.rows()[0].ratePerPersonPerNight).toBe(150);
    expect(c.editing()).toBe(true);
    expect(c.notice()).toBe('');
  });
  it('validates required fields, order, rate precision and parent validity', () => {
    load([]);
    c.open();
    c.save();
    expect(c.form.invalid).toBe(true);
    fill();
    c.form.controls.validFrom.setValue('2026-07-01');
    c.save();
    expect(c.form.hasError('period')).toBe(true);
    fill();
    for (const n of [0, -1, 1.001, 100000000]) {
      c.form.controls.ratePerPersonPerNight.setValue(n);
      c.save();
      expect(c.form.invalid).toBe(true);
    }
    fill();
    c.form.controls.validTo.setValue('2027-01-01');
    c.save();
    expect(c.failure()).toContain('contract validity');
    http.expectNone(url);
  });
  it('allows approved seasonal creation with inclusive equal dates', () => {
    fixture.componentRef.setInput('contract', { ...contract, status: 'APPROVED' });
    load([]);
    c.open();
    c.form.setValue({
      validFrom: contract.validTo,
      validTo: contract.validTo,
      ratePerPersonPerNight: 0.01,
    });
    c.save();
    http.expectOne(url).flush({
      ...seasonal,
      validFrom: contract.validTo,
      validTo: contract.validTo,
      ratePerPersonPerNight: 0.01,
    });
    expect(c.notice()).toContain('Confirmed reservation prices remain unchanged');
  });
  for (const status of ['SUSPENDED', 'TERMINATED'] as const)
    it(status + ' permits reading but blocks seasonal maintenance', () => {
      fixture.componentRef.setInput('contract', { ...contract, status });
      load();
      expect(fixture.nativeElement.textContent).not.toContain('Add Seasonal Rate');
      expect(fixture.nativeElement.textContent).not.toContain('Edit seasonal rate');
      c.open();
      fill();
      c.save();
      http.expectNone(url);
      expect(c.editing()).toBe(false);
    });
  it('shows backend field validation errors and retains editing', () => {
    load([]);
    c.open();
    fill();
    c.save();
    http
      .expectOne(url)
      .flush(
        { fieldErrors: { validFrom: 'Date must be within contract' } },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(c.failure()).toContain('validFrom: Date must be within contract');
    expect(c.editing()).toBe(true);
  });

  it('offers add and edit without seasonal-rate deletion controls', () => {
    load();
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    );
    expect(buttons.some((button) => button.textContent?.trim() === 'Edit seasonal rate')).toBe(
      true,
    );
    expect(buttons.some((button) => button.textContent?.trim() === 'Add Seasonal Rate')).toBe(true);
    expect(buttons.some((button) => /remove|delete/i.test(button.textContent ?? ''))).toBe(false);
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(c.rows()).toEqual([seasonal]);
  });
});
