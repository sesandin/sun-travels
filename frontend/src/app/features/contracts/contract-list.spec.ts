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
  afterEach(() => http.verify());
  function load(rows: Contract[] = [row]) {
    http.expectOne(url).flush(rows);
    http.expectOne(hotelsUrl).flush([{ hotelId: 3, name: 'Ocean Hotel' }]);
    fixture.detectChanges();
  }
  function fill() {
    component.form.setValue({
      hotelId: 3,
      validFrom: row.validFrom,
      validTo: row.validTo,
      markupPercentage: 15,
      currencyCode: 'USD',
    });
  }
  it('lists real data with loading, search, status filtering and empty matches', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading contracts');
    load([row, { ...row, contractId: 8, status: 'SUSPENDED' }]);
    component.query.set('ocean');
    expect(component.filtered()).toHaveLength(2);
    component.statusFilter.set('SUSPENDED');
    expect(component.filtered()).toHaveLength(1);
    component.query.set('unknown');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching contracts');
  });
  it('handles list errors, retries and the empty state', () => {
    http.expectOne(url).error(new ProgressEvent('error'));
    http.expectOne(hotelsUrl).flush([]);
    fixture.detectChanges();
    expect(component.error()).toContain('Cannot reach');
    component.loadContracts();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No contracts yet');
  });
  it('blocks creation when hotels cannot be loaded and supports retry', () => {
    http.expectOne(url).flush([]);
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
    expect(component.contracts()[0].status).toBe('APPROVED');
  });
  it('keeps outstanding-commitment termination failure visible', () => {
    const approved: Contract = { ...row, status: 'APPROVED' };
    load([approved]);
    component.confirmAction(approved, 'terminate');
    component.save();
    http
      .expectOne(url + '/7/terminate')
      .flush(
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
    const suspended = component.contracts()[0];
    component.openEditor(suspended);
    expect(component.mode()).toBeNull();
    component.confirmAction(suspended, 'terminate');
    component.save();
    http.expectOne(url + '/7/terminate').flush({ ...row, status: 'TERMINATED' });
    component.confirmAction(component.contracts()[0], 'terminate');
    expect(component.mode()).toBeNull();
  });
});
