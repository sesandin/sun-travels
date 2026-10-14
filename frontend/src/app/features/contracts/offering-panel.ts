import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  OnChanges,
  SimpleChanges,
  afterNextRender,
  inject,
  input,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Contract } from '../../models/contract.model';
import { Offering, OfferingStatus } from '../../models/offering.model';
import { RoomType } from '../../models/room-type.model';
import { OfferingService } from '../../services/offering.service';
import { RoomTypeService } from '../../services/room-type.service';
import { apiError } from '../../services/api-error';
import { SeasonalRates } from './seasonal-rates';
import { money, positiveInteger } from './offering-validators';
@Component({
  selector: 'app-offering-panel',
  imports: [ReactiveFormsModule, SeasonalRates],
  templateUrl: './offering-panel.html',
  styleUrl: './offering-panel.scss',
})
export class OfferingPanel implements OnInit, OnChanges {
  private loadRequest?: Subscription;
  private saveRequest?: Subscription;
  private readonly injector = inject(Injector);
  private readonly editor = viewChild<ElementRef<HTMLElement>>('editor');
  readonly contract = input.required<Contract>();
  readonly focusOfferingId = input<number | null>(null);
  private readonly offeringCards = viewChildren<ElementRef<HTMLElement>>('offeringCard');
  readonly targetMissing = signal(false);
  private readonly service = inject(OfferingService);
  private readonly roomsService = inject(RoomTypeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly rows = signal<Offering[]>([]);
  readonly rooms = signal<RoomType[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly roomsLoading = signal(false);
  readonly roomsError = signal('');
  readonly saving = signal(false);
  readonly failure = signal('');
  readonly notice = signal('');
  readonly selected = signal<Offering | null>(null);
  readonly mode = signal<'draft' | 'rate' | 'status' | null>(null);
  readonly form = new FormGroup({
    roomTypeId: new FormControl<number | null>(null, [Validators.required, positiveInteger]),
    ratePerPersonPerNight: new FormControl<number | null>(null, [Validators.required, money]),
    contractedRoomCount: new FormControl<number | null>(null, [
      Validators.required,
      positiveInteger,
    ]),
    maxAdults: new FormControl<number | null>(null, [Validators.required, positiveInteger]),
    status: new FormControl<OfferingStatus>('ENABLED', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });
  readonly rateForm = new FormGroup({
    ratePerPersonPerNight: new FormControl<number | null>(null, [Validators.required, money]),
  });
  readonly statusForm = new FormGroup({
    status: new FormControl<OfferingStatus>('ENABLED', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });
  ngOnChanges(changes: SimpleChanges) {
    const parent = changes['contract'];
    if (
      parent &&
      !parent.firstChange &&
      parent.previousValue.contractId !== parent.currentValue.contractId
    ) {
      this.loadRequest?.unsubscribe();
      this.saveRequest?.unsubscribe();
      this.loading.set(false);
      this.saving.set(false);
      this.rows.set([]);
      this.mode.set(null);
      this.selected.set(null);
      this.failure.set('');
      this.notice.set('');
      this.targetMissing.set(false);
      this.load();
    } else {
      if (
        parent &&
        !parent.firstChange &&
        parent.previousValue.status !== parent.currentValue.status
      )
        this.mode.set(null);
      if (changes['focusOfferingId'] && !changes['focusOfferingId'].firstChange)
        this.focusLinkedOffering();
    }
  }
  private focusLinkedOffering() {
    if (this.loading()) {
      this.targetMissing.set(false);
      return;
    }
    const target = this.focusOfferingId();
    this.targetMissing.set(
      target !== null && !this.rows().some((row) => row.offeringId === target),
    );
    if (target !== null && !this.targetMissing())
      afterNextRender(
        () => {
          if (this.focusOfferingId() !== target) return;
          const card = this.offeringCards().find(
            (ref) => ref.nativeElement.id === 'offering-' + target,
          )?.nativeElement;
          card?.focus({ preventScroll: true });
          card?.scrollIntoView?.({ block: 'center', behavior: 'auto' });
        },
        { injector: this.injector },
      );
  }
  ngOnInit() {
    this.load();
  }
  canMaintain() {
    return this.contract().status === 'DRAFT' || this.contract().status === 'APPROVED';
  }
  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.loadRequest = this.service
      .list(this.contract().contractId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.loading.set(false);
          this.focusLinkedOffering();
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.loading.set(false);
        },
      });
  }
  loadRooms() {
    if (this.roomsLoading()) return;
    this.roomsLoading.set(true);
    this.roomsError.set('');
    this.roomsService
      .getAllRoomTypes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.rooms.set(rows);
          this.roomsLoading.set(false);
        },
        error: (e) => {
          this.roomsError.set(apiError(e));
          this.roomsLoading.set(false);
        },
      });
  }
  open(row: Offering | null = null, mode: 'draft' | 'rate' | 'status' = 'draft') {
    if (
      this.saving() ||
      !this.canMaintain() ||
      (mode === 'draft' && this.contract().status !== 'DRAFT') ||
      (mode === 'rate' && this.contract().status !== 'APPROVED') ||
      (mode !== 'draft' && !row)
    )
      return;
    this.selected.set(row);
    this.mode.set(mode);
    this.failure.set('');
    this.notice.set('');
    this.form.reset({
      roomTypeId: row?.roomTypeId ?? null,
      ratePerPersonPerNight: row?.ratePerPersonPerNight ?? null,
      contractedRoomCount: row?.contractedRoomCount ?? null,
      maxAdults: row?.maxAdults ?? null,
      status: row?.status ?? 'ENABLED',
    });
    this.rateForm.reset({ ratePerPersonPerNight: row?.ratePerPersonPerNight ?? null });
    this.statusForm.reset({ status: row?.status ?? 'ENABLED' });
    if (mode === 'draft') this.loadRooms();
    afterNextRender(() => this.editor()?.nativeElement.focus(), { injector: this.injector });
  }
  cancel() {
    if (!this.saving()) this.mode.set(null);
  }
  save() {
    if (this.saving() || !this.canMaintain()) return;
    const row = this.selected(),
      mode = this.mode();
    let req;
    if (mode === 'draft' && this.contract().status === 'DRAFT') {
      this.form.markAllAsTouched();
      if (this.form.invalid || this.roomsLoading() || this.roomsError()) return;
      const raw = this.form.getRawValue(),
        payload = {
          ...raw,
          roomTypeId: raw.roomTypeId!,
          ratePerPersonPerNight: raw.ratePerPersonPerNight!,
          contractedRoomCount: raw.contractedRoomCount!,
          maxAdults: raw.maxAdults!,
        };
      req = row
        ? this.service.update(row.offeringId, payload)
        : this.service.create(this.contract().contractId, payload);
    } else if (mode === 'rate' && row && this.contract().status === 'APPROVED') {
      this.rateForm.markAllAsTouched();
      if (this.rateForm.invalid) return;
      req = this.service.rate(row.offeringId, this.rateForm.getRawValue().ratePerPersonPerNight!);
    } else if (mode === 'status' && row)
      req = this.service.status(row.offeringId, this.statusForm.getRawValue().status);
    else return;
    this.saving.set(true);
    this.failure.set('');
    this.saveRequest = req.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.rows.update((rows) =>
          row ? rows.map((r) => (r.offeringId === saved.offeringId ? saved : r)) : [...rows, saved],
        );
        this.saving.set(false);
        this.mode.set(null);
        this.notice.set('Offering saved. Confirmed reservations remain unchanged.');
      },
      error: (e) => {
        this.saving.set(false);
        this.failure.set(apiError(e));
      },
    });
  }
}
