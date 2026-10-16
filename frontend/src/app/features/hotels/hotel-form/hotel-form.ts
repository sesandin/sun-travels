import { Component, input, output } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Hotel, HotelStatus, HotelUpdateRequest } from '../../../models/hotel.model';
const notBlank: ValidatorFn = (control: AbstractControl) =>
  typeof control.value === 'string' && !control.value.trim() ? { required: true } : null;
@Component({
  selector: 'app-hotel-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './hotel-form.html',
  styleUrl: './hotel-form.scss',
})
export class HotelForm {
  readonly busy = input(false);
  readonly error = input('');
  readonly submitted = output<HotelUpdateRequest & { status?: HotelStatus }>();
  readonly cancelled = output<void>();
  editing = false;
  readonly fields = [
    { key: 'name', label: 'Hotel name', type: 'text', required: true },
    { key: 'address', label: 'Address', type: 'text', required: true },
    { key: 'city', label: 'City', type: 'text', required: true },
    { key: 'country', label: 'Country', type: 'text', required: true },
    { key: 'phone', label: 'Phone', type: 'tel', required: false },
    { key: 'email', label: 'Email', type: 'email', required: false },
  ] as const;
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true }),
    address: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
    country: new FormControl('', { nonNullable: true }),
    phone: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true }),
    starRating: new FormControl(3, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1), Validators.max(5)],
    }),
    status: new FormControl<HotelStatus>('ACTIVE', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^(ACTIVE|INACTIVE)$/)],
    }),
  });
  constructor() {
    this.reset(null);
  }
  reset(hotel: Hotel | null): void {
    this.editing = !!hotel;
    for (const field of this.fields) {
      const validators: ValidatorFn[] = field.required ? [notBlank] : [];
      if (field.key === 'name') validators.push(Validators.maxLength(150));
      if (field.key === 'email') validators.push(Validators.email);
      if (field.key !== 'name')
        validators.push(Validators.maxLength(this.maximumLength(field.key)));
      this.form.controls[field.key].setValidators(validators);
    }
    this.form.reset({
      name: hotel?.name ?? '',
      address: hotel?.address ?? '',
      city: hotel?.city ?? '',
      country: hotel?.country ?? '',
      phone: hotel?.phone ?? '',
      email: hotel?.email ?? '',
      starRating: hotel?.starRating ?? 3,
      status: hotel?.status ?? 'ACTIVE',
    });
  }
  private maximumLength(key: string): number {
    if (key === 'address') return 255;
    if (key === 'phone') return 20;
    return 100;
  }
  validationMessage(key: keyof typeof this.form.controls): string {
    const control = this.form.controls[key];
    if (!control.touched || !control.errors) return '';
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('maxlength'))
      return `Use at most ${control.errors['maxlength'].requiredLength} characters.`;
    if (control.hasError('email')) return 'Enter a valid email address.';
    return 'Choose a valid value.';
  }
  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    const raw = this.form.getRawValue();
    this.submitted.emit({
      name: raw.name.trim(),
      address: raw.address.trim(),
      city: raw.city.trim(),
      country: raw.country.trim(),
      phone: raw.phone.trim() || null,
      email: raw.email.trim() || null,
      starRating: raw.starRating,
      ...(this.editing ? {} : { status: raw.status }),
    });
  }
}
