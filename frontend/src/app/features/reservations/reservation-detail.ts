import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Reservation } from '../../models/reservation.model';
import { ReservationService } from '../../services/reservation.service';
import { apiError } from '../../services/api-error';
@Component({
  selector: 'app-reservation-detail',
  imports: [RouterLink, DecimalPipe, DatePipe],
  templateUrl: './reservation-detail.html',
  styleUrl: './reservations.scss',
})
export class ReservationDetail implements OnInit {
  private readonly service = inject(ReservationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  back() {
    if (!this.cancelling()) void this.router.navigate(['/reservations']);
  }
  private readonly destroyRef = inject(DestroyRef);
  readonly reservation = signal<Reservation | null>(null);
  readonly loading = signal(false);
  readonly cancelling = signal(false);
  readonly error = signal('');
  readonly notice = signal(history.state?.confirmed ? 'Reservation confirmed successfully.' : '');
  readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('cancelDialog');
  ngOnInit() {
    this.load();
  }
  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.service
      .get(Number(this.route.snapshot.paramMap.get('id')))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          this.reservation.set(r);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.loading.set(false);
        },
      });
  }
  openCancel() {
    this.dialog().nativeElement.showModal();
  }
  closeCancel() {
    if (!this.cancelling()) this.dialog().nativeElement.close();
  }
  cancel() {
    const r = this.reservation();
    if (!r || this.cancelling() || r.status !== 'CONFIRMED') return;
    this.cancelling.set(true);
    this.error.set('');
    this.service
      .cancel(r.reservationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.reservation.set(saved);
          this.cancelling.set(false);
          this.closeCancel();
          this.notice.set(
            'Reservation cancelled. Inventory has been released; booking history is preserved.',
          );
        },
        error: (e) => {
          this.cancelling.set(false);
          this.closeCancel();
          this.error.set(apiError(e));
        },
      });
  }
}
