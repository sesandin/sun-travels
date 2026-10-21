import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { ReservationBooking } from './reservation-booking';
import { ReservationDirectory } from './reservation-directory';
import { ReservationDetail } from './reservation-detail';
import { ReservationService } from '../../services/reservation.service';
import { Reservation, ReservationQuote } from '../../models/reservation.model';
import { AuthState } from '../../services/auth-state';
import { routes } from '../../app.routes';

const customer = { customerId: 9, name: 'Traveller', phone: '+94112345678', email: null };
const quote: ReservationQuote = {
  hotelId: 1,
  hotelName: 'Ocean',
  contractId: 4,
  checkIn: '2026-10-25',
  checkOut: '2026-10-26',
  nights: 1,
  currencyCode: 'USD',
  totalPrice: 230,
  rooms: [
    {
      offeringId: 7,
      roomTypeName: 'Double',
      adults: 2,
      totalPrice: 230,
      nightlyPrices: [{ occupiedDate: '2026-10-25', sellingPrice: 230 }],
    },
  ],
};
const saved: Reservation = {
  reservationId: 101,
  customerId: 9,
  customerName: 'Traveller',
  phone: customer.phone,
  email: null,
  checkIn: quote.checkIn,
  checkOut: quote.checkOut,
  nights: 1,
  status: 'CONFIRMED',
  createdAt: '2026-10-15T12:00:00',
  currencyCode: 'USD',
  totalPrice: 230,
  rooms: [
    { ...quote.rooms[0], reservationItemId: 201, hotelId: 1, hotelName: 'Ocean', contractId: 4 },
  ],
};

