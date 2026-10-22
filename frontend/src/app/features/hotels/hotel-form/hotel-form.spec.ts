import { TestBed } from '@angular/core/testing';
import { HotelForm } from './hotel-form';
import { Hotel } from '../../../models/hotel.model';

const hotel: Hotel = {
  hotelId: 7,
  name: 'Coastal Hotel',
  address: '1 Beach Road',
  city: 'Galle',
  country: 'Sri Lanka',
  phone: null,
  email: null,
  starRating: 4,
  status: 'INACTIVE',
};
describe('HotelForm', () => {
  let component: HotelForm;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HotelForm] });
    component = TestBed.createComponent(HotelForm).componentInstance;
  });
  it('rejects blank required fields, long names and invalid email or rating', () => {
    component.reset(hotel);
    component.form.controls.name.setValue('   ');
    expect(component.form.invalid).toBe(true);
    component.form.controls.name.setValue('a'.repeat(151));
    expect(component.form.invalid).toBe(true);
    component.reset(hotel);
    component.form.controls.email.setValue('invalid');
    expect(component.form.invalid).toBe(true);
    component.reset(hotel);
    component.form.controls.starRating.setValue(6);
    expect(component.form.invalid).toBe(true);
  });
  it('maps existing country names and codes to the same selected ISO country', () => {
    component.reset(hotel);
    expect(component.form.controls.country.value).toBe('LK');
    component.reset({ ...hotel, country: 'lk' });
    expect(component.form.controls.country.value).toBe('LK');
    component.countryQuery.setValue('Sri');
    expect(component.countryOptions.some((c) => c.code === 'LK')).toBe(true);
    const emit = vi.spyOn(component.submitted, 'emit');
    component.submit();
    expect(emit).toHaveBeenCalledWith(expect.objectContaining({ country: 'Sri Lanka' }));
  });
  it('requires a recognized country and never silently replaces unknown legacy values', () => {
    component.reset({ ...hotel, country: 'Legacy country' });
    expect(component.legacyCountry).toBe('Legacy country');
    expect(component.form.invalid).toBe(true);
    component.form.controls.country.setValue('ZZ');
    expect(component.form.invalid).toBe(true);
    component.form.controls.country.setValue('');
    expect(component.form.invalid).toBe(true);
    component.form.controls.country.setValue('US');
    expect(component.form.valid).toBe(true);
    expect(component.form.controls.city.value).toBe('Galle');
  });
  it('contains the full 249-entry catalogue and filters the dropdown locally', () => {
    component.reset(null);
    expect(component.countryOptions).toHaveLength(249);
    component.countryQuery.setValue('Sri Lanka');
    expect(component.countryOptions.map((c) => c.code)).toEqual(['LK']);
    component.countryQuery.setValue('no such country');
    expect(component.countryOptions).toEqual([]);
  });
  it('applies limits to both registration and editing and allows optional contact fields', () => {
    component.reset(hotel);
    expect(component.form.valid).toBe(true);
    component.form.controls.phone.setValue('1'.repeat(21));
    expect(component.form.invalid).toBe(true);
    component.reset(null);
    component.form.patchValue({ ...hotel, phone: '1'.repeat(21), email: '' });
    expect(component.form.invalid).toBe(true);
    component.form.controls.phone.setValue('');
    expect(component.form.valid).toBe(true);
  });
  const limits = [
    ['name', 150],
    ['address', 255],
    ['city', 100],
    ['phone', 20],
    ['email', 100],
  ] as const;
  it.each(limits)(
    'rejects oversized registration %s and displays accessible feedback',
    (key, max) => {
      const fixture = TestBed.createComponent(HotelForm);
      const form = fixture.componentInstance;
      form.form.patchValue({ ...hotel, phone: '', email: '' });
      fixture.detectChanges();
      // Keep the email syntactically valid so this specifically tests length.
      const value =
        key === 'email' ? 'a@' + 'b'.repeat(50) + '.' + 'c'.repeat(max - 53) : 'a'.repeat(max);
      const input = fixture.nativeElement.querySelector('#' + key) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      expect(form.form.valid).toBe(true);
      input.value = value + 'a';
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
      expect(form.form.controls[key].hasError('maxlength')).toBe(true);
      expect(input.getAttribute('aria-invalid')).toBe('true');
      expect(input.getAttribute('aria-describedby')).toBe(key + '-error');
      expect(fixture.nativeElement.querySelector('#' + key + '-error').textContent).toContain(
        `Use at most ${max} characters.`,
      );
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
      const emit = vi.spyOn(form.submitted, 'emit');
      form.submit();
      expect(emit).not.toHaveBeenCalled();
    },
  );
  it('emits update details without ID or status and blocks invalid submission', () => {
    const emit = vi.spyOn(component.submitted, 'emit');
    component.submit();
    expect(emit).not.toHaveBeenCalled();
    component.reset(hotel);
    component.submit();
    expect(emit).toHaveBeenCalledWith({
      name: hotel.name,
      address: hotel.address,
      city: hotel.city,
      country: hotel.country,
      phone: null,
      email: null,
      starRating: 4,
    });
  });
  it('emits creation status and blocks duplicate submissions while busy', () => {
    const fixture = TestBed.createComponent(HotelForm);
    const form = fixture.componentInstance;
    form.form.patchValue({ ...hotel, phone: '', email: '' });
    const created = vi.spyOn(form.submitted, 'emit');
    form.submit();
    expect(created).toHaveBeenCalledWith(expect.objectContaining({ status: 'INACTIVE' }));
    const emit = created.mockClear();
    fixture.componentRef.setInput('busy', true);
    form.submit();
    expect(emit).not.toHaveBeenCalled();
  });
});
