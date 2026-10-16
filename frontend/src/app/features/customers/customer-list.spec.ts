import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CustomerList } from './customer-list';

const page = {
  customers: [
    { customerId: 7, name: 'Alice Perera', phone: '+94 77 123 4567', email: 'alice@example.com' },
  ],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
};

describe('CustomerList', () => {
  async function setup(response = page) {
    await TestBed.configureTestingModule({
      imports: [CustomerList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerList);
    fixture.detectChanges();
    fixture.componentInstance.editor().nativeElement.showModal = vi.fn();
    fixture.componentInstance.editor().nativeElement.close = vi.fn();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne(
        (r) =>
          r.url === '/api/customers' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '10',
      )
      .flush(response);
    fixture.detectChanges();
    return { fixture, http };
  }

  it('loads and renders customer contacts and inventory-free directory information', async () => {
    const { fixture, http } = await setup();
    expect(fixture.nativeElement.textContent).toContain('Alice Perera');
    expect(fixture.nativeElement.textContent).toContain('+94 77 123 4567');
    expect(fixture.nativeElement.textContent).toContain('alice@example.com');
    http.verify();
  });

  it('searches using the backend query and renders an empty state', async () => {
    const { fixture, http } = await setup();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#customer-search');
    input.value = '42';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.componentInstance.search();
    const request = http.expectOne(
      (r) => r.url === '/api/customers' && r.params.get('query') === '42',
    );
    request.flush({ ...page, customers: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching customers');
    http.verify();
  });

  it('queues a search submitted while the initial directory request is in flight', async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerList);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    const initial = http.expectOne((r) => r.url === '/api/customers');
    fixture.componentInstance.searchControl.setValue('Alice', { emitEvent: false });
    fixture.componentInstance.search();
    initial.flush({ ...page, customers: [] });
    const search = http.expectOne(
      (r) => r.url === '/api/customers' && r.params.get('query') === 'Alice',
    );
    search.flush(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alice Perera');
    http.verify();
  });

  it('searches automatically after typing in the search field', async () => {
    const { fixture, http } = await setup();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#customer-search');
    input.value = 'alice@example.com';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 400));
    const request = http.expectOne(
      (r) => r.url === '/api/customers' && r.params.get('query') === 'alice@example.com',
    );
    request.flush(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alice Perera');
    http.verify();
  });

  it('validates required name, contact methods and contact formats', async () => {
    const { fixture, http } = await setup();
    fixture.componentInstance.openEditor();
    fixture.detectChanges();
    fixture.componentInstance.save();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Customer name is required');
    expect(fixture.nativeElement.textContent).toContain(
      'Provide at least a phone number or email address',
    );
    fixture.componentInstance.form.setValue({ name: 'Alice', phone: 'letters', email: 'bad' });
    fixture.componentInstance.form.markAllAsTouched();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('international phone');
    expect(fixture.nativeElement.textContent).toContain('valid email');
    http.verify();
  });

  it('registers a normalized customer and prevents duplicate submission', async () => {
    const { fixture, http } = await setup({
      ...page,
      customers: [],
      totalElements: 0,
      totalPages: 0,
    });
    fixture.componentInstance.openEditor();
    fixture.componentInstance.form.setValue({
      name: ' Alice ',
      phone: ' +94 77 123 4567 ',
      email: '',
    });
    fixture.componentInstance.save();
    fixture.componentInstance.save();
    const create = http.expectOne('/api/customers');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'Alice', phone: '+94 77 123 4567', email: null });
    create.flush(page.customers[0]);
    http.expectOne((r) => r.url === '/api/customers' && r.method === 'GET').flush(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('registered successfully');
    http.verify();
  });

  it('edits without changing customer identity and shows server errors', async () => {
    const { fixture, http } = await setup();
    fixture.componentInstance.openEditor(page.customers[0]);
    fixture.componentInstance.form.controls.name.setValue('Alice Silva');
    fixture.componentInstance.save();
    const update = http.expectOne('/api/customers/7');
    expect(update.request.method).toBe('PUT');
    update.flush({ message: 'Customer update rejected' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Customer update rejected');
    expect(fixture.componentInstance.selected()?.customerId).toBe(7);
    http.verify();
  });

  it('shows loading errors and retries', async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerList);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url === '/api/customers')
      .flush({ message: 'Unavailable' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('server could not complete');
    fixture.nativeElement.querySelector('.state button').click();
    http.expectOne((r) => r.url === '/api/customers').flush(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alice Perera');
    http.verify();
  });
});
