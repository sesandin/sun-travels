import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RoomTypeList } from './room-type-list';
const url = '/api/room-types';
const row = { roomTypeId: 7, name: 'Deluxe', description: 'Sea view' };
describe('Room type management', () => {
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
  function load() {
    http.expectOne(url).flush([row]);
    fixture.detectChanges();
  }
  it('shows loading, real API data, search and no matches', () => {
    expect(fixture.nativeElement.textContent).toContain('Loading room types');
    load();
    expect(fixture.nativeElement.textContent).toContain('Deluxe');
    component.query.set('sea');
    expect(component.filtered()).toHaveLength(1);
    component.query.set('unknown');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching room types');
  });
  it('shows errors and retries to an empty state', () => {
    http.expectOne(url).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(component.error()).toContain('Cannot reach');
    component.loadRoomTypes();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No room types yet');
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
  });
});
