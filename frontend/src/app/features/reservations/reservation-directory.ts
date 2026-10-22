import { Pagination } from '../../shared/pagination';
import { BookedPriceBreakdown } from './booked-price-breakdown';
import {
  Component,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { debounceTime, Subscription, tap } from 'rxjs';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Reservation, ReservationSummary, ReservationPage, StayPeriod } from '../../models/reservation.model';
import { ReservationService } from '../../services/reservation.service';
import { apiError } from '../../services/api-error';

@Component({
  selector: 'app-reservation-directory',
  imports: [
    Pagination,
    ReactiveFormsModule,
    RouterLink,
    DecimalPipe,
    DatePipe,
    BookedPriceBreakdown,
  ],
  templateUrl: './reservation-directory.html',
  styleUrl: './reservations.scss',
})
export class ReservationDirectory implements OnInit {
  readonly result = signal<ReservationPage | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly query = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(150)] });
  readonly status = new FormControl('', { nonNullable: true });
  readonly stayPeriod = new FormControl<StayPeriod>('', { nonNullable: true });
  readonly stayFrom = new FormControl('', { nonNullable: true });
  readonly stayTo = new FormControl('', { nonNullable: true });
  readonly filters = new FormGroup(
    {
      query: this.query,
      status: this.status,
      stayPeriod: this.stayPeriod,
      stayFrom: this.stayFrom,
      stayTo: this.stayTo,
    },
    {
      validators: (group) => {
        const { stayFrom, stayTo } = group.getRawValue();
        // Checks whether an optional reservation filter is a valid calendar date.
        const validDate = (v: string) =>
          !v ||
          (/^\d{4}-\d{2}-\d{2}$/.test(v) &&
            Number.isFinite(Date.parse(v + 'T00:00:00Z')) &&
            new Date(v + 'T00:00:00Z').toISOString().slice(0, 10) === v);
        return !validDate(stayFrom) ||
          !validDate(stayTo) ||
          (stayFrom && stayTo && stayFrom > stayTo)
          ? { dates: true }
          : null;
      },
    },
  );
  readonly selected = signal<Reservation | null>(null);
  readonly detailsLoading = signal(false);
  readonly detailsError = signal('');
  private detailRequest?: Subscription;
  private detailId: number | null = null;
  readonly detailsDialog = viewChild.required<ElementRef<HTMLDialogElement>>('detailsDialog');
  private readonly service = inject(ReservationService);
  private readonly destroyRef = inject(DestroyRef);
  private appliedQuery = '';
  private appliedStatus = '';
  private appliedStayFrom = '';
  private appliedStayTo = '';
  private listRequest?: Subscription;
  private appliedPeriod: StayPeriod = '';
  private appliedFilters = '';
  private requestedPage = 0;

  // Opens the selected reservation in the details dialog.
  openDetails(reservation: Pick<ReservationSummary, 'reservationId'>) {
    this.detailRequest?.unsubscribe();
    this.selected.set(null);
    this.detailsError.set('');
    this.detailsLoading.set(true);
    this.detailId = reservation.reservationId;
    this.detailsDialog().nativeElement.showModal();
    this.detailRequest = this.service.get(reservation.reservationId)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (detail) => { this.selected.set(detail); this.detailsLoading.set(false); },
        error: (e) => { this.detailsError.set(apiError(e)); this.detailsLoading.set(false); },
      });
  }
  retryDetails() {
    if (this.detailId !== null) this.openDetails({ reservationId: this.detailId });
  }

  // Retries the failed reservation directory request.
  retry() {
    this.load(this.requestedPage);
  }

  // Closes the reservation details dialog.
  closeDetails() {
    this.detailRequest?.unsubscribe();
    this.detailsLoading.set(false);
    this.selected.set(null);
    this.detailsDialog().nativeElement.close();
  }

  // Calculates the number of reservation directory pages.
  totalPages() {
    const r = this.result();
    return r ? Math.max(1, Math.ceil(r.totalElements / r.size)) : 1;
  }
  // Loads the initial data and subscriptions needed by this component.
  ngOnInit() {
    this.filters.valueChanges
      .pipe(
        tap(() => {
          this.listRequest?.unsubscribe();
          // The previous filter signature is no longer represented by a result or active request.
          // Invalidate it so returning to that selection still reloads after cancellation.
          this.appliedFilters = '';
          this.loading.set(false);
          this.result.set(null);
          this.selected.set(null);
          this.error.set('');
        }),
        debounceTime(250),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        if (this.appliedFilters !== JSON.stringify(this.filters.getRawValue())) this.search();
      });
    this.search();
  }
  // Applies reservation filters and loads the first matching page.
  search() {
    this.listRequest?.unsubscribe();
    this.loading.set(false);
    this.filters.markAllAsTouched();
    if (this.filters.invalid) return;
    this.appliedQuery = this.query.value;
    this.appliedStatus = this.status.value;
    this.appliedStayFrom = this.stayFrom.value;
    this.appliedStayTo = this.stayTo.value;
    this.appliedPeriod = this.stayPeriod.value;
    this.appliedFilters = JSON.stringify(this.filters.getRawValue());
    this.load(0);
  }
  // Loads the selected reservation page using all active filters.
  load(page = 0) {
    if (this.loading()) return;
    this.requestedPage = page;
    this.loading.set(true);
    this.error.set('');
    this.result.set(null);
    this.listRequest = this.service
      .list(
        this.appliedQuery,
        this.appliedStatus,
        page,
        this.appliedStayFrom,
        this.appliedStayTo,
        this.appliedPeriod,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.loading.set(false);
        },
      });
  }
}
