import { directoryPage } from '../../../testing/directory-page';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HotelList } from './hotel-list';
import { Hotel } from '../../../models/hotel.model';
const url = '/api/hotels';
const hotel: Hotel = {
  hotelId: 7,
  name: 'Coastal Hotel',
  address: 'Beach Road',
  city: 'Galle',
  country: 'Sri Lanka',
  phone: null,
  email: null,
  starRating: 4,
  status: 'ACTIVE',
};
describe('Hotel management API workflows', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<HotelList>>;
  let component: HotelList;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HotelList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HotelList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.editor().nativeElement.showModal = vi.fn();
    component.editor().nativeElement.close = vi.fn();
    component.confirmation().nativeElement.showModal = vi.fn();
    component.confirmation().nativeElement.close = vi.fn();
  });
  afterEach(() => http.verify());
  function load() {
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([hotel]));
    fixture.detectChanges();
  }
  it('shows loading, renders API hotels and filters by location and status', () => {
    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Loading hotel partners');
    load();
    expect(fixture.nativeElement.textContent).toContain(hotel.name);
    component.query.set('galle');
    component.loadHotels(0);
    http
      .expectOne((r) => r.url === url + '/page' && r.params.get('query') === 'galle')
      .flush(directoryPage([hotel]));
    expect(component.filtered().length).toBe(1);
    component.filter.set('INACTIVE');
    component.loadHotels(0);
    http
      .expectOne((r) => r.url === url + '/page' && r.params.get('status') === 'INACTIVE')
      .flush(directoryPage([], 0, 0, { totalRecords: 1 }));
    expect(component.filtered().length).toBe(0);
  });
  it('shows connection failures and retries successfully', () => {
    http.expectOne((r) => r.url === url + '/page').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(component.error()).toContain('Cannot reach');
    expect(component.loading()).toBe(false);
    component.loadHotels();
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your hotel network starts here');
  });
  it('creates a hotel, blocks duplicate clicks and refreshes after success', () => {
    load();
    component.openEditor();
    const { hotelId, ...details } = hotel;
    component.save(details);
    component.save(details);
    const request = http.expectOne(url);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.hotelId).toBeUndefined();
    request.flush(hotel);
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([hotel]));
    expect(component.notice()).toContain('registered successfully');
  });
  it('updates only editable fields and keeps failed submissions visible', () => {
    load();
    component.openEditor(hotel);
    const { hotelId, ...details } = hotel;
    component.save(details);
    const request = http.expectOne(`${url}/7`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.status).toBeUndefined();
    expect(request.request.body.hotelId).toBeUndefined();
    request.flush({ message: 'Duplicate hotel' }, { status: 409, statusText: 'Conflict' });
    expect(component.submissionError()).toBe('Duplicate hotel');
    expect(component.notice()).toBe('');
    expect(component.saving()).toBe(false);
    component.save({ ...details, name: 'Updated' });
    http.expectOne(`${url}/7`).flush({ ...hotel, name: 'Updated' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.hotels()));
    expect(component.hotels()[0].name).toBe('Updated');
    expect(component.hotels()[0].status).toBe('ACTIVE');
  });
  it('requires confirmation and changes status only after successful PATCH', () => {
    load();
    component.requestStatus(hotel);
    http.expectNone(`${url}/7/status`);
    expect(component.confirmation().nativeElement.showModal).toHaveBeenCalled();
    component.changeStatus();
    component.changeStatus();
    expect(component.hotels()[0].status).toBe('ACTIVE');
    const request = http.expectOne(`${url}/7/status`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'INACTIVE' });
    request.flush({ ...hotel, status: 'INACTIVE' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.hotels()));
    expect(component.hotels()[0].status).toBe('INACTIVE');
    component.requestStatus(component.hotels()[0]);
    component.changeStatus();
    http.expectOne(`${url}/7/status`).flush({}, { status: 500, statusText: 'Server error' });
    expect(component.hotels()[0].status).toBe('INACTIVE');
    expect(component.statusError()).toContain('server');
    expect(component.notice()).toBe('');
  });
  it('requests ten records, renders the final partial page and resets on a search event', () => {
    load();
    component.loadHotels(2);
    const final = http.expectOne((r) => r.url === url + '/page' && r.params.get('page') === '2');
    final.flush(directoryPage([hotel], 2, 21));
    fixture.detectChanges();
    expect(component.directory.totalPages()).toBe(3);
    expect(component.directory.totalElements()).toBe(21);
    expect(component.filtered()).toHaveLength(1);
    const search: HTMLInputElement = fixture.nativeElement.querySelector('input[type="search"]');
    search.value = 'updated';
    search.dispatchEvent(new Event('input'));
    const filtered = http.expectOne(
      (r) =>
        r.url === url + '/page' &&
        r.params.get('page') === '0' &&
        r.params.get('query') === 'updated',
    );
    filtered.flush(directoryPage([], 0, 0, { totalRecords: 21 }));
    fixture.detectChanges();
    expect(component.directory.page()).toBe(0);
    expect(component.directory.totalElements()).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(0);
  });
});
