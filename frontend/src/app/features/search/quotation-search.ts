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
  HotelPage,
  CitySuggestion,
  HotelSuggestion,
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
import {
  catchError,
  distinctUntilChanged,
  forkJoin,
  map,
  of,
  switchMap,
  timer,
  combineLatest,
  startWith,
} from 'rxjs';
import { findCountry } from '../../shared/location/country-catalog';
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
  host: { '(document:click)': 'dismissLookupOutside($event)' },
})
export class QuotationSearch implements OnInit {
  readonly activeLookup = signal<'city' | 'hotel' | null>(null);
  private readonly cityLookup = viewChild<ElementRef<HTMLElement>>('cityLookup');
  private readonly hotelLookup = viewChild<ElementRef<HTMLElement>>('hotelLookup');

  dismissLookupOutside(event: MouseEvent): void {
    const lookup = this.activeLookup() === 'city' ? this.cityLookup() : this.hotelLookup();
    if (event.target instanceof Node && !lookup?.nativeElement.contains(event.target)) {
      this.activeLookup.set(null);
    }
  }

  leaveLookup(event: FocusEvent): void {
    if (
      !(event.relatedTarget instanceof Node) ||
      !(event.currentTarget as HTMLElement).contains(event.relatedTarget)
    ) {
      this.activeLookup.set(null);
    }
  }

