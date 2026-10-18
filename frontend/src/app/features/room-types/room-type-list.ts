import { DirectoryState } from '../../shared/directory-state';
import { Pagination } from '../../shared/pagination';
import {
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
  OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
  ValidatorFn,
} from '@angular/forms';
import { RoomType } from '../../models/room-type.model';
import { RoomTypeService } from '../../services/room-type.service';
import { apiError } from '../../services/api-error';
// Rejects values containing only whitespace.
const notBlank: ValidatorFn = (control) => (!control.value?.trim() ? { required: true } : null);
@Component({
  selector: 'app-room-type-list',
  imports: [Pagination, ReactiveFormsModule],
  templateUrl: './room-type-list.html',
  styleUrl: './room-type-list.scss',
})
export class RoomTypeList implements OnInit {
  private readonly service = inject(RoomTypeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly directory = new DirectoryState<RoomType>(
    (page) => this.service.page(this.query(), page),
    this.destroyRef,
    () => this.query(),
  );
  readonly roomTypes = this.directory.items;
  readonly loading = this.directory.loading;
  readonly saving = signal(false);
  readonly error = this.directory.error;
  readonly submissionError = signal('');
  readonly notice = signal('');
  readonly query = signal('');
  readonly selected = signal<RoomType | null>(null);
  readonly editor = viewChild.required<ElementRef<HTMLDialogElement>>('editor');
  readonly filtered = this.roomTypes;

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(100)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255)],
    }),
  });
  // Loads the initial data and subscriptions needed by this component.
  ngOnInit() {
    this.loadRoomTypes();
  }
  // Loads the room types.
  loadRoomTypes(page = this.directory.page()) {
    this.directory.load(page);
  }
  // Opens room-type creation or editing with the selected values.
  openEditor(row: RoomType | null = null) {
    this.selected.set(row);
    this.submissionError.set('');
    this.notice.set('');
    this.form.reset({ name: row?.name ?? '', description: row?.description ?? '' });
    this.editor().nativeElement.showModal();
  }
  // Closes the editor and clears its selection when no save is pending.
  closeEditor() {
    if (!this.saving()) this.editor().nativeElement.close();
  }
  // Returns a readable validation message for the selected form control.
  validationMessage(key: 'name' | 'description') {
    const control = this.form.controls[key];
    if (!control.touched || !control.errors) return '';
    if (control.hasError('required')) return 'Room type name is required.';
    const maximumLength = key === 'name' ? 100 : 255;
    return 'Use at most ' + maximumLength + ' characters.';
  }
  // Validates and saves a room type, then refreshes the directory.
  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.submissionError.set('');
    const raw = this.form.getRawValue();
    const details = { name: raw.name.trim(), description: raw.description.trim() || null };
    const row = this.selected();
    const request = row
      ? this.service.updateRoomType(row.roomTypeId, details)
      : this.service.createRoomType(details);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.roomTypes.update((rows) => {
          let updated;
          if (row) {
            updated = rows.map((r) => {
              if (r.roomTypeId === row.roomTypeId) return saved;
              return r;
            });
          } else {
            updated = [...rows, saved];
          }
          return updated.sort((a, b) => a.name.localeCompare(b.name));
        });
        this.directory.load(this.directory.page(), true);
        this.saving.set(false);
        this.closeEditor();
        this.notice.set('Room type ' + (row ? 'updated' : 'created') + ' successfully.');
      },
      error: (error) => {
        this.saving.set(false);
        this.submissionError.set(this.message(error));
      },
    });
  }
  // Converts a room-type request failure into readable feedback.
  private message(error: unknown) {
    return apiError(error).replace(/hotel/gi, 'room type');
  }
}
