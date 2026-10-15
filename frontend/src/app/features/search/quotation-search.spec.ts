import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuotationSearch } from './quotation-search';
import { QuotationResponse, SearchLimits } from '../../models/quotation.model';
const api = '/api';
const limits: SearchLimits = {
  businessDate: '2026-10-15',
  maxRooms: 8,
  maxAdultsPerRoom: 10,
  maxNights: 31,
  maxAdvanceDays: 730,
  maxResults: 20,
  allocationPolicy: 'LOWEST_TOTAL_PER_CONTRACT',
};
const response: QuotationResponse = {
  businessDate: limits.businessDate,
  checkIn: '2026-10-24',
  checkOut: '2026-10-27',
  nights: 3,
  matchingOptions: 1,
  truncated: false,
  allocationPolicy: limits.allocationPolicy,
  notice:
    'Informational quotation only. Prices and availability must be rechecked at reservation confirmation.',
  quotations: [
    {
      hotelId: 7,
      hotelName: 'Ocean Hotel',
      city: 'Colombo',
      contractId: 4,
      contractCode: 'C004',
      currencyCode: 'USD',
      checkIn: '2026-10-24',
      checkOut: '2026-10-27',
      nights: 3,
      eligibility: 'INFORMATIONAL',
      totalPrice: 805,
      rooms: [
        {
          roomNumber: 1,
          adults: 2,
          offeringId: 8,
          roomTypeId: 3,
          roomTypeName: 'Double',
          contractedRooms: 3,
          availableRoomsForStay: 2,
          allocatedRoomsForOffering: 1,
          remainingRoomsAfterAllocation: 1,
          totalPrice: 805,
          nightlyPrices: [
            {
              occupiedDate: '2026-10-24',
              source: 'BASE',
              seasonalRateId: null,
              hotelierRate: 100,
              markupPercentage: 15,
              sellingPrice: 230,
            },
            {
              occupiedDate: '2026-10-25',
              source: 'SEASONAL',
              seasonalRateId: 10,
              hotelierRate: 150,
              markupPercentage: 15,
              sellingPrice: 345,
            },
            {
              occupiedDate: '2026-10-26',
              source: 'BASE',
              seasonalRateId: null,
              hotelierRate: 100,
              markupPercentage: 15,
              sellingPrice: 230,
            },
          ],
        },
      ],
    },
  ],
};
describe('Search and nightly quotations', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<QuotationSearch>>;
  let c: QuotationSearch;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [QuotationSearch],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QuotationSearch);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  function setup() {
    http.expectOne(api + '/search/limits').flush(limits);
    http.expectOne(api + '/search/filters').flush({ hotels: [{ hotelId: 7, name: 'Ocean Hotel', city: 'Colombo' }], roomTypes: [{ roomTypeId: 3, name: 'Double' }] });
    fixture.detectChanges();
  }
  function fill() {
    c.form.patchValue({ checkIn: '2026-10-24', checkOut: '2026-10-27' });
    c.rooms.at(0).controls.adults.setValue(2);
  }
  it('loads business date and real reference selections without browser-local today assumptions', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading search dates');
    setup();
    expect(fixture.nativeElement.textContent).toContain('2026-10-15 (Asia/Colombo)');
    expect(fixture.nativeElement.textContent).toContain('Ocean Hotel');
    expect(c.limits()?.maxRooms).toBe(8);
  });
  it('validates missing, past, reversed and too-long stay dates', () => {
    setup();
    c.search();
    expect(c.form.invalid).toBe(true);
    fill();
    c.form.controls.checkIn.setValue('2026-10-14');
    c.search();
    expect(c.form.hasError('stay')).toBe(true);
    fill();
    c.form.controls.checkOut.setValue('2026-10-24');
    expect(c.form.hasError('stay')).toBe(true);
    c.form.controls.checkOut.setValue('2026-10-23');
    expect(c.form.hasError('stay')).toBe(true);
    fill();
    c.form.controls.checkOut.setValue('2026-12-01');
    expect(c.form.hasError('stay')).toBe(true);
    http.expectNone(api + '/search/quotations');
  });
  it('rejects fractional, null and out-of-range adults', () => {
    setup();
    fill();
    for (const adults of [null, 0, -1, 1.5, 11]) {
      c.rooms.at(0).controls.adults.setValue(adults);
      c.search();
      expect(c.form.invalid).toBe(true);
    }
    http.expectNone(api + '/search/quotations');
  });
  it('adds individual rooms, preserves adult counts, caps rooms and keeps at least one', () => {
    setup();
    fill();
    c.addRoom();
    c.rooms.at(1).controls.adults.setValue(1);
    expect(c.rooms.getRawValue()).toEqual([{ adults: 2 }, { adults: 1 }]);
    for (let i = 0; i < 10; i++) c.addRoom();
    expect(c.rooms.length).toBe(8);
    while (c.rooms.length > 1) c.removeRoom(1);
    c.removeRoom(0);
    expect(c.rooms.length).toBe(1);
  });
  it('submits structured multi-room payload and selected filters only to the read-only API', () => {
    setup();
    fill();
    c.addRoom();
    c.form.patchValue({ hotelId: 7, roomTypeId: 3, city: ' Colombo ' });
    c.search();
    c.search();
    const req = http.expectOne(api + '/search/quotations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      checkIn: '2026-10-24',
      checkOut: '2026-10-27',
      rooms: [{ adults: 2 }, { adults: 1 }],
      city: 'Colombo',
      hotelId: 7,
      roomTypeId: 3,
    });
    expect(c.searching()).toBe(true);
    req.flush({ ...response, quotations: [] });
    expect(c.searching()).toBe(false);
  });
  it('renders allocation, currency, selling totals and expandable nightly price sources', () => {
    setup();
    fill();
    c.search();
    http.expectOne(api + '/search/quotations').flush(response);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    for (const value of [
      'Ocean Hotel',
      'C004',
      '805.00 USD',
      'Double',
      'Contracted rooms',
      'Available throughout stay',
      'BASE',
      'SEASONAL',
      '345.00 USD',
      '15%',
    ])
      expect(text).toContain(value);
    const details: HTMLDetailsElement = fixture.nativeElement.querySelector('details');
    expect(details.open).toBe(false);
    details.open = true;
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(fixture.nativeElement.querySelector('.inventory dd').textContent.trim()).toBe('3');
    expect(fixture.nativeElement.querySelectorAll('.inventory dd')[1].textContent.trim()).toBe('2');
  });
  it('shows informational warning and no active confirmation or booking control', () => {
    setup();
    fill();
    c.search();
    http.expectOne(api + '/search/quotations').flush(response);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('does not book or hold rooms');
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    );
    expect(buttons.some((b) => /^(confirm|book)/i.test(b.textContent?.trim() ?? ''))).toBe(false);
  });
  it('shows helpful successful empty state', () => {
    setup();
    fill();
    c.search();
    http
      .expectOne(api + '/search/quotations')
      .flush({ ...response, quotations: [], matchingOptions: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No complete room combination found');
    expect(c.error()).toBe('');
  });
  it('preserves fields on conflict and supports retry without a fake quotation', () => {
    setup();
    fill();
    c.search();
    http
      .expectOne(api + '/search/quotations')
      .flush(
        { message: 'Search cannot safely quote inconsistent inventory data' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();
    expect(c.error()).toContain('inconsistent inventory');
    expect(c.response()).toBeNull();
    expect(c.form.controls.checkIn.value).toBe('2026-10-24');
    c.search();
    http.expectOne(api + '/search/quotations').flush(response);
    expect(c.error()).toBe('');
    expect(c.response()?.quotations).toHaveLength(1);
  });
  it('shows loading and network errors without allowing duplicate calls', () => {
    setup();
    fill();
    c.search();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Checking eligible rooms');
    c.search();
    http.expectOne(api + '/search/quotations').error(new ProgressEvent('error'));
    expect(c.error()).toContain('Cannot reach');
    expect(c.searching()).toBe(false);
  });
  it('handles setup failure and retries all reference APIs', () => {
    http.expectOne(api + '/search/filters').flush({ hotels: [], roomTypes: [] });
    http.expectOne(api + '/search/limits').error(new ProgressEvent('error'));
    c.search();
    http.expectNone(api + '/search/quotations');
    c.loadSetup();
    setup();
    expect(c.setupError()).toBe('');
  });
  it('shows backend field errors and missing-filter messages', () => {
    setup();
    fill();
    c.search();
    http
      .expectOne(api + '/search/quotations')
      .flush(
        { fieldErrors: { 'rooms[0].adults': 'Use a whole number' } },
        { status: 400, statusText: 'Bad request' },
      );
    expect(c.error()).toContain('rooms[0].adults');
    c.search();
    http
      .expectOne(api + '/search/quotations')
      .flush({ message: 'Selected hotel was not found' }, { status: 404, statusText: 'Not found' });
    expect(c.error()).toContain('Selected hotel');
  });
  it('marks previous results when the form changes and explains truncation', () => {
    setup();
    fill();
    c.search();
    http
      .expectOne(api + '/search/quotations')
      .flush({ ...response, truncated: true, matchingOptions: 25 });
    c.form.controls.city.setValue('Galle');
    fixture.detectChanges();
    expect(c.edited()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('The form has changed');
    expect(fixture.nativeElement.textContent).toContain('of 25 options');
  });
});
