import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  AccommodationQuotation,
  OfferingOccupancy,
  QuotationRequest,
  QuotationResponse,
  QuotationSearchFilters,
  QuotedRoom,
  SearchLimits,
  SelectionOffering,
  UnavailableAccommodation,
} from '../../models/quotation.model';
import { QuotationSearchService } from '../../services/quotation-search.service';
import { apiError } from '../../services/api-error';
import { catchError, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';
import { Router } from '@angular/router';
import { ReservationService } from '../../services/reservation.service';
// Validates a search date using the supported calendar-date format.
const date = (value: string) =>
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(Date.parse(value + 'T00:00:00Z')) &&
  new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) === value;
// This is the API's signed Integer representation bound, not a room occupancy limit.
// Validates an adult count as a representable positive integer.
const adultInteger: ValidatorFn = (c) =>
  Number.isInteger(c.value) && c.value <= 2147483647 ? null : { integer: true };
// Validates a numeric form value as a whole number.
const integer: ValidatorFn = (c) => (Number.isInteger(c.value) ? null : { integer: true });
@Component({
  selector: 'app-quotation-search',
  imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './quotation-search.html',
  styleUrl: './quotation-search.scss',
})
export class QuotationSearch implements OnInit {
  readonly limits = signal<SearchLimits | null>(null);
  readonly hotels = signal<QuotationSearchFilters['hotels']>([]);
  readonly roomTypes = signal<QuotationSearchFilters['roomTypes']>([]);
  readonly setupLoading = signal(false);
  readonly setupError = signal('');
  readonly searching = signal(false);
  readonly error = signal('');
  readonly response = signal<QuotationResponse | null>(null);
  readonly requestedRooms = signal(1);
  readonly showUnavailable = new FormControl(true, { nonNullable: true });
  readonly occupancyOptions = signal<OfferingOccupancy[]>([]);
  readonly occupancyLoading = signal(false);
  readonly occupancyError = signal('');
  readonly occupancyChecked = signal(false);
  readonly occupancyMax = computed(() => {
    const options = this.occupancyOptions();
    return options.length === 1 ? options[0].maxAdults : null;
  });
  readonly occupancyGuidance = computed(() => {
    const options = this.occupancyOptions();
    const max = this.occupancyMax();
    if (max !== null)
      return `${options[0].roomTypeName} allows a maximum of ${max} adults per room.`;
    if (options.length)
      return `Eligible offerings have contractual adult limits (${[...new Set(options.map((o) => o.maxAdults))].sort((a, b) => a - b).join(', ')}). Search will check each room against an eligible offering.`;
    if (this.occupancyChecked())
      return 'No eligible offering found for these filters and dates. Try another selection.';
    return 'Enter a positive whole number. Actual capacity depends on each selected room type.';
  });
  readonly groupedQuotations = computed(() =>
    (this.response()?.quotations ?? [])
      .filter((quote) => quote.availabilityStatus === 'AVAILABLE')
      .map((quote) => {
        const offerings = new Map<
          number,
          {
            offeringId: number;
            roomTypeName: string;
            availableRooms: number;
            rooms: QuotedRoom[];
          }
        >();
        for (const room of quote.rooms) {
          let group = offerings.get(room.offeringId);
          if (!group) {
            group = {
              offeringId: room.offeringId,
              roomTypeName: room.roomTypeName,
              // Stay-wide availability before this quotation's selected rooms are deducted.
              availableRooms: room.availableRoomsForStay,
              rooms: [],
            };
            offerings.set(room.offeringId, group);
          }
          group.rooms.push(room);
        }
        return { ...quote, offeringGroups: [...offerings.values()] };
      }),
  );
  readonly customizationOpen = signal(false);
  readonly selectionError = signal('');
  readonly customOptions = signal<SelectionOffering[]>([]);
  readonly customQuote = signal<AccommodationQuotation | null>(null);
  readonly selectionLoading = signal(false);
  readonly selectedIds = signal<number[]>([]);
  readonly customizationGroups = computed(() => [
    ...new Map(
      (this.response()?.selectionOfferings ?? [])
        .filter((o) => this.knownInventory(o.availableRoomsForStay) && o.availableRoomsForStay > 0)
        .map((o) => [o.contractId, o]),
    ).values(),
  ]);
  readonly edited = signal(false);
  readonly canCustomize = computed(
    () => !!this.response() && this.requestedRooms() > 1 && !this.edited() && !this.searching(),
  );
  readonly latestCheckIn = computed(() => {
    const l = this.limits();
    if (!l) return '';
    return new Date(Date.parse(l.businessDate + 'T00:00:00Z') + l.maxAdvanceDays * 86400000)
      .toISOString()
      .slice(0, 10);
  });
  // Checks requested adults against the selected offering’s known capacity.
  private occupancyValidator(control: AbstractControl) {
    const max = this.occupancyMax();
    return !this.showUnavailable.value && max !== null && control.value > max
      ? { occupancyMax: { max } }
      : null;
  }

