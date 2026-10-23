import { Component, ElementRef, input, output, viewChild } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Hotel, HotelStatus, HotelUpdateRequest } from '../../../models/hotel.model';
import { countries, findCountry } from '../../../shared/location/country-catalog';
// Rejects values containing only whitespace.
const notBlank: ValidatorFn = (control: AbstractControl) =>
  typeof control.value === 'string' && !control.value.trim() ? { required: true } : null;
@Component({
  selector: 'app-hotel-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './hotel-form.html',
  styleUrl: './hotel-form.scss',
  host: { '(document:click)': 'closeCountryPickerOutside($event)' },
})
export class HotelForm {
  readonly busy = input(false);
  readonly error = input('');
  readonly submitted = output<HotelUpdateRequest & { status?: HotelStatus }>();
  readonly cancelled = output<void>();
  editing = false;
  readonly countryQuery = new FormControl('', { nonNullable: true });
  private readonly countryPicker = viewChild<ElementRef<HTMLDetailsElement>>('countryPicker');
  legacyCountry = '';
  get selectedCountryName(): string {
    return findCountry(this.form.controls.country.value)?.name ?? '';
  }

  countryPickerClicked(event: MouseEvent): void {
    if (this.busy()) event.preventDefault();
  }

  closeCountryPickerOutside(event: MouseEvent): void {
    const picker = this.countryPicker()?.nativeElement;
    if (picker?.open && event.target instanceof Node && !picker.contains(event.target)) {
      picker.open = false;
    }
  }

  selectCountry(code: string, picker: HTMLDetailsElement): void {
    this.form.controls.country.setValue(code);
    this.form.controls.country.markAsDirty();
    this.form.controls.country.markAsTouched();
    picker.open = false;
    picker.querySelector('summary')?.focus();
  }

  countryPickerToggled(picker: HTMLDetailsElement): void {
    if (picker.open) {
      this.countryQuery.setValue('');
      picker.querySelector('input')?.focus();
    }
  }

  get countryOptions() {
    const query = this.countryQuery.value.trim().toLocaleLowerCase('en');
    return countries.filter(
      (country) =>
        country.name.toLocaleLowerCase('en').includes(query) ||
        country.code.toLowerCase().includes(query),
    );
  }
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
  // Creates an empty object for fields to be populated after construction.
  constructor() {
    this.reset(null);
  }
  // Populates or clears the hotel form for the selected editing mode.
  reset(hotel: Hotel | null): void {
    this.editing = !!hotel;
    for (const field of this.fields) {
      const validators: ValidatorFn[] = field.required ? [notBlank] : [];
      validators.push(Validators.maxLength(this.maximumLength(field.key)));
      if (field.key === 'email') validators.push(Validators.email);
      if (field.key === 'country')
        validators.push((control) =>
          !control.value || findCountry(control.value) ? null : { country: true },
        );
      this.form.controls[field.key].setValidators(validators);
    }
    this.countryQuery.setValue('');
    this.legacyCountry = hotel?.country && !findCountry(hotel.country) ? hotel.country : '';
    this.form.reset({
      name: hotel?.name ?? '',
      address: hotel?.address ?? '',
      city: hotel?.city ?? '',
      country: hotel?.country ? (findCountry(hotel.country)?.code ?? hotel.country) : '',
      phone: hotel?.phone ?? '',
      email: hotel?.email ?? '',
      starRating: hotel?.starRating ?? 3,
      status: hotel?.status ?? 'ACTIVE',
    });
  }

  // Returns a readable validation message for the selected form control.
  validationMessage(key: keyof typeof this.form.controls): string {
    const control = this.form.controls[key];
    if (!control.touched || !control.errors) return '';
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('maxlength'))
      return `Use at most ${control.errors['maxlength'].requiredLength} characters.`;
    if (control.hasError('email')) return 'Enter a valid email address.';
    return 'Choose a valid value.';
  }

  // Validates hotel fields and emits the create or update request.
  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    const raw = this.form.getRawValue();
    this.submitted.emit({
      name: raw.name.trim(),
      address: raw.address.trim(),
      city: raw.city.trim(),
      country: findCountry(raw.country)!.name,
      phone: raw.phone.trim() || null,
      email: raw.email.trim() || null,
      starRating: raw.starRating,
      ...(this.editing ? {} : { status: raw.status }),
    });
  }

  // Returns the configured length limit for a hotel form field.
  maximumLength(key: string): number {
    if (key === 'name') return 150;
    if (key === 'address') return 255;
    if (key === 'phone') return 20;
    return 100;
  }
}