describe('Reservation review and confirmation', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<ReservationBooking>>;
  let c: ReservationBooking;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [ReservationBooking],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(ReservationService).saveDraft(quote);
    fixture = TestBed.createComponent(ReservationBooking);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });
  it('looks up existing customers and selects the persisted ID', () => {
    c.query.setValue('Traveller');
    c.searchCustomers();
    c.searchCustomers();
    const req = http.expectOne(
      (r) => r.url === '/api/customers' && r.params.get('query') === 'Traveller',
    );
    req.flush({ customers: [customer], page: 0, size: 20, totalPages: 1, totalElements: 1 });
    c.select(c.customers()[0]);
    expect(c.selected()?.customerId).toBe(9);
    expect(c.quote()).toEqual(quote);
  });
  it('registers and selects a customer without losing accommodation', () => {
    c.registration.setValue({ name: 'Traveller', phone: customer.phone, email: '' });
    c.register();
    const req = http.expectOne('/api/customers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Traveller', phone: customer.phone, email: null });
    req.flush(customer);
    expect(c.selected()?.customerId).toBe(9);
    expect(c.quote()).toEqual(quote);
    expect(c.customerBusy()).toBe(false);
  });
  it('preserves customer validation and registration failure feedback', () => {
    c.registration.setValue({ name: ' ', phone: '', email: '' });
    c.register();
    http.expectNone('/api/customers');
    c.registration.setValue({ name: 'Traveller', phone: customer.phone, email: '' });
    c.register();
    http
      .expectOne('/api/customers')
      .flush(
        { message: 'Email conflicts with another customer' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(c.customerError()).toContain('conflicts');
    expect(c.quote()).toEqual(quote);
  });
  it('requires an explicit price acceptance and prevents duplicate submissions', () => {
    c.select(customer);
    c.confirm();
    http.expectNone('/api/reservations');
    c.accepted.setValue(true);
    c.confirm();
    c.confirm();
    const req = http.expectOne('/api/reservations');
    expect(req.request.body.customerId).toBe(9);
    expect(req.request.body.acceptedTotal).toBe(230);
    expect(req.request.body.rooms).toEqual([{ offeringId: 7, adults: 2 }]);
    req.flush(
      { message: 'Sold out', code: 'UNAVAILABLE' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(c.error()).toContain('Sold out');
    expect(c.busy()).toBe(false);
    expect(c.accepted.value).toBe(false);
  });
  it('confirms customized mixed rooms with exact offering IDs and adult counts', () => {
    const mixed: ReservationQuote = {
      ...quote,
      totalPrice: 402.5,
      rooms: [
        {
          ...quote.rooms[0],
          offeringId: 8,
          roomTypeName: 'Sea View',
          adults: 1,
          totalPrice: 172.5,
          nightlyPrices: [{ occupiedDate: quote.checkIn, sellingPrice: 172.5 }],
        },
        { ...quote.rooms[0], offeringId: 7, adults: 2 },
      ],
    };
    TestBed.inject(ReservationService).saveDraft(mixed);
    c.quote.set(TestBed.inject(ReservationService).draft());
    c.select(customer);
    c.accepted.setValue(true);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.confirm();
    const req = http.expectOne('/api/reservations');
    expect(req.request.body.rooms).toEqual([
      { offeringId: 8, adults: 1 },
      { offeringId: 7, adults: 2 },
    ]);
    expect(req.request.body.acceptedTotal).toBe(402.5);
    expect(req.request.body.contractId).toBe(4);
    req.flush({
      ...saved,
      totalPrice: 402.5,
      rooms: mixed.rooms.map((r, i) => ({
        ...r,
        reservationItemId: 201 + i,
        hotelId: 1,
        hotelName: 'Ocean',
        contractId: 4,
      })),
    });
    expect(navigate).toHaveBeenCalledWith(['/reservations', 101], { state: { confirmed: true } });
    expect(TestBed.inject(ReservationService).draft()).toBeNull();
  });

  it('preserves entered adults above ten and shows changed server occupancy at confirmation', () => {
    c.quote.set({
      ...quote,
      totalPrice: 1380,
      rooms: [
        {
          ...quote.rooms[0],
          adults: 12,
          totalPrice: 1380,
          nightlyPrices: [{ occupiedDate: quote.checkIn, sellingPrice: 1380 }],
        },
      ],
    });
    c.select(customer);
    c.accepted.setValue(true);
    c.confirm();
    const req = http.expectOne('/api/reservations');
    expect(req.request.body.rooms).toEqual([{ offeringId: 7, adults: 12 }]);
    req.flush(
      {
        code: 'UNAVAILABLE',
        message: 'Room capacity changed; the selected room now allows at most 6 adults.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(c.error()).toContain('capacity changed');
    expect(c.accepted.value).toBe(false);
    expect(c.busy()).toBe(false);
    expect(c.quote()?.rooms[0].adults).toBe(12);
  });
  it('cancels the review, clears the draft and returns to search without a reservation request', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.select(customer);
    c.accepted.setValue(true);
    fixture.detectChanges();
    const cancel = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.trim() === 'Cancel')!;
    expect(cancel.disabled).toBe(false);
    cancel.click();
    expect(TestBed.inject(ReservationService).draft()).toBeNull();
    expect(c.quote()).toBeNull();
    expect(c.accepted.value).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/search']);
    http.expectNone('/api/reservations');
  });
  it('prevents canceling while confirmation or customer operations are in progress', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.busy.set(true);
    c.cancelBooking();
    expect(TestBed.inject(ReservationService).draft()).not.toBeNull();
    c.busy.set(false);
    c.customerBusy.set(true);
    c.cancelBooking();
    expect(TestBed.inject(ReservationService).draft()).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });
  it('shows previous and current amounts and requires fresh acceptance after every change', () => {
    c.select(customer);
    c.accepted.setValue(true);
    c.confirm();
    const updated = { ...quote, totalPrice: 250, rooms: [{ ...quote.rooms[0], totalPrice: 250 }] };
    http
      .expectOne('/api/reservations')
      .flush(
        { code: 'PRICE_CHANGED', message: 'Price changed', quotation: updated },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();
    expect(c.previous()?.totalPrice).toBe(230);
    expect(c.quote()?.totalPrice).toBe(250);
    expect(fixture.nativeElement.textContent).toContain('Previously: 230.00 USD');
    expect(fixture.nativeElement.textContent).toContain('Current: 250.00 USD');
    c.confirm();
    http.expectNone('/api/reservations');
    c.accepted.setValue(true);
    c.confirm();
    const req = http.expectOne('/api/reservations');
    expect(req.request.body.acceptedTotal).toBe(250);
    req.flush(
      {
        code: 'PRICE_CHANGED',
        message: 'Changed again',
        quotation: { ...updated, totalPrice: 260 },
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(c.accepted.value).toBe(false);
    expect(c.previous()?.totalPrice).toBe(250);
  });
  it('navigates to the confirmed reference and clears the draft on success', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.select(customer);
    c.accepted.setValue(true);
    c.confirm();
    http.expectOne('/api/reservations').flush(saved);
    expect(navigate).toHaveBeenCalledWith(['/reservations', 101], { state: { confirmed: true } });
    expect(TestBed.inject(ReservationService).draft()).toBeNull();
  });
  it('keeps the draft and clears acceptance on uncertain network failure', () => {
    c.select(customer);
    c.accepted.setValue(true);
    c.confirm();
    http.expectOne('/api/reservations').error(new ProgressEvent('error'));
    expect(c.busy()).toBe(false);
    expect(c.accepted.value).toBe(false);
    expect(TestBed.inject(ReservationService).draft()).toEqual(quote);
  });
  it('presents quotation, customer and acknowledgment as distinct review steps', () => {
    const steps: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.workflow-step');
    expect(Array.from(steps, (step) => step.getAttribute('data-step'))).toEqual(['1', '2', '3']);
    expect(fixture.nativeElement.querySelector('.quoted-total')?.textContent).toContain('230.00');
    expect(fixture.nativeElement.querySelector('#confirmation-guidance')?.textContent).toContain(
      'Select a customer',
    );
    const confirm: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-describedby="confirmation-guidance"]',
    );
    expect(confirm.disabled).toBe(true);
    expect(c.quote()).toEqual(quote);
    http.expectNone('/api/reservations');
  });
});

describe('Reservation directory and details', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ReservationDirectory, ReservationDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '101']]) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads the directory with customer and status filters and selling totals', () => {
    const f = TestBed.createComponent(ReservationDirectory);
    f.detectChanges();
    http
      .expectOne((r) => r.url === '/api/reservations')
      .flush({ reservations: [saved], page: 0, size: 10, totalElements: 1 });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('230.00 USD');
    f.componentInstance.query.setValue('Traveller');
    f.componentInstance.status.setValue('CONFIRMED');
    f.componentInstance.search();
    http
      .expectOne(
        (r) => r.params.get('query') === 'Traveller' && r.params.get('status') === 'CONFIRMED',
      )
      .flush({ reservations: [saved], page: 0, size: 10, totalElements: 1 });
  });
  it('loads immutable nightly prices and cancels only after confirmation', () => {
    const f = TestBed.createComponent(ReservationDetail);
    f.detectChanges();
    http.expectOne('/api/reservations/101').flush(saved);
    f.detectChanges();
    const dialog = f.componentInstance.dialog().nativeElement;
    dialog.showModal = vi.fn();
    dialog.close = vi.fn();
    f.componentInstance.openCancel();
    expect(dialog.showModal).toHaveBeenCalled();
    http.expectNone('/api/reservations/101/cancel');
    f.componentInstance.cancel();
    f.componentInstance.cancel();
    const req = http.expectOne('/api/reservations/101/cancel');
    expect(req.request.method).toBe('POST');
    req.flush({ ...saved, status: 'CANCELLED' });
    f.detectChanges();
    expect(f.componentInstance.reservation()?.status).toBe('CANCELLED');
    expect(f.nativeElement.textContent).toContain('Inventory has been released');
    expect(f.componentInstance.reservation()?.totalPrice).toBe(230);
    expect(dialog.close).toHaveBeenCalled();
  });
  it('shows cancellation errors and preserves the saved reservation', () => {
    const f = TestBed.createComponent(ReservationDetail);
    f.detectChanges();
    http.expectOne('/api/reservations/101').flush(saved);
    f.detectChanges();
    f.componentInstance.dialog().nativeElement.close = vi.fn();
    f.componentInstance.cancel();
    http
      .expectOne('/api/reservations/101/cancel')
      .flush({ message: 'Please retry' }, { status: 503, statusText: 'Unavailable' });
    expect(f.componentInstance.error()).toContain('server could not complete');
    expect(f.componentInstance.reservation()?.status).toBe('CONFIRMED');
  });

  const operationalWarnings = [
    {
      hotelId: 1,
      hotelName: 'Ocean',
      code: 'HOTEL_INACTIVE' as const,
      message:
        'This hotel is currently inactive. Please verify whether this reservation can still be honored.',
    },
    {
      hotelId: 1,
      hotelName: 'Ocean',
      code: 'CONTRACT_SUSPENDED' as const,
      message:
        'This reservation is confirmed, but its hotel contract is currently suspended. Please verify the booking arrangements with the hotel.',
    },
  ];
  it.each([
    { warnings: operationalWarnings.slice(0, 1) },
    { warnings: operationalWarnings.slice(1) },
    { warnings: operationalWarnings },
  ])('displays operational warnings without changing confirmed status', ({ warnings }) => {
    const f = TestBed.createComponent(ReservationDetail);
    f.detectChanges();
    http.expectOne('/api/reservations/101').flush({ ...saved, warnings });
    f.detectChanges();
    expect(f.nativeElement.querySelector('.reservation-warning').textContent).toContain(
      'This reservation remains confirmed',
    );
    for (const warning of warnings) expect(f.nativeElement.textContent).toContain(warning.message);
    expect(
      f.nativeElement
        .querySelector('.reservation-identity .reservation-status')
        ?.textContent.trim(),
    ).toBe('Confirmed');
    const cancelDialog: HTMLDialogElement = f.nativeElement.querySelector('dialog');
    expect(cancelDialog.getAttribute('aria-labelledby')).toBe('cancel-reservation-title');
    expect(f.componentInstance.reservation()?.status).toBe('CONFIRMED');
    expect(f.nativeElement.textContent).toContain('230.00 USD');
    expect(f.nativeElement.textContent).toContain('Cancel reservation');
    http.expectNone('/api/reservations/101/cancel');
  });
  it('does not display operational warnings on cancelled or unaffected reservations', () => {
    const f = TestBed.createComponent(ReservationDetail);
    f.detectChanges();
    http.expectOne('/api/reservations/101').flush({ ...saved, warnings: [] });
    f.detectChanges();
    expect(f.nativeElement.querySelector('.reservation-warning')).toBeNull();
    f.componentInstance.reservation.set({
      ...saved,
      status: 'CANCELLED',
      warnings: operationalWarnings,
    });
    f.detectChanges();
    expect(f.nativeElement.querySelector('.reservation-warning')).toBeNull();
    expect(f.nativeElement.querySelector('.reservation-status').textContent.trim()).toBe(
      'Cancelled',
    );
  });
  it('refreshes independent warnings and removes them only after explicit cancellation', () => {
    const f = TestBed.createComponent(ReservationDetail);
    f.detectChanges();
    http.expectOne('/api/reservations/101').flush({ ...saved, warnings: operationalWarnings });
    f.detectChanges();
    f.componentInstance.load();
    http
      .expectOne('/api/reservations/101')
      .flush({ ...saved, warnings: operationalWarnings.slice(1) });
    f.detectChanges();
    expect(f.nativeElement.textContent).not.toContain(operationalWarnings[0].message);
    expect(f.nativeElement.textContent).toContain(operationalWarnings[1].message);
    const dialog = f.componentInstance.dialog().nativeElement;
    dialog.showModal = vi.fn();
    dialog.close = vi.fn();
    f.componentInstance.openCancel();
    expect(dialog.showModal).toHaveBeenCalled();
    http.expectNone('/api/reservations/101/cancel');
    f.componentInstance.cancel();
    http
      .expectOne('/api/reservations/101/cancel')
      .flush({ ...saved, status: 'CANCELLED', warnings: [] });
    f.detectChanges();
    expect(f.nativeElement.querySelector('.reservation-warning')).toBeNull();
    expect(f.componentInstance.reservation()?.totalPrice).toBe(230);
  });
  it('shows an operational list indicator and explanatory messages in the details popup', () => {
    const f = TestBed.createComponent(ReservationDirectory);
    f.detectChanges();
    const affected = { ...saved, warnings: operationalWarnings };
    http
      .expectOne((r) => r.url === '/api/reservations')
      .flush({
        reservations: [
          affected,
          { ...saved, reservationId: 102, status: 'CANCELLED', warnings: operationalWarnings },
        ],
        page: 0,
        size: 10,
        totalElements: 2,
      });
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('.reservation-warning-indicator')).toHaveLength(1);
    expect(f.nativeElement.querySelector('.reservation-warning-indicator').textContent).toContain(
      'Verify hotel arrangements',
    );
    const dialog = f.componentInstance.detailsDialog().nativeElement;
    dialog.showModal = vi.fn();
    f.componentInstance.openDetails(affected);
    f.detectChanges();
    for (const warning of operationalWarnings)
      expect(f.nativeElement.querySelector('.reservation-warning').textContent).toContain(
        warning.message,
      );
    expect(f.nativeElement.querySelector('.reservation-status').textContent).toContain('Confirmed');
  });

  it('opens the selected reservation using its View button and explains why arrangements need verification', () => {
    const f = TestBed.createComponent(ReservationDirectory);
    f.detectChanges();
    const affected = { ...saved, warnings: operationalWarnings };
    http
      .expectOne((r) => r.url === '/api/reservations')
      .flush({
        reservations: [affected, { ...saved, reservationId: 102, warnings: [] }],
        page: 0,
        size: 10,
        totalElements: 2,
      });
    f.detectChanges();
    const dialog = f.componentInstance.detailsDialog().nativeElement;
    dialog.showModal = vi.fn();
    dialog.close = vi.fn();
    const buttons = f.nativeElement.querySelectorAll(
      '.reservation-view',
    ) as NodeListOf<HTMLButtonElement>;
    expect(buttons).toHaveLength(2);
    expect(buttons[0].textContent?.trim()).toBe('View / Manage');
    expect(buttons[0].getAttribute('aria-label')).toBe('View details for reservation #101');
    buttons[0].click();
    f.detectChanges();
    expect(dialog.showModal).toHaveBeenCalled();
    expect(f.componentInstance.selected()?.reservationId).toBe(101);
    expect(dialog.textContent).toContain('Hotel inactive');
    expect(dialog.textContent).toContain('Contract suspended');
    for (const warning of operationalWarnings)
      expect(dialog.textContent).toContain(warning.message);
    expect(dialog.textContent).toContain('This reservation remains confirmed');
    buttons[1].click();
    f.detectChanges();
    expect(f.componentInstance.selected()?.reservationId).toBe(102);
    expect(dialog.querySelector('.reservation-warning')).toBeNull();
    http.expectNone('/api/reservations/101/cancel');
  });

  it('applies stay dates with status and customer filters and retains them when paging', () => {
    const f = TestBed.createComponent(ReservationDirectory);
    f.detectChanges();
    http
      .expectOne((r) => r.url === '/api/reservations')
      .flush({ reservations: [], page: 0, size: 10, totalElements: 40 });
    const c = f.componentInstance;
    c.query.setValue('Traveller');
    c.status.setValue('CONFIRMED');
    c.stayFrom.setValue('2026-10-25');
    c.stayTo.setValue('2026-10-27');
    c.search();
    const first = http.expectOne(
      (r) =>
        r.url === '/api/reservations' &&
        r.params.get('stayFrom') === '2026-10-25' &&
        r.params.get('stayTo') === '2026-10-27',
    );
    expect(first.request.params.get('query')).toBe('Traveller');
    expect(first.request.params.get('status')).toBe('CONFIRMED');
    expect(first.request.params.get('page')).toBe('0');
    first.flush({ reservations: [], page: 0, size: 10, totalElements: 40 });
    c.stayTo.setValue('2026-11-01');
    c.load(1);
    const next = http.expectOne((r) => r.params.get('page') === '1');
    expect(next.request.params.get('stayTo')).toBe('2026-10-27');
    next.flush({ reservations: [], page: 1, size: 10, totalElements: 40 });
    c.stayFrom.setValue('');
    c.stayTo.setValue('');
    c.search();
    const cleared = http.expectOne((r) => r.url === '/api/reservations');
    expect(cleared.request.params.has('stayFrom')).toBe(false);
    expect(cleared.request.params.has('stayTo')).toBe(false);
    cleared.flush({ reservations: [], page: 0, size: 10, totalElements: 0 });
  });
  it('rejects reversed or malformed date filters before sending a request', () => {
    const f = TestBed.createComponent(ReservationDirectory);
    f.detectChanges();
    http
      .expectOne((r) => r.url === '/api/reservations')
      .flush({ reservations: [], page: 0, size: 10, totalElements: 0 });
    const c = f.componentInstance;
    c.stayFrom.setValue('2026-10-28');
    c.stayTo.setValue('2026-10-25');
    c.search();
    f.detectChanges();
    expect(c.filters.invalid).toBe(true);
    expect(f.nativeElement.textContent).toContain('Stay from on or before Stay to');
    http.expectNone('/api/reservations');
    c.stayFrom.setValue('2026-02-30');
    c.stayTo.setValue('');
    c.search();
    expect(c.filters.invalid).toBe(true);
    http.expectNone('/api/reservations');
  });

  it('explains contract and multiple offering suspensions without duplicate tracking or changing status', () => {
    const f = TestBed.createComponent(ReservationDetail);
    f.detectChanges();
    const warnings = [
      ...operationalWarnings,
      {
        hotelId: 1,
        hotelName: 'Ocean',
        code: 'CONTRACT_SUSPENDED',
        contractId: 4,
        message:
          'Its hotel contract is currently suspended. Please verify the booking arrangements with the hotel.',
      },
      {
        hotelId: 1,
        hotelName: 'Ocean',
        code: 'OFFERING_SUSPENDED',
        contractId: 4,
        offeringId: 7,
        roomTypeName: 'Standard',
        message: 'This room offering has been suspended for new sales.',
      },
      {
        hotelId: 1,
        hotelName: 'Ocean',
        code: 'OFFERING_SUSPENDED',
        contractId: 4,
        offeringId: 8,
        roomTypeName: 'Sea View',
        message: 'This room offering has been suspended for new sales.',
      },
    ];
    http.expectOne('/api/reservations/101').flush({ ...saved, warnings });
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('.reservation-warning li')).toHaveLength(5);
    for (const text of [
      'Contract suspended',
      'Room offering suspended',
      'Standard',
      'Sea View',
      'Confirmed',
    ])
      expect(f.nativeElement.textContent).toContain(text);
    f.componentInstance.load();
    http.expectOne('/api/reservations/101').flush({ ...saved, warnings: [] });
    f.detectChanges();
    expect(f.nativeElement.querySelector('.reservation-warning')).toBeNull();
  });

  it('automatically resets filters to page one and cancels obsolete requests', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(ReservationDirectory);
    try {
      f.detectChanges();
      const initial = http.expectOne((r) => r.url === '/api/reservations');
      expect(initial.request.params.get('size')).toBe('10');
      initial.flush({ reservations: [saved], page: 0, size: 10, totalElements: 25 });
      f.componentInstance.load(2);
      const old = http.expectOne((r) => r.params.get('page') === '2');
      f.componentInstance.stayPeriod.setValue('UPCOMING');
      expect(old.cancelled).toBe(true);
      expect(f.componentInstance.result()).toBeNull();
      vi.advanceTimersByTime(250);
      const filtered = http.expectOne((r) => r.params.get('stayPeriod') === 'UPCOMING');
      expect(filtered.request.params.get('page')).toBe('0');
      filtered.flush({ reservations: [saved], page: 0, size: 10, totalElements: 25 });
      f.detectChanges();
      expect(f.nativeElement.textContent).toContain('Page 1 of 3');
      f.componentInstance.status.setValue('CONFIRMED');
      f.componentInstance.query.setValue('Traveller');
      vi.advanceTimersByTime(250);
      const combined = http.expectOne((r) => r.params.get('query') === 'Traveller');
      expect(combined.request.params.get('status')).toBe('CONFIRMED');
      expect(combined.request.params.get('stayPeriod')).toBe('UPCOMING');
      expect(combined.request.params.get('page')).toBe('0');
      combined.flush({ reservations: [], page: 0, size: 10, totalElements: 0 });
      f.detectChanges();
      expect(f.nativeElement.textContent).toContain('No reservations match these filters');
      f.componentInstance.stayPeriod.setValue('PAST');
      vi.advanceTimersByTime(250);
      http
        .expectOne((r) => r.params.get('stayPeriod') === 'PAST')
        .flush({ message: 'Temporarily unavailable' }, { status: 503, statusText: 'Unavailable' });
      f.detectChanges();
      expect(f.componentInstance.loading()).toBe(false);
      expect(f.componentInstance.result()).toBeNull();
      expect(f.componentInstance.error()).not.toBe('');
    } finally {
      f.destroy();
      vi.useRealTimers();
    }
  });
  it('renders the final five records on page three with correct totals and disabled Next', () => {
    const f = TestBed.createComponent(ReservationDirectory);
    f.detectChanges();
    http
      .expectOne((r) => r.url === '/api/reservations')
      .flush({ reservations: [], page: 0, size: 10, totalElements: 25 });
    f.componentInstance.load(2);
    http
      .expectOne((r) => r.params.get('page') === '2')
      .flush({
        reservations: Array.from({ length: 5 }, (_, i) => ({ ...saved, reservationId: 5 - i })),
        page: 2,
        size: 10,
        totalElements: 25,
      });
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('tbody tr')).toHaveLength(5);
    expect(f.nativeElement.textContent).toContain('Page 3 of 3');
    const next = Array.from(
      f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Next')!;
    expect(next.disabled).toBe(true);
  });

  it('reloads All Stays when a quick period change returns to the original filter', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(ReservationDirectory);
    try {
      f.detectChanges();
      http
        .expectOne((r) => r.url === '/api/reservations')
        .flush({ reservations: [saved], page: 0, size: 10, totalElements: 1 });
      f.componentInstance.stayPeriod.setValue('UPCOMING');
      f.componentInstance.stayPeriod.setValue('');
      expect(f.componentInstance.result()).toBeNull();
      vi.advanceTimersByTime(250);
      const request = http.expectOne((r) => r.url === '/api/reservations');
      expect(request.request.params.has('stayPeriod')).toBe(false);
      expect(request.request.params.get('page')).toBe('0');
      request.flush({ reservations: [saved], page: 0, size: 10, totalElements: 1 });
      expect(f.componentInstance.result()?.reservations).toHaveLength(1);
    } finally {
      f.destroy();
      vi.useRealTimers();
    }
  });
  it('reissues a cancelled initial query when Stay Period returns to All Stays', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(ReservationDirectory);
    try {
      f.detectChanges();
      const initial = http.expectOne((r) => r.url === '/api/reservations');
      f.componentInstance.stayPeriod.setValue('PAST');
      expect(initial.cancelled).toBe(true);
      f.componentInstance.stayPeriod.setValue('');
      vi.advanceTimersByTime(250);
      const replacement = http.expectOne((r) => r.url === '/api/reservations');
      expect(replacement.request.params.has('stayPeriod')).toBe(false);
      replacement.flush({ reservations: [saved], page: 0, size: 10, totalElements: 1 });
      expect(f.componentInstance.loading()).toBe(false);
      expect(f.componentInstance.result()?.totalElements).toBe(1);
    } finally {
      f.destroy();
      vi.useRealTimers();
    }
  });
  it('sends each period correctly and does not duplicate a manually submitted request', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(ReservationDirectory);
    try {
      f.detectChanges();
      http
        .expectOne((r) => r.url === '/api/reservations')
        .flush({ reservations: [], page: 0, size: 10, totalElements: 0 });
      for (const period of ['UPCOMING', 'ONGOING', 'PAST', ''] as const) {
        f.componentInstance.stayPeriod.setValue(period);
        f.componentInstance.search();
        const request = http.expectOne((r) => r.url === '/api/reservations');
        expect(request.request.params.get('stayPeriod')).toBe(period || null);
        vi.advanceTimersByTime(250);
        http.expectNone('/api/reservations');
        request.flush({ reservations: [], page: 0, size: 10, totalElements: 0 });
      }
    } finally {
      f.destroy();
      vi.useRealTimers();
    }
  });

  it('applies the actual Stay Period dropdown immediately and updates the rendered rows', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(ReservationDirectory);
    try {
      f.detectChanges();
      http
        .expectOne((r) => r.url === '/api/reservations')
        .flush({ reservations: [saved], page: 0, size: 10, totalElements: 1 });
      f.detectChanges();
      const select = Array.from(
        f.nativeElement.querySelectorAll('select') as NodeListOf<HTMLSelectElement>,
      ).find((element) =>
        Array.from(element.options).some((option) => option.value === 'UPCOMING'),
      )!;
      select.value = 'PAST';
      select.dispatchEvent(new Event('change', { bubbles: true }));
      const request = http.expectOne((r) => r.url === '/api/reservations');
      expect(request.request.params.get('stayPeriod')).toBe('PAST');
      expect(request.request.params.get('page')).toBe('0');
      request.flush({ reservations: [], page: 0, size: 10, totalElements: 0 });
      f.detectChanges();
      expect(f.nativeElement.querySelectorAll('tbody tr')).toHaveLength(0);
      expect(f.nativeElement.textContent).toContain('No reservations match these filters');
      vi.advanceTimersByTime(250);
      http.expectNone('/api/reservations');
    } finally {
      f.destroy();
      vi.useRealTimers();
    }
  });

  it('Search submits Past and replaces November stays with only the server-filtered October rows', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(ReservationDirectory);
    try {
      f.detectChanges();
      const november = {
        ...saved,
        reservationId: 118,
        checkIn: '2026-11-15',
        checkOut: '2026-11-18',
      };
      const ongoing = {
        ...saved,
        reservationId: 117,
        checkIn: '2026-10-16',
        checkOut: '2026-10-17',
      };
      const past = { ...saved, reservationId: 116, checkIn: '2026-10-15', checkOut: '2026-10-16' };
      http
        .expectOne((r) => r.url === '/api/reservations')
        .flush({ reservations: [november, ongoing, past], page: 0, size: 10, totalElements: 3 });
      f.detectChanges();
      const select = Array.from(
        f.nativeElement.querySelectorAll('select') as NodeListOf<HTMLSelectElement>,
      ).find((element) => Array.from(element.options).some((option) => option.value === 'PAST'))!;
      select.value = 'PAST';
      select.dispatchEvent(new Event('change', { bubbles: true }));
      const dropdownRequest = http.expectOne((r) => r.url === '/api/reservations');
      f.nativeElement
        .querySelector('form.filters')
        .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      expect(dropdownRequest.cancelled).toBe(true);
      const search = http.expectOne((r) => r.url === '/api/reservations');
      expect(search.request.params.get('stayPeriod')).toBe('PAST');
      expect(search.request.params.get('page')).toBe('0');
      expect(search.request.params.get('size')).toBe('10');
      search.flush({ reservations: [past], page: 0, size: 10, totalElements: 1 });
      f.detectChanges();
      expect(f.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
      expect(f.nativeElement.querySelector('tbody').textContent).toContain('2026-10-16');
      expect(f.nativeElement.querySelector('tbody').textContent).not.toContain('2026-11-18');
      expect(f.nativeElement.querySelector('tbody').textContent).not.toContain('2026-10-17');
      vi.advanceTimersByTime(250);
      http.expectNone('/api/reservations');
    } finally {
      f.destroy();
      vi.useRealTimers();
    }
  });
});

describe('Reservation route permissions', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    sessionStorage.clear();
  });
  for (const role of ['ADMIN', 'AGENT'] as const) {
    it(`allows ${role} to review a reservation with the shared shell routes`, async () => {
      TestBed.inject(AuthState).setUser({ userId: 1, username: 'staff', fullName: 'Staff', role });
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/reservations/new');
      expect(router.url).toBe('/reservations/new');
      expect(routes.find((r) => r.path === 'reservations')?.canActivate).toEqual(
        routes.find((r) => r.path === 'reservations/new')?.canActivate,
      );
      expect(routes.find((r) => r.path === 'reservations/:id')?.canActivate).toEqual(
        routes.find((r) => r.path === 'reservations/new')?.canActivate,
      );
    });
  }
  it('redirects anonymous booking navigation to login', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/reservations/new');
    expect(router.url).toBe('/login?returnUrl=%2Freservations%2Fnew');
  });
});
