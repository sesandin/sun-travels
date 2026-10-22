import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { HotelForm } from '../features/hotels/hotel-form/hotel-form';
import { RoomTypeList } from '../features/room-types/room-type-list';
import { CustomerList } from '../features/customers/customer-list';
import { ReservationBooking } from '../features/reservations/reservation-booking';
import { ReservationDirectory } from '../features/reservations/reservation-directory';
import { ContractList } from '../features/contracts/contract-list';
import { Login } from '../features/auth/login';
import { money, positiveInteger } from '../features/contracts/offering-validators';
import { FormControl } from '@angular/forms';

describe('Database-aligned form boundaries', () => {
  beforeEach(() => TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  }));

  it.each([['name', 150], ['address', 255], ['city', 100], ['phone', 20], ['email', 100]] as const)(
    'validates hotel %s during creation and editing, including pasted values', (field, max) => {
      const fixture = TestBed.createComponent(HotelForm);
      const c = fixture.componentInstance;
      const boundary = field === 'email' ? 'a@' + 'b'.repeat(50) + '.' + 'c'.repeat(max - 53) : 'a'.repeat(max);
      for (const editing of [false, true]) {
        c.editing = editing;
        const control = c.form.controls[field];
        control.setValue(boundary);
        expect(control.valid).toBe(true);
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.nativeElement.querySelector('#' + field);
        expect(input.maxLength).toBe(max);
        input.value = boundary + 'a';
        input.dispatchEvent(new Event('input'));
        control.markAsTouched();
        expect(control.hasError('maxlength')).toBe(true);
        expect(c.validationMessage(field)).toBe(`Use at most ${max} characters.`);
      }
    });

  it('keeps room type name and textarea limits on programmatic edits', () => {
    const c = TestBed.createComponent(RoomTypeList).componentInstance;
    for (const [field, max] of [['name', 100], ['description', 255]] as const) {
      const control = c.form.controls[field];
      control.setValue('a'.repeat(max));
      expect(control.valid).toBe(true);
      control.setValue('a'.repeat(max + 1));
      expect(control.hasError('maxlength')).toBe(true);
    }
    c.form.controls.name.setValue('   ');
    expect(c.form.controls.name.invalid).toBe(true);
  });

  it('uses the same customer boundaries in management and booking registration', () => {
    const customer = TestBed.createComponent(CustomerList).componentInstance;
    const booking = TestBed.createComponent(ReservationBooking).componentInstance;
    for (const form of [customer.form, booking.registration]) {
      for (const [field, max] of [['name', 150], ['phone', 20], ['email', 150]] as const) {
        const boundary = field === 'email' ? 'a'.repeat(64) + '@' + 'b'.repeat(50) + '.' + 'c'.repeat(max - 116)
          : field === 'phone' ? '1'.repeat(max) : 'a'.repeat(max);
        form.controls[field].setValue(boundary);
        expect(form.controls[field].valid).toBe(true);
        form.controls[field].setValue(boundary + 'a');
        expect(form.controls[field].hasError('maxlength')).toBe(true);
      }
      form.controls.name.setValue('  ');
      expect(form.controls.name.invalid).toBe(true);
    }
    booking.registration.controls.name.markAsTouched();
    expect(booking.registrationMessage('name')).toBe('Customer name is required.');
  });

  it('validates login boundaries without trimming the password', () => {
    const c = TestBed.createComponent(Login).componentInstance;
    for (const [field, max] of [['username', 100], ['password', 200]] as const) {
      c.form.controls[field].setValue('a'.repeat(max));
      expect(c.form.controls[field].valid).toBe(true);
      c.form.controls[field].setValue('a'.repeat(max + 1));
      expect(c.form.controls[field].hasError('maxlength')).toBe(true);
      c.form.controls[field].setValue('   ');
      expect(c.form.controls[field].invalid).toBe(true);
    }
    c.form.controls.password.setValue(' secret ');
    expect(c.form.controls.password.value).toBe(' secret ');
    expect(c.form.controls.password.valid).toBe(true);
  });

  it('validates numeric precision, currency and signed INT bounds', () => {
    const c = TestBed.createComponent(ContractList).componentInstance;
    c.form.controls.currencyCode.setValue('USD');
    expect(c.form.controls.currencyCode.valid).toBe(true);
    c.form.controls.currencyCode.setValue('USDX');
    expect(c.form.controls.currencyCode.invalid).toBe(true);
    for (const value of [2147483648, 1.5, 0]) {
      c.form.controls.hotelId.setValue(value);
      expect(c.form.controls.hotelId.invalid).toBe(true);
      expect(positiveInteger(new FormControl(value))).not.toBeNull();
    }
    c.form.controls.markupPercentage.setValue(999.99);
    expect(c.form.controls.markupPercentage.valid).toBe(true);
    for (const value of [1000, 0.001, -1]) {
      c.form.controls.markupPercentage.setValue(value);
      expect(c.form.controls.markupPercentage.invalid).toBe(true);
    }
    expect(money(new FormControl(99999999.99))).toBeNull();
    for (const value of [100000000, 0.001, 0, -1]) expect(money(new FormControl(value))).not.toBeNull();
  });

  it('rejects programmatically populated overlength reservation search text', () => {
    const c = TestBed.createComponent(ReservationDirectory).componentInstance;
    c.query.setValue('a'.repeat(150));
    expect(c.query.valid).toBe(true);
    c.query.setValue('a'.repeat(151));
    expect(c.filters.invalid).toBe(true);
  });
});
