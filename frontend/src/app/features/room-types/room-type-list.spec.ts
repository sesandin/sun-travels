import { directoryPage } from '../../testing/directory-page';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RoomTypeList } from './room-type-list';
const url = '/api/room-types';
const row = { roomTypeId: 7, name: 'Deluxe', description: 'Sea view' };
describe('Room Type Management', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<RoomTypeList>>;
  let component: RoomTypeList;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RoomTypeList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RoomTypeList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.editor().nativeElement.showModal = vi.fn();
    component.editor().nativeElement.close = vi.fn();
  });
  afterEach(() => http.verify());
  // Completes the pending API request with test fixture data.
  function load() {
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([row]));
    fixture.detectChanges();
  }
  it('shows loading, real API data, search and no matches', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading room types');
    load();
    expect(fixture.nativeElement.textContent).toContain('Deluxe');
    component.query.set('sea');
    component.loadRoomTypes(0);
    http
      .expectOne((r) => r.url === url + '/page' && r.params.get('query') === 'sea')
      .flush(directoryPage([row]));
    expect(component.filtered()).toHaveLength(1);
    component.query.set('unknown');
    component.loadRoomTypes(0);
    http
      .expectOne((r) => r.url === url + '/page')
      .flush(directoryPage([], 0, 0, { totalRecords: 1 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No Matching Room Types');
  });
  it('shows errors and retries to an empty state', () => {
    http.expectOne((r) => r.url === url + '/page').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(component.error()).toContain('Cannot reach');
    component.loadRoomTypes();
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No Room Types Yet');
  });
  it('validates blank names and both length limits without sending writes', () => {
    load();
    component.openEditor();
    component.form.controls.name.setValue('  ');
    component.save();
    expect(component.validationMessage('name')).toContain('required');
    component.form.setValue({ name: 'a'.repeat(101), description: '' });
    component.save();
    expect(component.form.invalid).toBe(true);
    component.form.setValue({ name: 'Valid', description: 'a'.repeat(256) });
    component.save();
    expect(component.form.invalid).toBe(true);
    http.expectNone(url);
  });
  it('creates normalized data and prevents repeated submissions', () => {
    load();
    component.openEditor();
    component.form.setValue({ name: ' Standard ', description: ' ' });
    component.save();
    component.save();
    const request = http.expectOne(url);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Standard', description: null });
    request.flush({ roomTypeId: 8, name: 'Standard', description: null });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.roomTypes()));
    expect(component.roomTypes()).toHaveLength(2);
    expect(component.notice()).toContain('created successfully');
  });
  it('preserves edits after conflict and updates on successful retry', () => {
    load();
    component.openEditor(row);
    component.form.controls.name.setValue('Updated');
    component.save();
    const request = http.expectOne(url + '/7');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.roomTypeId).toBeUndefined();
    request.flush({ message: 'Name already exists' }, { status: 409, statusText: 'Conflict' });
    expect(component.submissionError()).toBe('Name already exists');
    expect(component.form.controls.name.value).toBe('Updated');
    expect(component.roomTypes()[0].name).toBe('Deluxe');
    component.save();
    http.expectOne(url + '/7').flush({ ...row, name: 'Updated' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.roomTypes()));
    expect(component.roomTypes()[0].name).toBe('Updated');
    expect(component.notice()).toContain('updated successfully');
  });
  it('resets the editor and prevents closing during save', () => {
    load();
    component.openEditor(row);
    expect(component.form.controls.name.value).toBe(row.name);
    component.openEditor();
    expect(component.form.controls.name.value).toBe('');
    component.form.controls.name.setValue('New');
    component.save();
    component.closeEditor();
    expect(component.editor().nativeElement.close).not.toHaveBeenCalled();
    http.expectOne(url).flush({ ...row, name: 'New' });
    http.expectOne((r) => r.url === url + '/page').flush(directoryPage(component.roomTypes()));
  });
  it('requests ten records, renders the final partial page and resets on a search event', () => {
    load();
    component.loadRoomTypes(2);
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
