import { directoryPage } from '../../testing/directory-page';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ContractList } from './contract-list';
import { Contract } from '../../models/contract.model';
const url = '/api/contracts';
const hotelsUrl = '/api/hotels';
const row: Contract = {
  contractId: 7,
  contractCode: 'C-2026',
  hotelId: 3,
  hotelName: 'Ocean Hotel',
  validFrom: '2026-01-01',
  validTo: '2026-12-31',
  markupPercentage: 15,
  currencyCode: 'USD',
  status: 'DRAFT',
};
describe('Contract management', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<ContractList>>;
  let component: ContractList;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ContractList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ContractList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });
  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });
  // Completes the pending API request with test fixture data.
  function load(rows: Contract[] = [row]) {
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(rows));
    http.expectOne(hotelsUrl).flush([{ hotelId: 3, name: 'Ocean Hotel' }]);
    fixture.detectChanges();
  }
  // Populates the form with valid test input.
  function fill() {
    component.form.setValue({
      hotelId: 3,
      validFrom: row.validFrom,
      validTo: row.validTo,
      markupPercentage: 15,
      currencyCode: 'USD',
    });
  }
  it.each(['DRAFT', 'APPROVED', 'SUSPENDED', 'TERMINATED'] as const)(
    'shows Expired alongside the unchanged %s status for a past validTo',
    (status) => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
      const contract = Object.freeze({ ...row, validTo: '2026-10-18', status });
      load([contract]);

      const rendered: HTMLElement = fixture.nativeElement.querySelector('tbody tr');
      expect(rendered.querySelector('.status-badge')?.textContent?.trim()).toBe(status);
      expect(rendered.querySelector('.status-badge')?.getAttribute('data-status')).toBe(status);
      expect(rendered.querySelector('.expired-badge')?.textContent).toBe('Expired');
      expect(component.contracts()[0].status).toBe(status);
      expect(component.statuses).toEqual(['DRAFT', 'APPROVED', 'SUSPENDED', 'TERMINATED']);
    },
  );
  it.each(['2026-10-19', '2026-10-20'])(
    'does not mark today or a future validTo (%s) expired near local midnight',
    (validTo) => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
      load([{ ...row, validTo }]);

      expect(fixture.nativeElement.querySelector('.expired-badge')).toBeNull();
      expect(fixture.nativeElement.querySelector('.status-badge').textContent.trim()).toBe('DRAFT');
    },
  );
  it('compares calendar dates across a year boundary', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2027, 0, 1, 23, 45));
    load([
      { ...row, validTo: '2026-12-31' },
      { ...row, contractId: 8, validTo: '2027-01-01' },
      { ...row, contractId: 9, validTo: '2027-01-02' },
    ]);

    const rendered = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rendered[0].querySelector('.expired-badge')).not.toBeNull();
    expect(rendered[1].querySelector('.expired-badge')).toBeNull();
    expect(rendered[2].querySelector('.expired-badge')).toBeNull();
  });
  it.each([
    ['2026-10-18', ['Details']],
    ['2026-10-19', ['Details', 'Edit markup', 'Suspend', 'Terminate']],
    ['2026-10-20', ['Details', 'Edit markup', 'Suspend', 'Terminate']],
  ] as const)('shows the appropriate approved-contract actions for validTo %s', (validTo, actions) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
    load([{ ...row, status: 'APPROVED', validTo }]);

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('tbody tr .actions button') as NodeListOf<HTMLButtonElement>,
    );
    expect(buttons.map((button) => button.textContent?.trim())).toEqual([...actions]);
    expect(component.contracts()[0].status).toBe('APPROVED');
    expect(buttons[0].disabled).toBe(false);
  });
  it('hides termination for expired drafts and suspended contracts while preserving their other actions', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 19, 0, 15));
    load([
      { ...row, validTo: '2026-10-18', status: 'DRAFT' },
      { ...row, contractId: 8, validTo: '2026-10-18', status: 'SUSPENDED' },
    ]);
    const rows = fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>;
    const labels = (element: HTMLElement) =>
      Array.from(element.querySelectorAll('.actions button')).map((button) => button.textContent?.trim());
    expect(labels(rows[0])).toEqual(['Details', 'Edit', 'Approve']);
    expect(labels(rows[1])).toEqual(['Details', 'Reapprove']);
  });
  it('recalculates expiration when refreshing after the calendar day changes', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 19, 23, 59));
    load([{ ...row, validTo: '2026-10-19' }]);
    expect(fixture.nativeElement.querySelector('.expired-badge')).toBeNull();

    vi.setSystemTime(new Date(2026, 9, 20, 0, 0));
    component.loadContracts();
    http
      .expectOne((r) => r.url === url + '/page')
      .flush(directoryPage([{ ...row, validTo: '2026-10-19' }]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.expired-badge')?.textContent).toBe('Expired');
  });
  it('lists real data with loading, search, status filtering and empty matches', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading contracts');
    load([row, { ...row, contractId: 8, status: 'SUSPENDED' }]);
    component.query.set('ocean');
    component.loadContracts(0);
    http
      .expectOne((r) => r.url === url + '/page' && r.params.get('query') === 'ocean')
      .flush(directoryPage([row, { ...row, contractId: 8, status: 'SUSPENDED' }]));
    expect(component.filtered()).toHaveLength(2);
    component.statusFilter.set('SUSPENDED');
    component.loadContracts(0);
    http
      .expectOne((r) => r.url === url + '/page' && r.params.get('status') === 'SUSPENDED')
      .flush(directoryPage([{ ...row, status: 'SUSPENDED' }], 0, 1, { totalRecords: 2 }));
    expect(component.filtered()).toHaveLength(1);
    component.query.set('unknown');
    component.loadContracts(0);
    http
      .expectOne((r) => r.url === url + '/page')
      .flush(directoryPage([], 0, 0, { totalRecords: 2 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching contracts');
  });
  it('handles list errors, retries and the empty state', () => {
    http.expectOne((r) => r.url === url + '/page').error(new ProgressEvent('error'));
    http.expectOne(hotelsUrl).flush([]);
    fixture.detectChanges();
    expect(component.error()).toContain('Cannot reach');
    component.loadContracts();
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No Contracts Yet');
  });
  it('blocks creation when hotels cannot be loaded and supports retry', () => {
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([]));
    http.expectOne(hotelsUrl).error(new ProgressEvent('error'));
    component.openEditor();
    fill();
    component.save();
    http.expectNone(url);
    expect(component.hotelError()).toContain('Cannot reach');
    component.loadHotels();
    http.expectOne(hotelsUrl).flush([{ hotelId: 3, name: 'Ocean Hotel' }]);
    expect(component.hotelError()).toBe('');
  });
  it('validates required fields, date range, markup precision and currency', () => {
    load();
    component.openEditor();
    component.save();
    expect(component.form.invalid).toBe(true);
    fill();
    component.form.controls.validFrom.setValue('2027-01-01');
    expect(component.form.hasError('dateRange')).toBe(true);
    fill();
    for (const value of [-1, 1000, 1.001]) {
      component.form.controls.markupPercentage.setValue(value);
      component.save();
      expect(component.form.invalid).toBe(true);
    }
    fill();
    component.form.controls.currencyCode.setValue('usd');
    component.save();
    expect(component.form.invalid).toBe(true);
    http.expectNone(url);
  });
  it('creates a DRAFT payload without status or IDs and prevents double submission', () => {
    load();
    component.openEditor();
    fill();
    component.save();
    component.save();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      hotelId: 3,
      validFrom: row.validFrom,
      validTo: row.validTo,
      markupPercentage: 15,
      currencyCode: 'USD',
    });
    component.closeEditor();
    expect(component.mode()).toBe('create');
    req.flush({ ...row, contractId: 8, contractCode: 'NEW' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    expect(component.contracts()).toHaveLength(2);
    expect(component.notice()).toContain('saved successfully');
    expect(component.mode()).toBeNull();
  });
  it('edits drafts and preserves form values after backend conflicts', () => {
    load();
    component.openEditor(row);
    expect(component.selected()?.contractCode).toBe(row.contractCode);
    component.form.controls.currencyCode.setValue('LKR');
    component.save();
    const req = http.expectOne(url + '/7');
    expect(req.request.method).toBe('PUT');
    req.flush({ message: 'Contract code already exists' }, { status: 409, statusText: 'Conflict' });
    expect(component.submissionError()).toBe('Contract code already exists');
    expect(component.form.controls.currencyCode.value).toBe('LKR');
    component.save();
    http.expectOne(url + '/7').flush({ ...row, currencyCode: 'LKR' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    expect(component.contracts()[0].currencyCode).toBe('LKR');
  });
  it('sends only markup for approved contracts and blocks invalid markup', () => {
    const approved: Contract = { ...row, status: 'APPROVED' };
    load([approved]);
    component.openEditor(approved);
    expect(component.mode()).toBe('markup');
    component.markupForm.controls.markupPercentage.setValue(1000);
    component.save();
    http.expectNone(url + '/7/markup');
    component.markupForm.controls.markupPercentage.setValue(25.5);
    component.save();
    const req = http.expectOne(url + '/7/markup');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ markupPercentage: 25.5 });
    req.flush({ ...approved, markupPercentage: 25.5 });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    expect(component.contracts()[0].markupPercentage).toBe(25.5);
  });
  it('shows backend validation errors without fake success', () => {
    load();
    component.openEditor();
    fill();
    component.save();
    http
      .expectOne(url)
      .flush(
        { fieldErrors: { currencyCode: 'Use uppercase' } },
        { status: 400, statusText: 'Bad request' },
      );
    expect(component.submissionError()).toContain('currencyCode: Use uppercase');
    expect(component.notice()).toBe('');
    expect(component.contracts()).toHaveLength(1);
  });
  it('fetches details and provides implemented approval action', () => {
    load();
    component.showDetails(row);
    http.expectOne(url + '/7').flush(row);
    fixture.detectChanges();
    const approve = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.trim() === 'Approve');
    expect(approve?.disabled).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('conflicting confirmed commitments');
    http.expectOne(url + '/7/offerings').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No offerings for this contract');
  });
  it('shows detail fetch failure and allows closing', () => {
    load();
    component.showDetails(row);
    http
      .expectOne(url + '/7')
      .flush({ message: 'Contract not found' }, { status: 404, statusText: 'Not found' });
    expect(component.submissionError()).toBe('Contract not found');
    component.closeEditor();
    expect(component.mode()).toBeNull();
  });
  it('creates without code input and displays generated reference', () => {
    load();
    component.openEditor();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[formControlName="contractCode"]')).toBeNull();
    fill();
    component.save();
    const req = http.expectOne(url);
    expect(req.request.body.contractCode).toBeUndefined();
    req.flush({ ...row, contractId: 8, contractCode: 'C008' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    expect(component.notice()).toContain('C008');
  });
  it('shows lifecycle actions for eligible statuses', () => {
    load([
      row,
      { ...row, contractId: 8, status: 'SUSPENDED' },
      { ...row, contractId: 9, status: 'APPROVED' },
      { ...row, contractId: 10, status: 'TERMINATED' },
    ]);
    const rows = Array.from(
      fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>,
    );
    expect(rows[0].textContent).toContain('Approve');
    expect(rows[0].textContent).not.toContain('Reapprove');
    expect(rows[1].textContent).toContain('Reapprove');
    expect(rows[1].textContent).not.toContain('Suspend');
    expect(rows[2].textContent).toContain('Suspend');
    expect(rows[2].textContent).not.toContain('Reapprove');
    expect(rows[3].textContent).not.toContain('Terminate');
    expect(rows[3].textContent).not.toContain('Approve');
  });
  it('approves drafts once and updates stored status', () => {
    load();
    component.confirmAction(row, 'approve');
    component.save();
    component.save();
    const req = http.expectOne(url + '/7/approve');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({});
    component.closeEditor();
    expect(component.mode()).toBe('approve');
    req.flush({ ...row, status: 'APPROVED' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    expect(component.contracts()[0].status).toBe('APPROVED');
    expect(component.selected()?.status).toBe('APPROVED');
    expect(component.mode()).toBeNull();
  });
  it('preserves DRAFT and displays failed-approval conflict', () => {
    load();
    component.confirmAction(row, 'approve');
    component.save();
    http
      .expectOne(url + '/7/approve')
      .flush(
        { message: 'Approval blocked by CONFIRMED reservation 12' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(component.contracts()[0].status).toBe('DRAFT');
    expect(component.submissionError()).toContain('CONFIRMED reservation 12');
    expect(component.notice()).toBe('');
    expect(component.mode()).toBe('approve');
  });
  it('reapproves suspended contracts and retains state on conflict', () => {
    const suspended: Contract = { ...row, status: 'SUSPENDED' };
    load([suspended]);
    component.confirmAction(suspended, 'reapprove');
    component.save();
    http
      .expectOne(url + '/7/reapprove')
      .flush(
        { message: 'Validity overlaps APPROVED contract C099' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(component.contracts()[0].status).toBe('SUSPENDED');
    expect(component.submissionError()).toContain('C099');
    component.save();
    http.expectOne(url + '/7/reapprove').flush({ ...row, status: 'APPROVED' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    expect(component.contracts()[0].status).toBe('APPROVED');
  });
  it('keeps outstanding-commitment termination failure visible', () => {
    const approved: Contract = { ...row, status: 'APPROVED' };
    load([approved]);
    component.confirmAction(approved, 'terminate');
    component.save();
    http.expectOne(url + '/7/terminate').flush(
      {
        message:
          'Cannot terminate this contract because it has 2 ongoing or upcoming confirmed reservations. Wait until these stays are completed or resolve them through the existing cancellation process.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(component.contracts()[0].status).toBe('APPROVED');
    expect(component.submissionError()).toContain('2 ongoing or upcoming confirmed reservations');
    expect(component.notice()).toBe('');
    expect(component.mode()).toBe('terminate');
  });
  it('confirms suspension and termination and hides editing for historical statuses', () => {
    const approved: Contract = { ...row, status: 'APPROVED' };
    load([approved]);
    component.confirmAction(approved, 'suspend');
    component.save();
    const suspend = http.expectOne(url + '/7/suspend');
    expect(suspend.request.method).toBe('PATCH');
    suspend.flush({ ...row, status: 'SUSPENDED' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    const suspended = component.contracts()[0];
    component.openEditor(suspended);
    expect(component.mode()).toBeNull();
    component.confirmAction(suspended, 'terminate');
    component.save();
    http.expectOne(url + '/7/terminate').flush({ ...row, status: 'TERMINATED' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.contracts()));
    component.confirmAction(component.contracts()[0], 'terminate');
    expect(component.mode()).toBeNull();
  });
  it('requests ten records, renders the final partial page and resets on a search event', () => {
    load();
    component.loadContracts(2);
    const final = http.expectOne((r) => r.url === url + '/page' && r.params.get('page') === '2');
    final.flush(directoryPage([row], 2, 21));
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
