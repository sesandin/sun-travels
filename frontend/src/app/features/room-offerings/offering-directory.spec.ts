import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { OfferingDirectory } from './offering-directory';
import { OfferingDirectoryEntry } from '../../models/offering.model';
const url = '/api/offerings';
const row: OfferingDirectoryEntry = {
  offeringId: 8,
  hotelId: 7,
  hotelName: 'Ocean Hotel',
  hotelStatus: 'ACTIVE',
  contractId: 4,
  contractCode: 'C004',
  contractStatus: 'APPROVED',
  validFrom: '2026-01-01',
  validTo: '2026-12-31',
  roomTypeId: 3,
  roomTypeName: 'Double',
  ratePerPersonPerNight: 125.5,
  currencyCode: 'USD',
  contractedRoomCount: 12,
  maxAdults: 2,
  status: 'ENABLED',
  seasonalRateCount: 3,
};
const historical: OfferingDirectoryEntry = {
  ...row,
  offeringId: 9,
  hotelId: 10,
  hotelName: 'Palm Hotel',
  hotelStatus: 'INACTIVE',
  contractId: 5,
  contractCode: 'C005',
  contractStatus: 'TERMINATED',
  roomTypeId: 6,
  roomTypeName: 'Suite',
  status: 'SUSPENDED',
  seasonalRateCount: 0,
};
describe('Room offerings directory', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<OfferingDirectory>>;
  let c: OfferingDirectory;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OfferingDirectory],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'contracts', component: OfferingDirectory }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OfferingDirectory);
    c = fixture.componentInstance;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  function load(rows: OfferingDirectoryEntry[] = [row, historical]) {
    const req = http.expectOne(url);
    expect(req.request.method).toBe('GET');
    req.flush(rows);
    fixture.detectChanges();
  }
  it('loads the global API and displays real commercial terms including history', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading room offerings');
    expect(fixture.nativeElement.querySelector('.state output')?.textContent).toContain(
      'Loading room offerings',
    );
    load();
    const text = fixture.nativeElement.textContent;
    for (const value of [
      'Ocean Hotel',
      'Palm Hotel',
      'C004',
      'C005',
      'APPROVED',
      'TERMINATED',
      'Double',
      'Suite',
      '125.50 USD',
      'Contracted rooms',
      'Hotel INACTIVE',
      'Seasonal periods',
    ])
      expect(text).toContain(value);
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.results output')?.getAttribute('aria-live')).toBe(
      'polite',
    );
    expect(c.rows()[0].contractedRoomCount).toBe(12);
    expect(text).toContain('not live availability');
    expect(text).toContain('Per adult per night');
    expect(c.rows()[0].seasonalRateCount).toBe(3);
  });
  it('searches hotel, contract and room names case-insensitively', () => {
    load();
    for (const search of [' OCEAN ', 'c004', 'double']) {
      c.form.controls.search.setValue(search);
      expect(c.filtered()).toEqual([row]);
    }
    c.form.controls.search.setValue('missing');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching offerings');
    c.reset();
    expect(c.filtered()).toHaveLength(2);
  });
  it('combines hotel, contract, room and offering status filters using IDs', () => {
    load();
    c.form.patchValue({
      hotelId: '10',
      contractStatus: 'TERMINATED',
      roomTypeId: '6',
      offeringStatus: 'SUSPENDED',
    });
    expect(c.filtered()).toEqual([historical]);
    c.form.controls.contractStatus.setValue('DRAFT');
    expect(c.filtered()).toHaveLength(0);
    c.reset();
    expect(c.hotels()).toHaveLength(2);
    expect(c.roomTypes()).toHaveLength(2);
  });
  it('sorts numeric commercial terms and naturally sorts contract codes', () => {
    const low = {
      ...row,
      offeringId: 11,
      contractCode: 'C010',
      ratePerPersonPerNight: 20,
      contractedRoomCount: 2,
    };
    const high = {
      ...historical,
      contractCode: 'C100',
      ratePerPersonPerNight: 200,
      contractedRoomCount: 20,
    };
    load([high, low]);
    c.form.patchValue({ sortBy: 'rate', direction: 'asc' });
    expect(c.filtered()[0]).toEqual(low);
    c.form.controls.direction.setValue('desc');
    expect(c.filtered()[0]).toEqual(high);
    c.form.patchValue({ sortBy: 'contract', direction: 'asc' });
    expect(c.filtered()[0].contractCode).toBe('C010');
    c.form.patchValue({ sortBy: 'rooms', direction: 'asc' });
    expect(c.filtered()[0].contractedRoomCount).toBe(2);
  });
  it('groups rate sorting by currency rather than implying a conversion', () => {
    const eur = { ...historical, currencyCode: 'EUR', ratePerPersonPerNight: 1000 };
    load([row, eur]);
    c.form.controls.sortBy.setValue('rate');
    expect(c.filtered()[0].currencyCode).toBe('EUR');
  });
  it('provides a real empty state with a Contracts link', () => {
    load([]);
    expect(fixture.nativeElement.textContent).toContain('No room offerings yet');
    expect(fixture.nativeElement.querySelector('a[href="/contracts"]')).not.toBeNull();
  });
  it('shows failures, retries, and prevents duplicate loads', () => {
    c.load();
    http.expectOne(url).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(c.error()).toContain('Cannot reach');
    expect(fixture.nativeElement.textContent).toContain('Retry directory');
    c.load();
    load();
    expect(c.error()).toBe('');
    expect(c.rows()).toHaveLength(2);
  });
  it('links View / Manage to the corresponding existing contract and offering', async () => {
    load();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('tbody a');
    expect(link.getAttribute('href')).toBe('/contracts?contractId=4&offeringId=8');
    expect(link.getAttribute('aria-label')).toContain('Ocean Hotel');
    link.click();
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/contracts?contractId=4&offeringId=8');
  });
  it('paginates locally and resets the page when filtering changes', () => {
    load(Array.from({ length: 25 }, (_, i) => ({ ...row, offeringId: i + 1 })));
    expect(c.pageRows()).toHaveLength(20);
    c.changePage(1);
    expect(c.pageRows()).toHaveLength(5);
    c.form.controls.search.setValue('Ocean');
    expect(c.page()).toBe(1);
    c.changePage(-1);
    expect(c.page()).toBe(1);
    c.changePage(10);
    expect(c.page()).toBe(2);
  });
  it('retains typed labels and descriptive action text for keyboard/screen-reader users', () => {
    load();
    expect(fixture.nativeElement.querySelector('label[for="offering-search"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('caption').textContent).toContain(
      'not current availability',
    );
    expect(
      fixture.nativeElement.querySelector('a.manage-link').getAttribute('aria-label'),
    ).toContain('Double');
  });
});
