import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ReservationService } from '../../services/reservation.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuotationSearch } from './quotation-search';
import {
  QuotationResponse,
  SearchLimits,
  UnavailableAccommodation,
} from '../../models/quotation.model';

const api = '/api';
const limits: SearchLimits = {
  businessDate: '2026-10-15',
  maxRooms: 8,
  maxNights: 31,
  maxAdvanceDays: 730,
  maxResults: 20,
  allocationPolicy: 'INDIVIDUAL_ROOM_TYPES',
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
  unavailableOptions: [],
  matchingUnavailableOptions: 0,
  unavailableTruncated: false,
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
      availabilityStatus: 'AVAILABLE',
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
              sellingPrice: 230,
            },
            {
              occupiedDate: '2026-10-25',
              source: 'SEASONAL',
              seasonalRateId: 10,
              sellingPrice: 345,
            },
            {
              occupiedDate: '2026-10-26',
              source: 'BASE',
              seasonalRateId: null,
              sellingPrice: 230,
            },
          ],
        },
      ],
    },
  ],
};
const unavailableOption: UnavailableAccommodation = {
  hotelId: 9,
  hotelName: 'Garden Hotel',
  city: 'Colombo',
  contractId: 9,
  contractCode: 'C009',
  currencyCode: 'USD',
  availabilityStatus: 'NOT_AVAILABLE',
  offerings: [
    {
      offeringId: 10,
      roomTypeId: 3,
      roomTypeName: 'Garden View',
      maxAdults: 2,
      availableRoomsForStay: 1,
    },
  ],
  reasons: [{ code: 'INSUFFICIENT_INVENTORY', message: 'Only 1 room available; 2 requested.' }],
  indicativeTotalPrice: 690,
};
describe('Search and nightly quotations', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<QuotationSearch>>;
  let c: QuotationSearch;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [QuotationSearch],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QuotationSearch);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  // Configures the component and its test dependencies.
  function setup() {
    http.expectOne(api + '/search/limits').flush(limits);
    http.expectOne(api + '/search/filters').flush({
      hotels: [{ hotelId: 7, name: 'Ocean Hotel', city: 'Colombo' }],
      roomTypes: [{ roomTypeId: 3, name: 'Double' }],
    });
    fixture.detectChanges();
  }
  // Populates the form with valid test input.
  function fill() {
    c.form.patchValue({ checkIn: '2026-10-24', nights: 3 });
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
    expect(c.form.invalid).toBe(true);
    fill();
    c.form.controls.nights.setValue(0);
    expect(c.form.invalid).toBe(true);
    c.form.controls.nights.setValue(-1);
    expect(c.form.invalid).toBe(true);
    fill();
    c.form.controls.nights.setValue(38);
    expect(c.form.invalid).toBe(true);
    http.expectNone(api + '/search/quotations');
  });
  it('rejects fractional, null and out-of-range adults', () => {
    setup();
    fill();
    for (const adults of [null, 0, -1, 1.5, 2147483648, Number.MAX_SAFE_INTEGER]) {
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
    http
      .expectOne((r) => r.url === api + '/search/occupancy')
      .flush([{ offeringId: 8, roomTypeId: 3, roomTypeName: 'Double', maxAdults: 2 }]);
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
      '805.00 USD',
      'Double',
      '2 rooms available',
      '1 room requested',
      'BASE',
      'SEASONAL',
      '345.00 USD',
      'Available',
    ])
      expect(text).toContain(value);
    const details: HTMLDetailsElement = fixture.nativeElement.querySelector('details');
    expect(details.open).toBe(false);
    details.open = true;
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(fixture.nativeElement.querySelectorAll('.room-availability')).toHaveLength(1);
    expect(text).not.toContain('Contracted rooms');
    expect(text).not.toContain('Hotelier rate');
    expect(text).not.toContain('Markup');
    expect(text).not.toContain('C004');
  });
  it('keeps search informational and offers a separate reservation review action', () => {
    setup();
    fill();
    c.search();
    http.expectOne(api + '/search/quotations').flush(response);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('does not book or hold rooms');
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    );
    const button = buttons.find((b) => b.textContent?.trim() === 'Book This Room')!;
    expect(button.disabled).toBe(false);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button.click();
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    http.expectNone('/api/reservations');
    TestBed.inject(ReservationService).clearDraft();
  });
  it('shows helpful successful empty state', () => {
    setup();
    fill();
    c.search();
    http
      .expectOne(api + '/search/quotations')
      .flush({ ...response, quotations: [], matchingOptions: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'No Single Room Type Can Accommodate This Request',
    );
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
  it('starts booking from the exact quotation and refuses edited search results', () => {
    setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.reserve(response.quotations[0]);
    expect(TestBed.inject(ReservationService).draft()?.rooms[0].offeringId).toBe(8);
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    navigate.mockClear();
    c.edited.set(true);
    c.reserve(response.quotations[0]);
    expect(navigate).not.toHaveBeenCalled();
    TestBed.inject(ReservationService).clearDraft();
  });
  it('shows unavailable options by default after available quotations and supports filtering', () => {
    setup();
    fill();
    c.search();
    http.expectOne(api + '/search/quotations').flush({
      ...response,
      unavailableOptions: [unavailableOption],
      matchingUnavailableOptions: 1,
    });
    fixture.detectChanges();
    const toggle: HTMLInputElement = fixture.nativeElement.querySelector(
      '.unavailable-toggle input',
    );
    expect(toggle.checked).toBe(true);
    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.quotation, .unavailable-option'),
    ) as HTMLElement[];
    expect(cards).toHaveLength(2);
    expect(cards[0].textContent).toContain('Ocean Hotel');
    expect(cards[1].textContent).toContain('Garden Hotel');
    expect(cards[1].textContent).toContain('Not Available');
    expect(cards[1].textContent).toContain('Only 1 room available; 2 requested.');
    expect(cards[1].textContent).toContain('690.00 USD');
    expect(cards[1].textContent).toContain('Non-bookable');
    expect(cards[1].querySelector('button')?.disabled).toBe(true);
    toggle.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.unavailable-option')).toBeNull();
  });
  it('offers to reveal unavailable reasons when no complete allocation exists', () => {
    setup();
    fill();
    c.showUnavailable.setValue(false);
    c.search();
    http.expectOne(api + '/search/quotations').flush({
      ...response,
      quotations: [],
      matchingOptions: 0,
      unavailableOptions: [{ ...unavailableOption, indicativeTotalPrice: null }],
      matchingUnavailableOptions: 1,
    });
    fixture.detectChanges();
    const reveal = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Show unavailable options and reasons'))!;
    reveal.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.unavailable-option')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.indicative-price')).toBeNull();
  });
  it('refuses to start reservations from unavailable options even when invoked directly', () => {
    setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const draft = vi.spyOn(TestBed.inject(ReservationService), 'saveDraft');
    c.reserve(unavailableOption);
    expect(draft).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    c.reserve(response.quotations[0]);
    expect(draft).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    TestBed.inject(ReservationService).clearDraft();
  });
  it('renders and reserves complete quotations from an older API without availability fields', () => {
    setup();
    fill();
    c.search();
    const { availabilityStatus, ...legacyQuote } = response.quotations[0];
    const {
      unavailableOptions,
      matchingUnavailableOptions,
      unavailableTruncated,
      ...legacyResponse
    } = response;
    http
      .expectOne(api + '/search/quotations')
      .flush({ ...legacyResponse, quotations: [legacyQuote] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.quotation')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Ocean Hotel');
    expect(fixture.nativeElement.textContent).toContain('805.00 USD');
    expect(c.response()?.unavailableOptions).toEqual([]);
    const reserve = fixture.nativeElement.querySelector(
      '.quote-actions button',
    ) as HTMLButtonElement;
    expect(reserve.disabled).toBe(false);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    reserve.click();
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    TestBed.inject(ReservationService).clearDraft();
  });
  it('never promotes an explicitly unavailable quotation to available', () => {
    setup();
    fill();
    c.search();
    http.expectOne(api + '/search/quotations').flush({
      ...response,
      quotations: [{ ...response.quotations[0], availabilityStatus: 'NOT_AVAILABLE' }],
      unavailableOptions: [unavailableOption],
      matchingUnavailableOptions: 1,
    });
    fixture.detectChanges();
    expect(c.groupedQuotations()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.quotation')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('0 available room-type options');
    c.showUnavailable.setValue(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.unavailable-option button').disabled).toBe(true);
  });
  // Supplies occupancy reference responses for the selected search filters.
  function occupancySelection() {
    setup();
    fill();
    c.form.patchValue({ hotelId: 7, roomTypeId: 3 });
    return http.expectOne((r) => r.url === api + '/search/occupancy');
  }
  const doubleOccupancy = { offeringId: 8, roomTypeId: 3, roomTypeName: 'Double', maxAdults: 2 };
  it('shows the applicable limit and blocks each oversized room without changing its count', () => {
    const request = occupancySelection();
    c.showUnavailable.setValue(false);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('hotelId')).toBe('7');
    expect(request.request.params.get('roomTypeId')).toBe('3');
    expect(request.request.params.get('checkOut')).toBe('2026-10-27');
    c.addRoom();
    c.rooms.at(1).controls.adults.setValue(3);
    request.flush([doubleOccupancy]);
    fixture.detectChanges();
    expect(c.rooms.at(0).valid).toBe(true);
    expect(c.rooms.at(1).controls.adults.hasError('occupancyMax')).toBe(true);
    expect(c.rooms.at(1).controls.adults.value).toBe(3);
    expect(fixture.nativeElement.textContent).toContain(
      'Double allows a maximum of 2 adults per room.',
    );
    expect(fixture.nativeElement.querySelectorAll('.invalid-input')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    c.search();
    http.expectNone(api + '/search/quotations');
    c.rooms.at(1).controls.adults.setValue(2);
    fixture.detectChanges();
    expect(c.form.valid).toBe(true);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
    http.expectNone((r) => r.url === api + '/search/occupancy');
  });
  it('refreshes on hotel, room type and stay changes and cancels obsolete requests', () => {
    const stale = occupancySelection();
    c.showUnavailable.setValue(false);
    c.form.controls.nights.setValue(4);
    expect(stale.cancelled).toBe(true);
    http
      .expectOne(
        (r) => r.url === api + '/search/occupancy' && r.params.get('checkOut') === '2026-10-28',
      )
      .flush([doubleOccupancy]);
    c.rooms.at(0).controls.adults.setValue(3);
    expect(c.form.invalid).toBe(true);
    for (const change of [{ hotelId: 9 }, { roomTypeId: 4 }, { checkIn: '2026-10-25' }]) {
      c.form.patchValue(change);
      expect(c.occupancyMax()).toBeNull();
      expect(c.rooms.at(0).controls.adults.hasError('occupancyMax')).toBe(false);
      http
        .expectOne((r) => r.url === api + '/search/occupancy')
        .flush([{ ...doubleOccupancy, maxAdults: 4 }]);
    }
    expect(c.rooms.at(0).controls.adults.value).toBe(3);
    expect(c.form.valid).toBe(true);
  });
  it('defers ambiguous limits and preserves optional-filter and empty-result searches', () => {
    occupancySelection().flush([
      doubleOccupancy,
      { ...doubleOccupancy, offeringId: 9, maxAdults: 4 },
    ]);
    c.rooms.at(0).controls.adults.setValue(3);
    expect(c.occupancyMax()).toBeNull();
    expect(c.form.valid).toBe(true);
    expect(c.occupancyGuidance()).toContain('contractual adult limits');
    c.form.controls.roomTypeId.setValue(null);
    expect(c.occupancyGuidance()).toContain('Actual capacity depends');
    http.expectNone((r) => r.url === api + '/search/occupancy');
    c.form.controls.roomTypeId.setValue(3);
    http.expectOne((r) => r.url === api + '/search/occupancy').flush([]);
    expect(c.form.valid).toBe(true);
    expect(c.occupancyGuidance()).toContain('No eligible offering');
    c.form.controls.hotelId.setValue(null);
    expect(c.form.valid).toBe(true);
    http.expectNone((r) => r.url === api + '/search/occupancy');
  });
  it('does not impose one capacity when multiple offerings are eligible', () => {
    c.showUnavailable.setValue(false);
    occupancySelection().flush([doubleOccupancy, { ...doubleOccupancy, offeringId: 9 }]);
    expect(c.occupancyMax()).toBeNull();
    c.addRoom();
    c.rooms.at(1).controls.adults.setValue(3);
    expect(c.rooms.at(1).controls.adults.hasError('occupancyMax')).toBe(false);
    expect(c.rooms.at(0).valid).toBe(true);
    c.form.controls.hotelId.setValue(null);
    expect(c.form.valid).toBe(true);
    expect(c.rooms.at(1).controls.adults.value).toBe(3);
    http.expectNone((r) => r.url === api + '/search/occupancy');
  });
  it('allows occupancy diagnostics when unavailable options are enabled without weakening global input limits', () => {
    c.showUnavailable.setValue(false);
    occupancySelection().flush([doubleOccupancy]);
    c.rooms.at(0).controls.adults.setValue(4);
    fixture.detectChanges();
    expect(c.form.invalid).toBe(true);
    c.showUnavailable.setValue(true);
    fixture.detectChanges();
    expect(c.form.valid).toBe(true);
    expect(c.rooms.at(0).controls.adults.value).toBe(4);
    c.search();
    const request = http.expectOne(api + '/search/quotations');
    expect(request.request.body.rooms).toEqual([{ adults: 4 }]);
    request.flush({
      ...response,
      quotations: [],
      unavailableOptions: [
        {
          ...unavailableOption,
          indicativeTotalPrice: null,
          reasons: [{ code: 'MAX_ADULT_OCCUPANCY', message: 'Maximum 2 adults per room.' }],
        },
      ],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Maximum 2 adults per room.');
    c.rooms.at(0).controls.adults.setValue(2147483648);
    expect(c.form.invalid).toBe(true);
    c.rooms.at(0).controls.adults.setValue(4);
    c.showUnavailable.setValue(false);
    fixture.detectChanges();
    expect(c.form.invalid).toBe(true);
  });
  it('clears stale occupancy on invalid dates and lets authoritative search handle lookup failures', () => {
    occupancySelection().flush([doubleOccupancy]);
    c.form.controls.nights.setValue(0);
    expect(c.occupancyMax()).toBeNull();
    http.expectNone((r) => r.url === api + '/search/occupancy');
    c.form.controls.nights.setValue(3);
    http.expectOne((r) => r.url === api + '/search/occupancy').error(new ProgressEvent('error'));
    c.rooms.at(0).controls.adults.setValue(3);
    expect(c.occupancyLoading()).toBe(false);
    expect(c.occupancyError()).toContain('still search');
    expect(c.form.valid).toBe(true);
    c.search();
    http.expectOne(api + '/search/quotations').flush({ ...response, quotations: [] });
  });

  it('calculates exclusive checkout and never changes entered adult counts', () => {
    setup();
    fill();
    c.rooms.at(0).controls.adults.setValue(7);
    c.form.controls.nights.setValue(5);
    expect(c.form.controls.checkOut.value).toBe('2026-10-29');
    expect(c.rooms.at(0).controls.adults.value).toBe(7);
    expect(c.form.valid).toBe(true);
    for (const nights of [null, 0, 1.5, 32]) {
      c.form.controls.nights.setValue(nights);
      expect(c.form.invalid).toBe(true);
    }
  });
  it('renders independent Standard and Sea View cards for the same contract', () => {
    setup();
    fill();
    c.addRoom();
    c.search();
    const q = response.quotations[0],
      r = q.rooms[0];
    http.expectOne(api + '/search/quotations').flush({
      ...response,
      quotations: [
        { ...q, totalPrice: 362.25, rooms: [{ ...r, roomTypeName: 'Standard', offeringId: 7 }] },
        { ...q, totalPrice: 569.25, rooms: [{ ...r, roomTypeName: 'Sea View', offeringId: 8 }] },
      ],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.quotation')).toHaveLength(2);
    for (const text of ['Standard', 'Sea View', '362.25 USD', '569.25 USD'])
      expect(fixture.nativeElement.textContent).toContain(text);
  });
  it('quotes exact custom choices and preserves selected offerings', () => {
    setup();
    fill();
    c.addRoom();
    c.search();
    const choices = [7, 8].map((id, i) => ({
      hotelId: 7,
      hotelName: 'Ocean Hotel',
      contractId: 4,
      offeringId: id,
      roomTypeName: i ? 'Sea View' : 'Standard',
      maxAdults: 2,
      availableRoomsForStay: 1,
      currencyCode: 'USD',
      rooms: [
        { roomNumber: 1, adults: 2, totalPrice: i ? 379.5 : 241.5 },
        { roomNumber: 2, adults: 1, totalPrice: i ? 189.75 : 120.75 },
      ],
    }));
    http.expectOne(api + '/search/quotations').flush({ ...response, selectionOfferings: choices });
    c.customize(choices[0]);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    expect(c.optionReason(choices[1], 1)).toContain('inventory');
    c.choose(1, { target: { value: '7' } } as unknown as Event);
    const req = http.expectOne(api + '/search/selection');
    expect(req.request.body.rooms).toEqual([
      { offeringId: 8, adults: 2 },
      { offeringId: 7, adults: 1 },
    ]);
    const q = response.quotations[0],
      room = q.rooms[0];
    req.flush({
      ...q,
      totalPrice: 500.25,
      rooms: [
        {
          ...room,
          roomNumber: 1,
          offeringId: 8,
          roomTypeName: 'Sea View',
          adults: 2,
          totalPrice: 379.5,
        },
        {
          ...room,
          roomNumber: 2,
          offeringId: 7,
          roomTypeName: 'Standard',
          adults: 1,
          totalPrice: 120.75,
        },
      ],
    });
    expect(c.selectedIds()).toEqual([8, 7]);
    expect(c.customQuote()?.totalPrice).toBe(500.25);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.reserve(c.customQuote()!);
    expect(
      TestBed.inject(ReservationService)
        .draft()
        ?.rooms.map((r) => ({ offeringId: r.offeringId, adults: r.adults })),
    ).toEqual([
      { offeringId: 8, adults: 2 },
      { offeringId: 7, adults: 1 },
    ]);
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    TestBed.inject(ReservationService).clearDraft();
  });
  it('ignores obsolete custom quotation responses and surfaces errors', () => {
    setup();
    fill();
    c.addRoom();
    c.search();
    const choice = {
      hotelId: 7,
      hotelName: 'Ocean',
      contractId: 4,
      offeringId: 8,
      roomTypeName: 'Sea View',
      maxAdults: 2,
      availableRoomsForStay: 2,
      currencyCode: 'USD',
      rooms: [
        { roomNumber: 1, adults: 2, totalPrice: 805 },
        { roomNumber: 2, adults: 1, totalPrice: 402.5 },
      ],
    };
    http.expectOne(api + '/search/quotations').flush({ ...response, selectionOfferings: [choice] });
    c.customize(choice);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.choose(1, { target: { value: '8' } } as unknown as Event);
    const old = http.expectOne(api + '/search/selection');
    c.customize(choice);
    old.flush(response.quotations[0]);
    expect(c.customQuote()).toBeNull();
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.choose(1, { target: { value: '8' } } as unknown as Event);
    http
      .expectOne(api + '/search/selection')
      .flush(
        { message: 'Selected rooms are unavailable' },
        { status: 400, statusText: 'Bad request' },
      );
    expect(c.selectionError()).toContain('unavailable');
    expect(c.selectionLoading()).toBe(false);
  });

  // Builds a quotation response fixture for the requested number of rooms.
  function resultWithRooms(count: number): QuotationResponse {
    const q = response.quotations[0],
      room = q.rooms[0];
    const quoted = Array.from({ length: count }, (_, i) => ({
      ...room,
      roomNumber: i + 1,
      adults: i ? 1 : 2,
      allocatedRoomsForOffering: count,
      availableRoomsForStay: 3,
      remainingRoomsAfterAllocation: 3 - count,
      totalPrice: i ? 402.5 : 805,
      nightlyPrices: room.nightlyPrices.map((n) => ({
        ...n,
        sellingPrice: i ? n.sellingPrice / 2 : n.sellingPrice,
      })),
    }));
    const selectionOfferings = [8, 9].map((id, i) => ({
      hotelId: 7,
      hotelName: 'Ocean Hotel',
      contractId: 4,
      offeringId: id,
      roomTypeName: i ? 'Standard' : 'Sea View',
      maxAdults: 2,
      availableRoomsForStay: 3,
      currencyCode: 'USD',
      rooms: quoted.map((r) => ({
        roomNumber: r.roomNumber,
        adults: r.adults,
        totalPrice: r.totalPrice,
      })),
    }));
    return {
      ...response,
      selectionOfferings,
      quotations: [
        {
          ...q,
          rooms: quoted.map((r) => ({ ...r, roomTypeName: 'Sea View' })),
          totalPrice: quoted.reduce((sum, r) => sum + r.totalPrice, 0),
        },
      ],
    };
  }
  // Executes a room search and supplies its quotation response fixture.
  function searchRooms(count: number) {
    setup();
    fill();
    while (c.rooms.length < count) c.addRoom();
    c.search();
    const data = resultWithRooms(count);
    http.expectOne(api + '/search/quotations').flush(data);
    fixture.detectChanges();
    return data;
  }
  it('hides all customization for one room and books through the existing review flow', () => {
    searchRooms(1);
    expect(fixture.nativeElement.querySelector('.customization-action')).toBeNull();
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Customize Room Selection');
    const button = fixture.nativeElement.querySelector(
      '.quote-actions button',
    ) as HTMLButtonElement;
    expect(button.textContent?.trim()).toBe('Book This Room');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button.click();
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    expect(c.customizationOpen()).toBe(false);
    http.expectNone(api + '/search/selection');
    http.expectNone(api + '/reservations');
    TestBed.inject(ReservationService).clearDraft();
  });
  it.each([2, 3])('keeps customization collapsed and labels booking for %i rooms', (count) => {
    searchRooms(count);
    const trigger = fixture.nativeElement.querySelector(
      '.customization-action button',
    ) as HTMLButtonElement;
    expect(trigger.textContent?.trim()).toBe('Customize Room Selection');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    const button = fixture.nativeElement.querySelector(
      '.quote-actions button',
    ) as HTMLButtonElement;
    expect(button.textContent?.trim()).toBe('Book All ' + count + ' Rooms as Sea View');
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button.click();
    expect(c.customizationOpen()).toBe(false);
    http.expectNone(api + '/search/selection');
    TestBed.inject(ReservationService).clearDraft();
  });
  it('opens scoped customization on demand and closes without booking or holding inventory', () => {
    const data = searchRooms(2);
    c.response.set({
      ...data,
      selectionOfferings: [
        ...data.selectionOfferings!,
        {
          ...data.selectionOfferings![0],
          hotelId: 99,
          contractId: 99,
          offeringId: 99,
          hotelName: 'Other hotel',
        },
      ],
    });
    fixture.detectChanges();
    const trigger = fixture.nativeElement.querySelector(
      '.customization-action button',
    ) as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.selection-panel')).not.toBeNull();
    const group = fixture.nativeElement.querySelector(
      '.selection-panel > button',
    ) as HTMLButtonElement;
    group.click();
    fixture.detectChanges();
    expect(c.customOptions().every((o) => o.hotelId === 7 && o.contractId === 4)).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.selection-panel select')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.quotation')).not.toBeNull();
    (fixture.nativeElement.querySelector('.selection-heading button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    expect(c.selectedIds()).toEqual([]);
    expect(document.activeElement).toBe(trigger);
    http.expectNone(api + '/search/selection');
    http.expectNone(api + '/reservations');
  });
  it('clears customization on edited adults, room count, dates, filters and new searches', () => {
    const data = searchRooms(2);
    c.customize(data.selectionOfferings![0]);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.rooms.at(0).controls.adults.setValue(1);
    fixture.detectChanges();
    expect(c.customizationOpen()).toBe(false);
    expect(c.selectedIds()).toEqual([]);
    expect(c.customQuote()).toBeNull();
    expect(fixture.nativeElement.querySelector('.quotation')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.customization-action')).toBeNull();
    c.removeRoom(1);
    expect(c.customizationOpen()).toBe(false);
    c.search();
    http.expectOne(api + '/search/quotations').flush(resultWithRooms(1));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    expect(c.customOptions()).toEqual([]);
    for (const change of [{ nights: 4 }, { city: 'Galle' }, { checkIn: '2026-10-25' }]) {
      c.form.patchValue(change);
      expect(c.selectedIds()).toEqual([]);
    }
  });
  it('uses stay-wide backend inventory and hides incompatible custom room types', () => {
    const data = searchRooms(2),
      base = data.selectionOfferings![0];
    c.response.set({
      ...data,
      selectionOfferings: [
        base,
        {
          ...base,
          offeringId: 10,
          maxAdults: 1,
          rooms: [
            { roomNumber: 1, adults: 2, totalPrice: null },
            { roomNumber: 2, adults: 1, totalPrice: 402.5 },
          ],
        },
        { ...base, offeringId: 11, availableRoomsForStay: 0 },
      ],
    });
    c.customize(base);
    expect(c.roomOptions(0).map((o) => o.offeringId)).toEqual([8]);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    expect(c.roomOptions(1).map((o) => o.offeringId)).toEqual([8, 10]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.room-type-title').textContent).toContain(
      'Sea View',
    );
    expect(fixture.nativeElement.querySelector('.hotel-context').textContent).toContain(
      'Ocean Hotel',
    );
    expect(fixture.nativeElement.querySelectorAll('.room-availability')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.room-availability').textContent).toContain(
      '3 rooms available throughout stay',
    );
    expect(fixture.nativeElement.querySelector('.quote-total').textContent).toContain(
      '1,207.50 USD',
    );
    expect(fixture.nativeElement.textContent).not.toContain('C004');
    c.closeCustomization();
    http.expectNone(api + '/reservations');
  });
  it('does not restore a custom quotation after closing while a quote request is in flight', () => {
    const data = searchRooms(2);
    c.customize(data.selectionOfferings![0]);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.choose(1, { target: { value: '9' } } as unknown as Event);
    const pending = http.expectOne(api + '/search/selection');
    c.closeCustomization();
    pending.flush(data.quotations[0]);
    expect(c.customQuote()).toBeNull();
    expect(c.selectionLoading()).toBe(false);
    expect(c.customizationOpen()).toBe(false);
  });

  it('closes customization with Escape and hides it after reducing the request to one room', () => {
    searchRooms(2);
    (
      fixture.nativeElement.querySelector('.customization-action button') as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('.selection-panel')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(c.customizationOpen()).toBe(false);
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    c.openCustomization();
    fixture.detectChanges();
    c.removeRoom(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    expect(fixture.nativeElement.querySelector('.customization-action')).toBeNull();
    expect(c.selectedIds()).toEqual([]);
    http.expectNone(api + '/reservations');
  });
  it('does not invent missing inventory or expose a bookable custom option without it', () => {
    searchRooms(2);
    expect(c.knownInventory(undefined)).toBe(false);
    expect(c.knownInventory(-1)).toBe(false);
    const option = resultWithRooms(2).selectionOfferings![0];
    expect(
      c.optionReason({ ...option, availableRoomsForStay: undefined as unknown as number }, 0),
    ).toContain('Inventory is unavailable');
  });

  it('allows adults above ten without filters and protects the integer representation boundary', () => {
    setup();
    fill();
    c.rooms.at(0).controls.adults.setValue(12);
    fixture.detectChanges();
    expect(c.form.valid).toBe(true);
    expect(c.occupancyMax()).toBeNull();
    expect(
      fixture.nativeElement.querySelector('#requested-adults-0').getAttribute('max'),
    ).toBeNull();
    expect(c.occupancyGuidance()).toContain('Actual capacity depends');
    c.search();
    const req = http.expectOne(api + '/search/quotations');
    expect(req.request.body.rooms).toEqual([{ adults: 12 }]);
    req.flush({ ...response, quotations: [] });
    c.rooms.at(0).controls.adults.setValue(2147483647);
    expect(c.form.valid).toBe(true);
    for (const adults of [2147483648, Number.MAX_SAFE_INTEGER, Infinity, 1.5, 0, -1, null]) {
      c.rooms.at(0).controls.adults.setValue(adults);
      expect(c.form.invalid).toBe(true);
    }
  });
  it('uses a specifically identified offering capacity above ten and revalidates lower capacities', () => {
    c.showUnavailable.setValue(false);
    occupancySelection().flush([{ ...doubleOccupancy, maxAdults: 12 }]);
    c.rooms.at(0).controls.adults.setValue(12);
    expect(c.form.valid).toBe(true);
    c.form.controls.roomTypeId.setValue(4);
    http
      .expectOne((r) => r.url === api + '/search/occupancy')
      .flush([{ ...doubleOccupancy, roomTypeId: 4, maxAdults: 6 }]);
    expect(c.rooms.at(0).controls.adults.value).toBe(12);
    expect(c.rooms.at(0).controls.adults.hasError('occupancyMax')).toBe(true);
    c.form.controls.roomTypeId.setValue(null);
    expect(c.form.valid).toBe(true);
  });
  it('preserves differing adults when selecting customized offerings with lower capacities', () => {
    const data = searchRooms(2);
    c.rooms.at(0).controls.adults.setValue(12);
    c.rooms.at(1).controls.adults.setValue(5);
    c.search();
    const choices = [
      {
        ...data.selectionOfferings![0],
        maxAdults: 12,
        rooms: [
          { roomNumber: 1, adults: 12, totalPrice: 4140 },
          { roomNumber: 2, adults: 5, totalPrice: 1725 },
        ],
      },
      {
        ...data.selectionOfferings![1],
        maxAdults: 6,
        rooms: [
          { roomNumber: 1, adults: 12, totalPrice: null },
          { roomNumber: 2, adults: 5, totalPrice: 1725 },
        ],
      },
    ];
    http.expectOne(api + '/search/quotations').flush({ ...data, selectionOfferings: choices });
    c.customize(choices[0]);
    expect(c.roomOptions(0).map((o) => o.offeringId)).toEqual([8]);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.choose(0, { target: { value: '9' } } as unknown as Event);
    expect(c.selectedIds()[0]).toBe(8);
    expect(c.rooms.at(0).controls.adults.value).toBe(12);
    c.choose(1, { target: { value: '9' } } as unknown as Event);
    const req = http.expectOne(api + '/search/selection');
    expect(req.request.body.rooms).toEqual([
      { offeringId: 8, adults: 12 },
      { offeringId: 9, adults: 5 },
    ]);
    req.flush(
      { message: 'Room capacity has changed; search again' },
      { status: 400, statusText: 'Bad request' },
    );
    expect(c.customQuote()).toBeNull();
    expect(c.selectionError()).toContain('capacity');
  });
  it('groups required stay inputs separately from optional filters without changing the request', () => {
    setup();
    fill();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.checkout-preview output')?.textContent).toContain(
      '2026-10-27',
    );
    expect(fixture.nativeElement.querySelector('.optional-filters')?.textContent).toContain(
      'Optional filters',
    );
    expect(fixture.nativeElement.querySelectorAll('.room-number').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.selection-panel')).toBeNull();
    expect(c.selectionRoomPrice(0, 999)).toBeNull();
    expect(c.rooms.at(0).controls.adults.value).toBe(2);
    expect(c.limits()?.maxRooms).toBe(8);
  });
});
