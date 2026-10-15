import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Hotel } from '../../models/hotel.model';
import { RoomType } from '../../models/room-type.model';
import { QuotationResponse, SearchLimits } from '../../models/quotation.model';
import { QuotationSearchService } from '../../services/quotation-search.service';
import { HotelService } from '../../services/hotel.service';
import { RoomTypeService } from '../../services/room-type.service';
import { apiError } from '../../services/api-error';
import { forkJoin } from 'rxjs';
const date = (value: string) =>
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(Date.parse(value + 'T00:00:00Z')) &&
  new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) === value;
const integer: ValidatorFn = (c) => (Number.isInteger(c.value) ? null : { integer: true });
@Component({
  selector: 'app-quotation-search',
  imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './quotation-search.html',
  styleUrl: './quotation-search.scss',
})
export class QuotationSearch implements OnInit {
  private readonly service = inject(QuotationSearchService);
  private readonly hotelService = inject(HotelService);
  private readonly roomService = inject(RoomTypeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly limits = signal<SearchLimits | null>(null);
  readonly hotels = signal<Hotel[]>([]);
  readonly roomTypes = signal<RoomType[]>([]);
  readonly setupLoading = signal(false);
  readonly setupError = signal('');
  readonly searching = signal(false);
  readonly error = signal('');
  readonly response = signal<QuotationResponse | null>(null);
  readonly edited = signal(false);
  readonly latestCheckIn = computed(() => {
    const l = this.limits();
    if (!l) return '';
    return new Date(Date.parse(l.businessDate + 'T00:00:00Z') + l.maxAdvanceDays * 86400000)
      .toISOString()
      .slice(0, 10);
  });
  readonly form = new FormGroup(
    {
      checkIn: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
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
  get rooms() {
    return this.form.controls.rooms;
  }
  private room() {
    return new FormGroup({
      adults: new FormControl<number | null>(1, [
        Validators.required,
        Validators.min(1),
        Validators.max(10),
        integer,
      ]),
    });
  }
  ngOnInit() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.response()) this.edited.set(true);
    });
    this.loadSetup();
  }
  loadSetup() {
    if (this.setupLoading()) return;
    this.setupLoading.set(true);
    this.setupError.set('');
    forkJoin({
      limits: this.service.limits(),
      hotels: this.hotelService.getAllHotels(),
      rooms: this.roomService.getAllRoomTypes(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.limits.set(data.limits);
          this.hotels.set(data.hotels);
          this.roomTypes.set(data.rooms);
          this.rooms.controls.forEach((r) =>
            r.controls.adults.setValidators([
              Validators.required,
              Validators.min(1),
              Validators.max(data.limits.maxAdultsPerRoom),
              integer,
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
  addRoom() {
    const l = this.limits();
    if (!l || this.searching() || this.rooms.length >= l.maxRooms) return;
    const r = this.room();
    r.controls.adults.setValidators([
      Validators.required,
      Validators.min(1),
      Validators.max(l.maxAdultsPerRoom),
      integer,
    ]);
    this.rooms.push(r);
  }
  removeRoom(index: number) {
    if (!this.searching() && this.rooms.length > 1) this.rooms.removeAt(index);
  }
  search() {
    if (this.searching() || this.setupLoading() || this.setupError() || !this.limits()) return;
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid || this.rooms.length < 1 || this.rooms.length > this.limits()!.maxRooms)
      return;
    const raw = this.form.getRawValue(),
      request = {
        ...raw,
        city: raw.city.trim() || null,
        rooms: raw.rooms.map((r) => ({ adults: r.adults! })),
      };
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
          this.searching.set(false);
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.searching.set(false);
        },
      });
  }
}
const integerOptional: ValidatorFn = (c) =>
  c.value === null || Number.isInteger(c.value) ? null : { integer: true };