  readonly form = new FormGroup(
    {
      checkIn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      nights: new FormControl<number | null>(1, [
        Validators.required,
        Validators.min(1),
        Validators.max(31),
        integer,
      ]),
      checkOut: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      rooms: new FormArray([this.room()]),
      city: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
      hotelId: new FormControl<number | null>(null, [Validators.min(1), integerOptional]),
      roomTypeId: new FormControl<number | null>(null, [Validators.min(1), integerOptional]),
    },
    {
      validators: (group) => {
        const l = this.limits(),
          a = group.get('checkIn')?.value,
          b = group.get('checkOut')?.value;
        if (!a || !b) return null;
        if (!date(a) || !date(b)) return { dates: true };
        const nights = (Date.parse(b + 'T00:00:00Z') - Date.parse(a + 'T00:00:00Z')) / 86400000;
        return l &&
          (a < l.businessDate || a > this.latestCheckIn() || nights < 1 || nights > l.maxNights)
          ? { stay: true }
          : null;
      },
    },
  );
  private readonly router = inject(Router);
  private readonly reservations = inject(ReservationService);
  private readonly service = inject(QuotationSearchService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly customizeTrigger = viewChild<ElementRef<HTMLButtonElement>>('customizeTrigger');
  private readonly customTitle = viewChild<ElementRef<HTMLElement>>('customTitle');
  private lastRequest: QuotationRequest | null = null;
  private selectionVersion = 0;

  // Returns the form array containing the requested rooms.
  get rooms() {
    return this.form.controls.rooms;
  }

  // Stores an available quotation and opens the agent’s reservation review.
  reserve(quote: AccommodationQuotation | UnavailableAccommodation) {
    if (quote.availabilityStatus !== 'AVAILABLE' || this.edited() || this.searching()) return;
    this.reservations.saveDraft(quote);
    void this.router.navigate(['/reservations/new']);
  }

  // Returns the booking action label for the quotation.
  bookLabel(quote: AccommodationQuotation) {
    return quote.rooms.length === 1
      ? 'Book This Room'
      : 'Book All ' + quote.rooms.length + ' Rooms as ' + quote.rooms[0].roomTypeName;
  }

  // Checks that the supplied stay-wide inventory is a valid nonnegative integer.
  knownInventory(value: number | undefined) {
    return Number.isInteger(value) && value! >= 0;
  }

  // Opens room selection for the current search results.
  openCustomization() {
    if (!this.canCustomize()) return;
    this.customizationOpen.set(true);
    afterNextRender(() => this.customTitle()?.nativeElement.focus(), { injector: this.injector });
  }

  // Closes room selection and invalidates pending custom quotations.
  closeCustomization(restoreFocus = true) {
    this.selectionVersion++;
    this.customizationOpen.set(false);
    this.customOptions.set([]);
    this.selectedIds.set([]);
    this.customQuote.set(null);
    this.selectionError.set('');
    this.selectionLoading.set(false);
    if (restoreFocus) this.customizeTrigger()?.nativeElement.focus();
  }

  // Returns custom offerings that can accommodate the selected room.
  roomOptions(room: number) {
    return this.customOptions().filter((o) => !this.optionReason(o, room));
  }

  // Reads the server-provided selling price for one custom room choice.
  selectionRoomPrice(room: number, offeringId: number): number | null {
    // Display the backend's selling quotation; this does not calculate or select a rate.
    return (
      this.customOptions().find((offering) => offering.offeringId === offeringId)?.rooms[room]
        ?.totalPrice ?? null
    );
  }

  // Returns the adult limit of the selected offering.
  capacity(offeringId: number) {
    return this.response()?.selectionOfferings?.find((o) => o.offeringId === offeringId)?.maxAdults;
  }

  // Initializes custom room choices for the selected hotel and contract.
  customize(group: { hotelId: number; contractId: number }) {
    if (!this.lastRequest || !this.canCustomize()) return;
    this.customizationOpen.set(true);
    this.selectionVersion++;
    this.selectionLoading.set(false);
    this.selectionError.set('');
    this.customQuote.set(null);
    this.customOptions.set(
      (this.response()?.selectionOfferings ?? []).filter(
        (o) => o.hotelId === group.hotelId && o.contractId === group.contractId,
      ),
    );
    this.selectedIds.set(this.lastRequest.rooms.map(() => 0));
  }

  // Explains why an offering cannot be chosen for the requested room.
  optionReason(option: SelectionOffering, room: number) {
    if (
      !option.rooms[room] ||
      option.rooms[room].adults > option.maxAdults ||
      option.rooms[room].totalPrice === null
    )
      return 'Maximum ' + option.maxAdults + ' adults';
    if (!this.knownInventory(option.availableRoomsForStay)) return 'Inventory is unavailable';
    const used = this.selectedIds().filter(
      (id, i) => i !== room && id === option.offeringId,
    ).length;
    return used >= option.availableRoomsForStay ? 'Insufficient shared inventory' : '';
  }

  // Updates one room’s offering selection and requests a fresh quotation.
  choose(room: number, event: Event) {
    if (!this.customizationOpen() || this.edited() || this.searching()) return;
    const id = Number((event.target as HTMLSelectElement).value);
    const option = this.customOptions().find((o) => o.offeringId === id);
    if (id && (!option || this.optionReason(option, room))) return;
    const ids = [...this.selectedIds()];
    ids[room] = Number((event.target as HTMLSelectElement).value);
    this.selectedIds.set(ids);
    this.requote();
  }

  // Loads the initial data and subscriptions needed by this component.
  ngOnInit() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const { checkIn, nights } = this.form.getRawValue();
      const checkout =
        date(checkIn) &&
        Number.isInteger(nights) &&
        nights! >= 1 &&
        nights! <= (this.limits()?.maxNights ?? 31)
          ? new Date(Date.parse(checkIn + 'T00:00:00Z') + nights! * 86400000)
              .toISOString()
              .slice(0, 10)
          : '';
      if (this.form.controls.checkOut.value !== checkout)
        this.form.controls.checkOut.setValue(checkout, { emitEvent: false });
    });
    this.showUnavailable.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revalidateOccupancy());
    this.form.valueChanges
      .pipe(
        map(() => {
          const v = this.form.getRawValue();
          return {
            hotelId: v.hotelId,
            roomTypeId: v.roomTypeId,
            checkIn: v.checkIn,
            checkOut: v.checkOut,
            city: v.city.trim(),
          };
        }),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        switchMap((context) => {
          this.occupancyOptions.set([]);
          this.occupancyError.set('');
          this.occupancyChecked.set(false);
          this.occupancyLoading.set(false);
          this.revalidateOccupancy();
          const l = this.limits();
          const nights = (Date.parse(context.checkOut) - Date.parse(context.checkIn)) / 86400000;
          if (
            !l ||
            !context.hotelId ||
            !context.roomTypeId ||
            !date(context.checkIn) ||
            !date(context.checkOut) ||
            context.checkIn < l.businessDate ||
            context.checkIn > this.latestCheckIn() ||
            nights < 1 ||
            nights > l.maxNights ||
            context.city.length > 100
          )
            return of(null);
          this.occupancyLoading.set(true);
          return this.service
            .occupancy({ ...context, hotelId: context.hotelId, roomTypeId: context.roomTypeId })
            .pipe(
              catchError(() => {
                this.occupancyError.set(
                  'Occupancy guidance is unavailable. You can still search; eligible room limits will be checked by the server.',
                );
                return of(null);
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((options) => {
        this.occupancyLoading.set(false);
        this.occupancyChecked.set(options !== null);
        this.occupancyOptions.set(options ?? []);
        this.revalidateOccupancy();
      });
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.response()) this.edited.set(true);
      this.closeCustomization(false);
    });
    this.loadSetup();
  }

  // Loads search limits, reference filters, and occupancy guidance.
  loadSetup() {
    if (this.setupLoading()) return;
    this.setupLoading.set(true);
    this.setupError.set('');
    forkJoin({
      limits: this.service.limits(),
      filters: this.service.filters(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.limits.set(data.limits);
          this.hotels.set(data.filters.hotels);
          this.roomTypes.set(data.filters.roomTypes);
          this.rooms.controls.forEach((r) =>
            r.controls.adults.setValidators([
              Validators.required,
              Validators.min(1),
              adultInteger,
              (control) => this.occupancyValidator(control),
            ]),
          );
          this.form.updateValueAndValidity();
          this.setupLoading.set(false);
        },
        error: (e) => {
          this.setupError.set(apiError(e));
          this.setupLoading.set(false);
        },
      });
  }

  // Adds a requested room within the search room limit.
  addRoom() {
    const l = this.limits();
    if (!l || this.searching() || this.rooms.length >= l.maxRooms) return;
    const r = this.room();
    r.controls.adults.setValidators([
      Validators.required,
      Validators.min(1),
      adultInteger,
      (control) => this.occupancyValidator(control),
    ]);
    this.rooms.push(r);
  }

  // Removes a requested room and clears stale custom selections.
  removeRoom(index: number) {
    if (!this.searching() && this.rooms.length > 1) this.rooms.removeAt(index);
  }

  // Validates the stay request and loads available and unavailable quotations.
  search() {
    if (
      this.searching() ||
      this.setupLoading() ||
      this.setupError() ||
      !this.limits() ||
      this.occupancyLoading()
    )
      return;
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid || this.rooms.length < 1 || this.rooms.length > this.limits()!.maxRooms)
      return;
    const raw = this.form.getRawValue(),
      request = {
        checkIn: raw.checkIn,
        checkOut: raw.checkOut,
        hotelId: raw.hotelId,
        roomTypeId: raw.roomTypeId,
        city: raw.city.trim() || null,
        rooms: raw.rooms.map((r) => ({ adults: r.adults! })),
      };
    this.closeCustomization(false);
    this.lastRequest = request;
    this.requestedRooms.set(request.rooms.length);
    this.searching.set(true);
    this.error.set('');
    this.response.set(null);
    this.edited.set(false);
    this.service
      .search(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.response.set(data);
          this.customOptions.set([]);
          this.customQuote.set(null);
          this.searching.set(false);
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.searching.set(false);
        },
      });
  }

  // Requests an exact quotation for the current custom room choices.
  private requote() {
    const version = ++this.selectionVersion;
    this.customQuote.set(null);
    this.selectionError.set('');
    this.selectionLoading.set(false);
    const options = this.customOptions(),
      request = this.lastRequest;
    if (!request || !options.length || this.selectedIds().some((id) => !id)) return;
    this.selectionLoading.set(true);
    this.service
      .customize({
        hotelId: options[0].hotelId,
        contractId: options[0].contractId,
        checkIn: request.checkIn,
        checkOut: request.checkOut,
        rooms: request.rooms.map((r, i) => ({
          adults: r.adults,
          offeringId: this.selectedIds()[i],
        })),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (q) => {
          if (version === this.selectionVersion) {
            this.customQuote.set(q);
            this.selectionLoading.set(false);
          }
        },
        error: (e) => {
          if (version === this.selectionVersion) {
            this.selectionError.set(apiError(e));
            this.selectionLoading.set(false);
          }
        },
      });
  }

  // Creates a requested-room form group with adult-count validation.
  private room() {
    return new FormGroup({
      adults: new FormControl<number | null>(1, [
        Validators.required,
        Validators.min(1),
        adultInteger,
        (control) => this.occupancyValidator(control),
      ]),
    });
  }

  // Rechecks adult controls when the available occupancy guidance changes.
  private revalidateOccupancy() {
    this.rooms.controls.forEach((room) =>
      room.controls.adults.updateValueAndValidity({ emitEvent: false }),
    );
  }
}
// Accepts an empty optional filter or a whole-number reference.
const integerOptional: ValidatorFn = (c) =>
  c.value === null || Number.isInteger(c.value) ? null : { integer: true };
