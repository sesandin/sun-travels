import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ReservationService } from '../../services/reservation.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuotationSearch } from './quotation-search';
import {
  QuotationResponse,
  HotelPage,
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
describe('Hotel-paginated accommodation search', () => {
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
    http.expectOne(api + '/search/limits').flush(limits);
    http.expectOne(api + '/search/room-types').flush([{ roomTypeId: 3, name: 'Double' }]);
    c.form.patchValue({ checkIn: '2026-10-24', nights: 3, city: 'Colombo' });
    c.rooms.at(0).controls.adults.setValue(2);
    fixture.detectChanges();
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('opens suggestion panels on focus and dismisses them outside or with Escape', () => {
    c.cityMatches.set([{ city: 'Colombo', country: 'Sri Lanka' }]);
    c.cityLookupDone.set(true);
    fixture.detectChanges();
    const city = fixture.nativeElement.querySelector('#search-city') as HTMLInputElement;
    city.focus();
    fixture.detectChanges();
    const menu = fixture.nativeElement.querySelector('#city-matches').parentElement as HTMLElement;
    expect(menu.hidden).toBe(false);
    expect(menu.querySelector('strong')!.textContent).toBe('Colombo');
    city.click();
    fixture.detectChanges();
    expect(menu.hidden).toBe(false);
    document.body.click();
    fixture.detectChanges();
    expect(menu.hidden).toBe(true);
    city.click();
    fixture.detectChanges();
    expect(menu.hidden).toBe(false);
    city.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(menu.hidden).toBe(true);
    expect(c.form.controls.city.value).toBe('Colombo');
  });

  it('debounces city lookup, keeps countries distinct and sends the selected country on every page', async () => {
    vi.useFakeTimers();
    c.form.controls.city.setValue('');
    c.cityQuery.setValue('S');
    c.hotelQuery.setValue('O');
    await vi.advanceTimersByTimeAsync(300);
    http.expectNone((r) => r.url === api + '/search/cities' || r.url === api + '/search/hotels');
    c.cityQuery.setValue('Sp');
    await vi.advanceTimersByTimeAsync(299);
    http.expectNone((r) => r.url === api + '/search/cities');
    await vi.advanceTimersByTimeAsync(1);
    const req = http.expectOne((r) => r.url === api + '/search/cities');
    expect(req.request.params.get('term')).toBe('Sp');
    req.flush([
      { city: 'Springfield', country: 'United States' },
      { city: 'Springfield', country: 'Sri Lanka' },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#city-matches').textContent).toContain(
      'United States',
    );
    expect(fixture.nativeElement.querySelector('#city-matches').textContent).toContain('Sri Lanka');
    expect(c.form.hasError('destination')).toBe(true); // Typed text alone is not a selection.
    c.selectCity({ city: 'Springfield', country: 'Sri Lanka' });
    c.search();
    const first = http.expectOne(api + '/search/hotel-pages');
    expect(first.request.body.country).toBe('Sri Lanka');
    first.flush(page(7, 20));
    c.loadMore();
    const more = http.expectOne((r) => r.url === api + '/search/hotel-pages');
    expect(more.request.body.country).toBe('Sri Lanka');
    more.flush(page(21));
  });

  it('cancels old city requests and handles empty and error states', async () => {
    vi.useFakeTimers();
    c.cityQuery.setValue('Ga');
    await vi.advanceTimersByTimeAsync(300);
    const old = http.expectOne((r) => r.url === api + '/search/cities');
    c.cityQuery.setValue('Co');
    expect(old.cancelled).toBe(true);
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne((r) => r.url === api + '/search/cities').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching destinations.');
    c.cityQuery.setValue('Ma');
    await vi.advanceTimersByTimeAsync(300);
    http
      .expectOne((r) => r.url === api + '/search/cities')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(c.cityLoading()).toBe(false);
    expect(c.cityError()).toContain('try again');
  });

  it('filters hotel requests by location and cancels requests when that location changes', async () => {
    vi.useFakeTimers();
    c.selectCity({ city: 'Springfield', country: 'Sri Lanka' });
    c.hotelQuery.setValue('Oc');
    await vi.advanceTimersByTimeAsync(300);
    const old = http.expectOne((r) => r.url === api + '/search/hotels');
    expect(old.request.params.get('city')).toBe('Springfield');
    expect(old.request.params.get('country')).toBe('Sri Lanka');
    c.selectCity({ city: 'Springfield', country: 'United States' });
    expect(old.cancelled).toBe(true);
    await vi.advanceTimersByTimeAsync(300);
    const current = http.expectOne((r) => r.url === api + '/search/hotels');
    expect(current.request.params.get('country')).toBe('United States');
    current.flush([]);
  });

  it('clears incompatible hotels and old destination selections without losing hotel-only search', () => {
    c.selectCity({ city: 'Springfield', country: 'Sri Lanka' });
    c.selectHotel({ hotelId: 7, name: 'Ocean', city: 'Springfield', country: 'Sri Lanka' });
    expect(c.form.controls.hotelId.value).toBe(7);
    search(page(7, 20));
    c.selectCity({ city: 'Springfield', country: 'United States' });
    expect(c.form.controls.hotelId.value).toBeNull();
    expect(c.response()).toBeNull();
    expect(c.nextAfterHotelId()).toBeNull();
    c.hotelQuery.setValue('');
    c.cityQuery.setValue('New text');
    expect(c.selectedCity()).toBeNull();
    expect(c.form.controls.city.value).toBe('');
    expect(c.form.controls.country.value).toBeNull();
    expect(c.form.hasError('destination')).toBe(true);
    c.clearCity();
    c.selectHotel({ hotelId: 9, name: 'Hotel only', city: 'Colombo', country: 'Sri Lanka' });
    expect(c.form.valid).toBe(true);
    c.search();
    const req = http.expectOne(api + '/search/hotel-pages');
    expect(req.request.body.city).toBeNull();
    expect(req.request.body.hotelId).toBe(9);
    req.flush(page(9));
  });

  it('debounces hotel-name lookup and never loads the hotel catalogue during setup', async () => {
    vi.useFakeTimers();
    http.expectNone(api + '/search/filters');
    c.hotelQuery.setValue('Oc');
    await vi.advanceTimersByTimeAsync(299);
    http.expectNone((r) => r.url === api + '/search/hotels');
    await vi.advanceTimersByTimeAsync(1);
    const req = http.expectOne((r) => r.url === api + '/search/hotels');
    expect(req.request.params.get('term')).toBe('Oc');
    req.flush([{ hotelId: 7, name: 'Ocean Hotel', city: 'Colombo' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#hotel-matches strong').textContent).toBe(
      'Ocean Hotel',
    );
    expect(fixture.nativeElement.querySelector('#hotel-matches span').textContent).toContain(
      'Colombo',
    );
    expect(c.form.controls.hotelId.value).toBeNull();
  });

  it('cancels stale hotel requests immediately when newer text is typed', async () => {
    vi.useFakeTimers();
    c.hotelQuery.setValue('Oc');
    await vi.advanceTimersByTimeAsync(300);
    const old = http.expectOne((r) => r.url === api + '/search/hotels');
    c.hotelQuery.setValue('Sea');
    expect(old.cancelled).toBe(true);
    await vi.advanceTimersByTimeAsync(300);
    http
      .expectOne((r) => r.url === api + '/search/hotels')
      .flush([{ hotelId: 9, name: 'Sea Hotel', city: 'Colombo' }]);
    expect(c.hotelMatches().map((h) => h.hotelId)).toEqual([9]);
  });

  it('selects a real ID and clears it when the name is edited or selection is cleared', () => {
    c.form.controls.city.setValue('');
    const hotel = { hotelId: 7, name: 'Ocean Hotel', city: 'Colombo' };
    c.selectHotel(hotel);
    expect(c.form.controls.hotelId.value).toBe(7);
    expect(c.hotelQuery.value).toBe('Ocean Hotel');
    expect(c.form.valid).toBe(true);
    http.expectNone(api + '/search/hotels/7');
    c.hotelQuery.setValue('Different');
    expect(c.form.controls.hotelId.value).toBeNull();
    expect(c.form.hasError('destination')).toBe(true);
    c.selectHotel(hotel);
    c.clearHotel();
    expect(c.selectedHotel()).toBeNull();
    expect(c.hotelQuery.value).toBe('');
    expect(c.form.controls.hotelId.value).toBeNull();
  });

  it('resolves selected IDs independently and cancels an obsolete ID lookup', () => {
    c.form.controls.hotelId.setValue(7);
    const old = http.expectOne(api + '/search/hotels/7');
    c.form.controls.hotelId.setValue(9);
    expect(old.cancelled).toBe(true);
    http
      .expectOne(api + '/search/hotels/9')
      .flush({ hotelId: 9, name: 'Sea Hotel', city: 'Colombo' });
    expect(c.selectedHotel()?.hotelId).toBe(9);
    expect(c.hotelQuery.value).toBe('Sea Hotel');
    expect(c.hotelMatches()).toEqual([]);
  });

  it('handles blank text, no matches and hotel API errors', async () => {
    vi.useFakeTimers();
    c.hotelQuery.setValue('   ');
    await vi.advanceTimersByTimeAsync(300);
    http.expectNone((r) => r.url === api + '/search/hotels');
    c.hotelQuery.setValue('Missing');
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne((r) => r.url === api + '/search/hotels').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching hotels.');
    c.hotelQuery.setValue('Other');
    await vi.advanceTimersByTimeAsync(300);
    expect(c.hotelLoading()).toBe(true);
    http
      .expectOne((r) => r.url === api + '/search/hotels')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(c.hotelLoading()).toBe(false);
    expect(c.hotelError()).toContain('try again');
    expect(c.form.valid).toBe(true); // City-only search remains usable.
  });

  it.each([
    ['Colombo', null, true],
    ['', 7, true],
    ['Colombo', 7, true],
    ['   ', 7, true],
    ['   ', null, false],
    ['', null, false],
  ] as const)('validates destination city %s and hotel %s', (city, hotelId, valid) => {
    c.form.patchValue({ city, hotelId });
    if (hotelId !== null)
      http
        .expectOne(api + '/search/hotels/' + hotelId)
        .flush({ hotelId, name: 'Ocean Hotel', city: 'Colombo' });
    fixture.detectChanges();
    expect(c.form.hasError('destination')).toBe(!valid);
    expect(c.form.valid).toBe(valid);
    if (!valid) {
      c.search();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Select a suggested city or hotel.');
      http.expectNone(api + '/search/hotel-pages');
    } else {
      c.search();
      const req = http.expectOne(api + '/search/hotel-pages');
      expect(req.request.body.city).toBe(city.trim() || null);
      expect(req.request.body.hotelId).toBe(hotelId);
      req.flush({ ...page(), hotels: [] });
    }
  });

  function page(hotelId = 7, cursor: number | null = null, count = 1): HotelPage {
    const quotation = {
      ...response.quotations[0],
      hotelId,
      rooms: Array.from({ length: count }, (_, i) => ({
        ...response.quotations[0].rooms[0],
        roomNumber: i + 1,
        adults: i ? 1 : 2,
        allocatedRoomsForOffering: count,
        totalPrice: i ? 402.5 : 805,
      })),
      totalPrice: count === 1 ? 805 : 1207.5,
    };
    return {
      businessDate: limits.businessDate,
      checkIn: quotation.checkIn,
      checkOut: quotation.checkOut,
      nights: 3,
      nextAfterHotelId: cursor,
      hotels: [
        {
          hotelId,
          hotelName: 'Ocean Hotel',
          city: 'Colombo',
          offerings: [
            {
              offeringId: 8,
              roomTypeId: 3,
              contractedRooms: 3,
              availabilityStatus: 'AVAILABLE',
              unavailableOption: null,
              quotation,
              roomChoices: {
                hotelId,
                hotelName: 'Ocean Hotel',
                contractId: 4,
                offeringId: 8,
                roomTypeName: 'Double',
                maxAdults: 2,
                availableRoomsForStay: 2,
                currencyCode: 'USD',
                rooms: quotation.rooms.map((r) => ({
                  roomNumber: r.roomNumber,
                  adults: r.adults,
                  totalPrice: r.totalPrice,
                })),
              },
            },
          ],
        },
      ],
    };
  }
  function search(data = page()) {
    c.search();
    http.expectOne(api + '/search/hotel-pages').flush(data);
    fixture.detectChanges();
  }
  function mixedChoices() {
    const data = page(7, null, 2);
    const first = data.hotels[0].offerings[0].roomChoices;
    return [
      first,
      {
        ...first,
        offeringId: 9,
        roomTypeName: 'Single',
        maxAdults: 1,
        rooms: [
          { roomNumber: 1, adults: 2, totalPrice: null },
          { roomNumber: 2, adults: 1, totalPrice: 172.5 },
        ],
      },
    ];
  }
  function openCustom() {
    c.addRoom();
    search(page(7, null, 2));
    c.openCustomization();
    http
      .expectOne(api + '/search/quotations')
      .flush({ ...response, selectionOfferings: mixedChoices() });
    c.customize(mixedChoices()[0]);
    fixture.detectChanges();
  }
  it('loads real reference data and derives exclusive checkout without changing adults', () => {
    expect(c.limits()?.businessDate).toBe('2026-10-15');
    expect(c.form.controls.checkOut.value).toBe('2026-10-27');
    c.form.controls.nights.setValue(1);
    expect(c.form.controls.checkOut.value).toBe('2026-10-25');
    expect(c.rooms.at(0).controls.adults.value).toBe(2);
  });
  it.each([0, -1, 1.5, 2147483648, null])('rejects invalid adults %s', (adults) => {
    c.rooms.at(0).controls.adults.setValue(adults);
    c.search();
    expect(c.form.invalid).toBe(true);
    http.expectNone(api + '/search/hotel-pages');
  });
  it.each(['2026-10-14', 'bad-date'])('rejects invalid check-in %s', (checkIn) => {
    c.form.controls.checkIn.setValue(checkIn);
    c.search();
    expect(c.form.invalid).toBe(true);
    http.expectNone(api + '/search/hotel-pages');
  });
  it('preserves optional filters and blocks duplicate initial searches', () => {
    c.form.controls.city.setValue(' Colombo ');
    c.addRoom();
    c.search();
    c.search();
    const req = http.expectOne(api + '/search/hotel-pages');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      checkIn: '2026-10-24',
      checkOut: '2026-10-27',
      city: 'Colombo',
      hotelId: null,
      roomTypeId: null,
      rooms: [{ adults: 2 }, { adults: 1 }],
    });
    expect(req.request.params.has('afterHotelId')).toBe(false);
    req.flush(page(7, null, 2));
    expect(c.searching()).toBe(false);
  });
  it('renders seasonal selling prices, adult capacity, inventory and original currency', () => {
    search();
    const text = fixture.nativeElement.textContent;
    for (const value of [
      'Ocean Hotel',
      'Colombo',
      'Double',
      '805.00 USD',
      '345.00 USD',
      'SEASONAL',
      'Maximum 2 adults',
      '2 rooms available throughout stay',
    ])
      expect(text).toContain(value);
    expect(text).toContain('Results are not sorted by price');
    expect(text).not.toContain('Markup');
    expect(text).not.toContain('Hotelier rate');
    expect(fixture.nativeElement.querySelector('details').open).toBe(false);
    expect(fixture.nativeElement.querySelector('.unavailable-option')).toBeNull();
    expect(fixture.nativeElement.querySelector('.unavailable-toggle')).toBeNull();
  });
  it('groups two offerings under one hotel card', () => {
    const data = page();
    const offering = data.hotels[0].offerings[0];
    data.hotels[0].offerings.push({
      ...offering,
      offeringId: 9,
      roomChoices: { ...offering.roomChoices, offeringId: 9 },
      quotation: {
        ...offering.quotation,
        rooms: offering.quotation.rooms.map((r) => ({
          ...r,
          offeringId: 9,
          roomTypeName: 'Sea View',
        })),
      },
    });
    search(data);
    expect(fixture.nativeElement.querySelectorAll('.hotel-card')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.quotation')).toHaveLength(2);
  });
  it('appends pages, sends the candidate cursor and prevents duplicate requests', () => {
    search(page(7, 20));
    c.loadMore();
    c.loadMore();
    const req = http.expectOne((r) => r.url === api + '/search/hotel-pages');
    expect(req.request.params.get('afterHotelId')).toBe('20');
    expect(req.request.body.rooms).toEqual([{ adults: 2 }]);
    req.flush(page(21));
    fixture.detectChanges();
    expect(c.hotelGroups().map((h) => h.hotelId)).toEqual([7, 21]);
    expect(fixture.nativeElement.querySelectorAll('.hotel-card')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.load-more')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('All matching hotels have been checked');
  });
  it('deduplicates hotel cards when a repeated hotel is returned', () => {
    search(page(7, 20));
    c.loadMore();
    http.expectOne((r) => r.url === api + '/search/hotel-pages').flush(page(7));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.hotel-card')).toHaveLength(1);
    expect(c.groupedQuotations()).toHaveLength(1);
  });
  it('continues through an empty available page with a cursor', () => {
    search({ ...page(), hotels: [], nextAfterHotelId: 20 });
    expect(fixture.nativeElement.textContent).toContain('More hotels remain to check');
    expect(fixture.nativeElement.querySelector('.load-more')).not.toBeNull();
    c.loadMore();
    http.expectOne((r) => r.url === api + '/search/hotel-pages').flush(page(21));
    expect(c.hotelGroups()[0].hotelId).toBe(21);
  });
  it('distinguishes final empty results from an empty intermediate page', () => {
    search({ ...page(), hotels: [] });
    expect(c.nextAfterHotelId()).toBeNull();
    expect(fixture.nativeElement.querySelector('.load-more')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No single room type fits your request');
  });
  it('keeps earlier pages and retries the same cursor after a load error', () => {
    search(page(7, 20));
    c.loadMore();
    http
      .expectOne((r) => r.url === api + '/search/hotel-pages')
      .flush({ message: 'Try again' }, { status: 503, statusText: 'Unavailable' });
    expect(c.hotelGroups()).toHaveLength(1);
    expect(c.nextAfterHotelId()).toBe(20);
    expect(c.error()).toContain('try again');
    c.loadMore();
    const retry = http.expectOne((r) => r.url === api + '/search/hotel-pages');
    expect(retry.request.params.get('afterHotelId')).toBe('20');
    retry.flush(page(21));
    expect(c.error()).toBe('');
  });
  it.each(['city', 'checkIn', 'nights', 'hotelId', 'roomTypeId', 'adults', 'rooms'])(
    'clears results and cursor when %s changes',
    (field) => {
      search(page(7, 20));
      if (field === 'city') c.form.controls.city.setValue('Galle');
      if (field === 'checkIn') c.form.controls.checkIn.setValue('2026-10-25');
      if (field === 'nights') c.form.controls.nights.setValue(4);
      if (field === 'hotelId') {
        c.form.controls.hotelId.setValue(7);
        http
          .expectOne(api + '/search/hotels/7')
          .flush({ hotelId: 7, name: 'Ocean Hotel', city: 'Colombo' });
      }
      if (field === 'roomTypeId') c.form.controls.roomTypeId.setValue(3);
      if (field === 'adults') c.rooms.at(0).controls.adults.setValue(1);
      if (field === 'rooms') c.addRoom();
      expect(c.response()).toBeNull();
      expect(c.nextAfterHotelId()).toBeNull();
      c.loadMore();
      http.expectNone((r) => r.url === api + '/search/hotel-pages');
    },
  );
  it('ignores an obsolete response after the criteria change and a new search starts', () => {
    c.search();
    const old = http.expectOne(api + '/search/hotel-pages');
    c.form.controls.city.setValue('Galle');
    c.search();
    const current = http.expectOne(api + '/search/hotel-pages');
    old.flush(page(7, 20));
    expect(c.response()).toBeNull();
    expect(c.searching()).toBe(true);
    current.flush(page(21));
    expect(c.hotelGroups().map((h) => h.hotelId)).toEqual([21]);
  });
  it('passes the displayed quotation unchanged to the existing reservation draft', () => {
    const data = page();
    search(data);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (fixture.nativeElement.querySelector('.quote-actions button') as HTMLButtonElement).click();
    const draft = TestBed.inject(ReservationService).draft()!;
    const quote = data.hotels[0].offerings[0].quotation;
    expect(draft).toMatchObject({
      hotelId: quote.hotelId,
      contractId: quote.contractId,
      checkIn: quote.checkIn,
      checkOut: quote.checkOut,
      currencyCode: quote.currencyCode,
      totalPrice: quote.totalPrice,
      rooms: quote.rooms.map((r) => ({
        offeringId: r.offeringId,
        adults: r.adults,
        totalPrice: r.totalPrice,
        nightlyPrices: r.nightlyPrices.map((n) => ({
          occupiedDate: n.occupiedDate,
          sellingPrice: n.sellingPrice,
        })),
      })),
    });
    expect(navigate).toHaveBeenCalledWith(['/reservations/new']);
    http.expectNone(api + '/reservations');
    TestBed.inject(ReservationService).clearDraft();
  });
  it('refuses unavailable quotations and stale results', () => {
    const save = vi.spyOn(TestBed.inject(ReservationService), 'saveDraft');
    c.reserve(unavailableOption);
    expect(save).not.toHaveBeenCalled();
    search();
    c.form.controls.city.setValue('Galle');
    c.reserve(response.quotations[0]);
    expect(save).not.toHaveBeenCalled();
  });
  it('loads mixed-room choices explicitly and preserves selected IDs and adult counts', () => {
    openCustom();
    expect(c.roomOptions(0).map((o) => o.offeringId)).toEqual([8]);
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.choose(1, { target: { value: '9' } } as unknown as Event);
    const req = http.expectOne(api + '/search/selection');
    expect(req.request.body.rooms).toEqual([
      { offeringId: 8, adults: 2 },
      { offeringId: 9, adults: 1 },
    ]);
    req.flush({
      ...response.quotations[0],
      rooms: page(7, null, 2).hotels[0].offerings[0].quotation.rooms,
    });
    expect(c.customQuote()).not.toBeNull();
    expect(c.selectedIds()).toEqual([8, 9]);
    http.expectNone(api + '/reservations');
  });
  it('supports explicit customization even when no whole-request offering is available', () => {
    c.addRoom();
    search({ ...page(), hotels: [] });
    c.openCustomization();
    http
      .expectOne(api + '/search/quotations')
      .flush({ ...response, quotations: [], selectionOfferings: mixedChoices() });
    expect(c.customizationGroups()).toHaveLength(1);
    c.customize(mixedChoices()[0]);
    expect(c.roomOptions(1).map((o) => o.offeringId)).toEqual([8, 9]);
  });
  it('prevents duplicate customization loads and ignores choices after criteria change', () => {
    c.addRoom();
    search(page(7, null, 2));
    c.openCustomization();
    c.openCustomization();
    const pending = http.expectOne(api + '/search/quotations');
    c.form.controls.city.setValue('Galle');
    pending.flush({ ...response, selectionOfferings: mixedChoices() });
    expect(c.response()).toBeNull();
    expect(c.customizationOpen()).toBe(false);
    expect(c.selectionLoading()).toBe(false);
  });
  it('ignores custom quotation responses after closing customization', () => {
    openCustom();
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    c.choose(1, { target: { value: '9' } } as unknown as Event);
    const req = http.expectOne(api + '/search/selection');
    c.closeCustomization();
    req.flush(response.quotations[0]);
    expect(c.customQuote()).toBeNull();
    expect(c.customizationOpen()).toBe(false);
  });
  it('enforces shared inventory without silently changing selections', () => {
    openCustom();
    c.customOptions.update((options) => options.map((o) => ({ ...o, availableRoomsForStay: 1 })));
    c.choose(0, { target: { value: '8' } } as unknown as Event);
    expect(c.roomOptions(1).map((o) => o.offeringId)).toEqual([9]);
    c.choose(1, { target: { value: '8' } } as unknown as Event);
    expect(c.selectedIds()).toEqual([8, 0]);
    http.expectNone(api + '/search/selection');
  });
  it('applies a specific offering occupancy limit without changing adult counts', () => {
    c.form.patchValue({ hotelId: 7, roomTypeId: 3 });
    http
      .expectOne(api + '/search/hotels/7')
      .flush({ hotelId: 7, name: 'Ocean Hotel', city: 'Colombo' });
    http
      .expectOne((r) => r.url === api + '/search/occupancy')
      .flush([{ offeringId: 8, roomTypeId: 3, roomTypeName: 'Double', maxAdults: 1 }]);
    expect(c.rooms.at(0).controls.adults.value).toBe(2);
    expect(c.form.invalid).toBe(true);
    c.search();
    http.expectNone(api + '/search/hotel-pages');
  });
});
