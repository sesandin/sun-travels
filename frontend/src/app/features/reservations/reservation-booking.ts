import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Customer } from '../../models/customer.model';
import { ReservationQuote } from '../../models/reservation.model';
import { ReservationService } from '../../services/reservation.service';
import { CustomerService } from '../../services/customer.service';
import { apiError } from '../../services/api-error';

@Component({
  selector: 'app-reservation-booking',
  imports: [ReactiveFormsModule, DecimalPipe, RouterLink],
  templateUrl: './reservation-booking.html',
  styleUrl: './reservations.scss',
})
export class ReservationBooking {
  readonly previous = signal<ReservationQuote | null>(null);
  readonly selected = signal<Customer | null>(null);
  readonly customers = signal<Customer[]>([]);
  readonly busy = signal(false);
  readonly customerBusy = signal(false);
  readonly customerSearched = signal(false);
  readonly error = signal('');
  readonly customerError = signal('');
  readonly showRegistration = signal(false);
  readonly accepted = new FormControl(false, { nonNullable: true });
  readonly query = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(150)],
  });
  readonly customerSearch = new FormGroup({ query: this.query });
  readonly registration = new FormGroup(
    {
      name: new FormControl('', {
        nonNullable: true,
        validators: [
          (c) => (c.value.trim() ? null : { required: true }),
          Validators.maxLength(150),
        ],
      }),
      phone: new FormControl('', {
        nonNullable: true,
        validators: [Validators.maxLength(20), Validators.pattern(/^(?=.*\d)[+0-9(). -]{5,20}$/)],
      }),
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.maxLength(150), Validators.email],
      }),
    },
    {
      validators: (c) =>
        c.get('phone')?.value?.trim() || c.get('email')?.value?.trim() ? null : { contact: true },
    },
  );
  private readonly service = inject(ReservationService);
  readonly quote = signal(this.service.draft());
  private readonly customerService = inject(CustomerService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // Finds customer records matching the agent’s booking search.
  searchCustomers() {
    if (this.customerBusy() || this.busy() || this.query.invalid) return;
    this.customerBusy.set(true);
    this.customerError.set('');
    this.customerService
      .search(this.query.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          this.customers.set(r.customers);
          this.customerSearched.set(true);
          this.customerBusy.set(false);
        },
        error: (e) => {
          this.customerError.set(apiError(e));
          this.customerBusy.set(false);
        },
      });
  }
  // Selects an existing customer for the reservation.
  registrationMessage(field: 'name' | 'phone' | 'email'): string {
    const control = this.registration.controls[field];
    if (!control.touched || !control.errors) return '';
    if (control.hasError('required')) return 'Customer name is required.';
    if (control.hasError('maxlength'))
      return `Use at most ${control.errors['maxlength'].requiredLength} characters.`;
    return field === 'phone'
      ? 'Use 5 to 20 international phone characters, including a digit.'
      : 'Enter a valid email address.';
  }

  // Selects an existing customer for the reservation.
  select(customer: Customer) {
    if (this.busy()) return;
    this.selected.set(customer);
    this.accepted.setValue(false);
    this.customers.set([]);
    this.customerSearched.set(false);
    this.customerError.set('');
    this.showRegistration.set(false);
  }
  // Clears the selected customer so the agent can choose another.
  changeCustomer() {
    if (this.busy() || this.customerBusy()) return;
    this.selected.set(null);
    this.accepted.setValue(false);
    this.query.reset();
    this.registration.reset();
  }
  // Registers a customer and selects the new record for the booking.
  register() {
    this.registration.markAllAsTouched();
    if (this.registration.invalid || this.customerBusy() || this.busy()) return;
    const v = this.registration.getRawValue();
    this.customerBusy.set(true);
    this.customerError.set('');
    this.customerService
      .create({ name: v.name.trim(), phone: v.phone.trim() || null, email: v.email.trim() || null })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.select(customer);
          this.showRegistration.set(false);
          this.customerBusy.set(false);
        },
        error: (e) => {
          this.customerError.set(apiError(e));
          this.customerBusy.set(false);
        },
      });
  }
  // Leaves the booking review without confirming the reservation.
  cancelBooking() {
    if (this.busy() || this.customerBusy()) return;
    this.service.clearDraft();
    this.quote.set(null);
    this.previous.set(null);
    this.selected.set(null);
    this.accepted.setValue(false);
    void this.router.navigate(['/search']);
  }
  // Confirms the reviewed quotation for the selected customer and handles price changes.
  confirm() {
    const q = this.quote(),
      customer = this.selected();
    if (!q || !customer || !this.accepted.value || this.busy() || this.customerBusy()) return;
    this.busy.set(true);
    this.error.set('');
    this.service
      .confirm({
        customerId: customer.customerId,
        hotelId: q.hotelId,
        contractId: q.contractId,
        checkIn: q.checkIn,
        checkOut: q.checkOut,
        rooms: q.rooms.map((r) => ({ offeringId: r.offeringId, adults: r.adults })),
        acceptedTotal: q.totalPrice,
        acceptedCurrency: q.currencyCode,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.service.clearDraft();
          this.busy.set(false);
          void this.router.navigate(['/reservations', saved.reservationId], {
            state: { confirmed: true },
          });
        },
        error: (e) => {
          this.busy.set(false);
          this.accepted.setValue(false);
          if (e.status === 409 && e.error?.code === 'PRICE_CHANGED' && e.error.quotation) {
            this.previous.set(q);
            this.quote.set(e.error.quotation);
            this.service.saveDraft(e.error.quotation);
          }
          this.error.set(apiError(e));
        },
      });
  }
}
