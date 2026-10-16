import { Pagination } from '../../shared/pagination';
import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Subscription, debounceTime, tap } from 'rxjs';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { StayPeriod, Reservation, ReservationPage } from '../../models/reservation.model';
import { ReservationService } from '../../services/reservation.service';
import { apiError } from '../../services/api-error';
@Component({
  selector: 'app-reservation-directory',
  imports: [Pagination, ReactiveFormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './reservation-directory.html',
  styleUrl: './reservations.scss',
})
export class ReservationDirectory implements OnInit {
  private readonly service = inject(ReservationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly result = signal<ReservationPage | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly query = new FormControl('', { nonNullable: true });
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
  readonly detailsDialog = viewChild.required<ElementRef<HTMLDialogElement>>('detailsDialog');
  openDetails(reservation: Reservation) {
    this.selected.set(reservation);
    this.detailsDialog().nativeElement.showModal();
  }
  retry() {
    this.load(this.requestedPage);
  }
  closeDetails() {
    this.detailsDialog().nativeElement.close();
  }
  private appliedQuery = '';
  private appliedStatus = '';
  private appliedStayFrom = '';
  private appliedStayTo = '';
  private listRequest?: Subscription;
  private appliedPeriod: StayPeriod = '';
  private appliedFilters = '';
  private requestedPage = 0;
  totalPages() {
    const r = this.result();
    return r ? Math.max(1, Math.ceil(r.totalElements / r.size)) : 1;
  }
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