  readonly limits = signal<SearchLimits | null>(null);
  readonly hotelQuery = new FormControl('', { nonNullable: true });
  readonly hotelMatches = signal<HotelSuggestion[]>([]);
  readonly selectedHotel = signal<HotelSuggestion | null>(null);
  readonly cityQuery = new FormControl('', { nonNullable: true });
  readonly cityMatches = signal<CitySuggestion[]>([]);
  readonly selectedCity = signal<CitySuggestion | null>(null);
  readonly cityLoading = signal(false);
  readonly cityLookupDone = signal(false);
  readonly cityError = signal('');
  readonly hotelLoading = signal(false);
  readonly hotelResolving = signal(false);
  readonly hotelLookupDone = signal(false);
  readonly hotelError = signal('');
  readonly roomTypes = signal<QuotationSearchFilters['roomTypes']>([]);
  readonly setupLoading = signal(false);
  readonly setupError = signal('');
  readonly searching = signal(false);
  readonly error = signal('');
  // Keep the existing quotation objects intact for booking and custom room selection.
  readonly response = signal<Pick<
    QuotationResponse,
    'businessDate' | 'checkIn' | 'checkOut' | 'nights' | 'quotations' | 'selectionOfferings'
  > | null>(null);
  readonly nextAfterHotelId = signal<number | null>(null);
  private searchVersion = 0;
  private customizationLoaded = false;
  readonly hotelGroups = computed(() => {
    const quotations = this.groupedQuotations();
    const groups = new Map<
      number,
      { hotelId: number; hotelName: string; city: string | null; quotations: typeof quotations }
    >();
    for (const quote of quotations) {
      let hotel = groups.get(quote.hotelId);
      if (!hotel) {
        hotel = {
          hotelId: quote.hotelId,
          hotelName: quote.hotelName,
          city: quote.city,
          quotations: [],
        };
        groups.set(quote.hotelId, hotel);
      }
      hotel.quotations.push(quote);
    }
    return [...groups.values()];
  });
  readonly requestedRooms = signal(1);

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
    return max !== null && control.value > max ? { occupancyMax: { max } } : null;
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
      country: new FormControl<string | null>(null),
      hotelId: new FormControl<number | null>(null, [Validators.min(1), integerOptional]),
      roomTypeId: new FormControl<number | null>(null, [Validators.min(1), integerOptional]),
    },
    {
      validators: [
        (group) =>
          group.get('city')?.value?.trim() || group.get('hotelId')?.value !== null
            ? null
            : { destination: true },
        (group) => {
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
      ],
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
    if (!this.canCustomize() || this.customizationOpen()) return;
    this.customizationOpen.set(true);
    afterNextRender(() => this.customTitle()?.nativeElement.focus(), { injector: this.injector });
    if (this.customizationLoaded || !this.lastRequest) return;
    const version = ++this.selectionVersion;
    this.selectionLoading.set(true);
    // Explicit customization retains the legacy bounded search and exact selection API.
    this.service
      .search(this.lastRequest)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (version !== this.selectionVersion) return;
          this.response.update((current) =>
            current ? { ...current, selectionOfferings: data.selectionOfferings ?? [] } : current,
          );
          this.customizationLoaded = true;
          this.selectionLoading.set(false);
        },
        error: (error) => {
          if (version !== this.selectionVersion) return;
          this.selectionError.set(apiError(error));
          this.selectionLoading.set(false);
        },
      });
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
    if (
      !this.lastRequest ||
      !this.canCustomize() ||
      (this.selectionLoading() && !this.customizationLoaded)
    )
      return;
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
    this.cityQuery.valueChanges
      .pipe(
        switchMap((text) => {
          this.cityMatches.set([]);
          this.cityError.set('');
          this.cityLoading.set(false);
          this.cityLookupDone.set(false);
          if (this.selectedCity()?.city === text) return of([]);
          this.selectedCity.set(null);
          if (this.form.controls.city.value || this.form.controls.country.value)
            this.form.patchValue({ city: '', country: null });
          if (text.trim() && this.selectedHotel()) this.clearHotel();
          const term = text.trim();
          if (term.length < 2) return of([]);
          if (term.length > 100) {
            this.cityError.set('City must be at most 100 characters');
            return of([]);
          }
          return timer(300).pipe(
            switchMap(() => {
              this.cityLoading.set(true);
              return this.service.cities(term).pipe(
                catchError((error) => {
                  this.cityError.set(apiError(error));
                  return of([]);
                }),
              );
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((matches) => {
        this.cityMatches.set(matches);
        this.cityLoading.set(false);
        this.cityLookupDone.set(this.cityQuery.value.trim().length >= 2 && !this.selectedCity());
      });
    // Resolve saved/programmatic IDs independently of the current suggestion list.
    this.form.controls.hotelId.valueChanges
      .pipe(
        switchMap((id) => {
          this.hotelResolving.set(false);
          if (id === null) return of(null);
          if (this.selectedHotel()?.hotelId === id) return of(this.selectedHotel());
          this.hotelResolving.set(true);
          this.hotelError.set('');
          return this.service.hotel(id).pipe(
            catchError((error) => {
              this.hotelError.set(apiError(error));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((hotel) => {
        this.hotelResolving.set(false);
        if (hotel && !this.hotelFitsLocation(hotel)) {
          this.form.controls.hotelId.setValue(null);
          this.hotelQuery.setValue('');
          return;
        }
        this.selectedHotel.set(hotel);
        if (hotel) this.hotelQuery.setValue(hotel.name);
        else if (this.form.controls.hotelId.value !== null)
          this.form.controls.hotelId.setValue(null);
      });
    // The outer switchMap cancels both the debounce timer and any older HTTP request immediately.
    let previousHotelText = this.hotelQuery.value;
    combineLatest([
      this.hotelQuery.valueChanges.pipe(startWith(this.hotelQuery.value)),
      this.form.valueChanges.pipe(
        map(() => ({
          city: this.form.controls.city.value,
          country: this.form.controls.country.value,
        })),
        distinctUntilChanged((a, b) => a.city === b.city && a.country === b.country),
        startWith({
          city: this.form.controls.city.value,
          country: this.form.controls.country.value,
        }),
      ),
    ])
      .pipe(
        switchMap(([text, location]) => {
          const textChanged = text !== previousHotelText;
          previousHotelText = text;
          this.hotelMatches.set([]);
          this.hotelError.set('');
          this.hotelLoading.set(false);
          this.hotelLookupDone.set(false);
          // A location update must not cancel an independent ID lookup; its response
          // is checked against the latest location before accepting the hotel.
          if (this.hotelResolving() && !textChanged) return of([]);
          if (
            this.selectedHotel() &&
            text === this.selectedHotel()!.name &&
            this.hotelFitsLocation(this.selectedHotel()!)
          )
            return of([]);
          if (this.form.controls.hotelId.value !== null) this.form.controls.hotelId.setValue(null);
          const term = text.trim();
          if (term.length < 2) return of([]);
          if (term.length > 100) {
            this.hotelError.set('Hotel name must be at most 100 characters');
            return of([]);
          }
          return timer(300).pipe(
            switchMap(() => {
              this.hotelLoading.set(true);
              return this.service.hotels(term, location.city, location.country).pipe(
                catchError((error) => {
                  this.hotelError.set(apiError(error));
                  return of([]);
                }),
              );
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((matches) => {
        this.hotelMatches.set(matches);
        this.hotelLoading.set(false);
        this.hotelLookupDone.set(this.hotelQuery.value.trim().length >= 2 && !this.selectedHotel());
      });
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
      this.searchVersion++;
      this.edited.set(true);
      this.response.set(null);
      this.nextAfterHotelId.set(null);
      this.lastRequest = null;
      this.searching.set(false);
      this.error.set('');
      this.customizationLoaded = false;
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
      roomTypes: this.service.roomTypes(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.limits.set(data.limits);
          this.roomTypes.set(data.roomTypes);
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

  private hotelFitsLocation(hotel: HotelSuggestion) {
    const normalize = (text?: string | null) => text?.trim().toLocaleLowerCase('en') ?? '';
    const country = (text?: string | null) =>
      normalize(text ? (findCountry(text)?.name ?? text) : '');
    const location = this.form.getRawValue();
    return (
      (!normalize(location.city) || normalize(location.city) === normalize(hotel.city)) &&
      (!country(location.country) || country(location.country) === country(hotel.country))
    );
  }

  selectCity(city: CitySuggestion) {
    this.selectedCity.set(city);
    this.form.patchValue({ city: city.city, country: city.country });
    this.cityQuery.setValue(city.city);
    this.activeLookup.set(null);
  }

  clearCity() {
    this.selectedCity.set(null);
    this.form.patchValue({ city: '', country: null });
    this.cityQuery.setValue('');
  }

  selectHotel(hotel: HotelSuggestion) {
    if (!this.hotelFitsLocation(hotel)) return;
    this.selectedHotel.set(hotel);
    this.form.controls.hotelId.setValue(hotel.hotelId);
    this.activeLookup.set(null);
  }

  clearHotel() {
    this.form.controls.hotelId.setValue(null);
    this.hotelQuery.setValue('');
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
      this.occupancyLoading() ||
      this.hotelResolving()
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
        ...(raw.country ? { country: raw.country } : {}),
        rooms: raw.rooms.map((r) => ({ adults: r.adults! })),
      };
    this.closeCustomization(false);
    this.lastRequest = request;
    this.requestedRooms.set(request.rooms.length);
    this.searching.set(true);
    this.error.set('');
    this.response.set(null);
    this.edited.set(false);
    this.nextAfterHotelId.set(null);
    this.customizationLoaded = false;
    this.loadPage(request, null);
  }

  loadMore() {
    if (this.searching() || !this.lastRequest || this.nextAfterHotelId() === null) return;
    this.loadPage(this.lastRequest, this.nextAfterHotelId());
  }

  private loadPage(request: QuotationRequest, cursor: number | null) {
    const version = ++this.searchVersion;
    this.searching.set(true);
    this.error.set('');
    this.service
      .page(request, cursor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (version !== this.searchVersion) return;
          this.appendPage(page, cursor !== null);
          this.searching.set(false);
        },
        error: (error) => {
          if (version !== this.searchVersion) return;
          this.error.set(apiError(error));
          this.searching.set(false);
        },
      });
  }

  private appendPage(page: HotelPage, append: boolean) {
    const previous = append ? this.response() : null;
    const seen = new Set(previous?.quotations.map((q) => q.hotelId) ?? []);
    const hotels = page.hotels.filter((hotel) => {
      if (seen.has(hotel.hotelId)) return false;
      seen.add(hotel.hotelId);
      return true;
    });
    this.response.set({
      businessDate: page.businessDate,
      checkIn: page.checkIn,
      checkOut: page.checkOut,
      nights: page.nights,
      quotations: [
        ...(previous?.quotations ?? []),
        ...hotels.flatMap((h) => h.offerings.map((o) => o.quotation)),
      ],
      selectionOfferings: this.customizationLoaded
        ? previous?.selectionOfferings
        : [
            ...(previous?.selectionOfferings ?? []),
            ...hotels.flatMap((h) => h.offerings.map((o) => o.roomChoices)),
          ],
    });
    this.nextAfterHotelId.set(page.nextAfterHotelId);
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
