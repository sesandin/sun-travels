import { DirectoryState } from '../../../shared/directory-state';
import { Pagination } from '../../../shared/pagination';
import { Icon } from '../../../shared/icon';
import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HotelService } from '../../../services/hotel.service';
import { Hotel, HotelStatus, HotelUpdateRequest } from '../../../models/hotel.model';
import { apiError } from '../../../services/api-error';
import { HotelForm } from '../hotel-form/hotel-form';
@Component({
  selector: 'app-hotel-list',
  standalone: true,
  imports: [Pagination, HotelForm, Icon],
  templateUrl: './hotel-list.html',
  styleUrl: './hotel-list.scss',
})
export class HotelList implements OnInit {
  private readonly service = inject(HotelService);
  private readonly destroyRef = inject(DestroyRef);
  readonly directory = new DirectoryState<Hotel>(
    (page) => this.service.page(this.query(), this.filter(), page),
    this.destroyRef,
    () => [this.query(), this.filter()],
  );
  readonly hotels = this.directory.items;
  readonly loading = this.directory.loading;
  readonly error = this.directory.error;
  readonly notice = signal('');
  readonly query = signal('');
  readonly filter = signal('');
  readonly selected = signal<Hotel | null>(null);
  readonly saving = signal(false);
  readonly submissionError = signal('');
  readonly statusTarget = signal<Hotel | null>(null);
  readonly statusBusy = signal(false);
  readonly statusError = signal('');
  readonly editor = viewChild.required<ElementRef<HTMLDialogElement>>('editor');
  readonly confirmation = viewChild.required<ElementRef<HTMLDialogElement>>('confirmation');
  readonly form = viewChild.required(HotelForm);
  readonly activeCount = computed(() => Number(this.directory.summary()['activeRecords'] ?? 0));
  readonly filtered = this.hotels;

  // Loads the initial data and subscriptions needed by this component.
  ngOnInit(): void {
    this.loadHotels();
  }
  // Loads the hotels.
  loadHotels(page = this.directory.page()) {
    this.directory.load(page);
  }
  // Opens the hotel form for creation or editing.
  openEditor(hotel: Hotel | null = null): void {
    this.selected.set(hotel);
    this.submissionError.set('');
    this.notice.set('');
    this.form().reset(hotel);
    this.editor().nativeElement.showModal();
  }
  // Closes the editor and clears its selection when no save is pending.
  closeEditor(): void {
    if (!this.saving()) this.editor().nativeElement.close();
  }
  // Saves hotel details and refreshes the hotel directory.
  save(details: HotelUpdateRequest & { status?: HotelStatus }): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.submissionError.set('');
    const hotel = this.selected();
    const { status, ...update } = details;
    const request = hotel
      ? this.service.updateHotel(hotel.hotelId, update)
      : this.service.createHotel({ ...update, status: status ?? 'ACTIVE' });
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.closeEditor();
        if (hotel)
          this.hotels.update((list) => list.map((h) => (h.hotelId === hotel.hotelId ? saved : h)));
        this.directory.load(this.directory.page(), true);
        this.notice.set(`Hotel ${hotel ? 'updated' : 'registered'} successfully.`);
      },
      error: (error) => {
        this.saving.set(false);
        this.submissionError.set(apiError(error));
      },
    });
  }
  // Opens confirmation for changing the selected hotel’s active status.
  requestStatus(hotel: Hotel): void {
    if (this.statusBusy()) return;
    this.statusTarget.set(hotel);
    this.statusError.set('');
    this.notice.set('');
    this.confirmation().nativeElement.showModal();
  }
  // Closes the hotel status confirmation when no change is pending.
  closeStatus(): void {
    if (!this.statusBusy()) this.confirmation().nativeElement.close();
  }
  // Submits the confirmed hotel status change and refreshes the directory.
  changeStatus(): void {
    const hotel = this.statusTarget();
    if (!hotel || this.statusBusy()) return;
    this.statusBusy.set(true);
    this.statusError.set('');
    this.service
      .updateHotelStatus(hotel.hotelId, hotel.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.hotels.update((list) => list.map((h) => (h.hotelId === hotel.hotelId ? saved : h)));
          this.directory.load(this.directory.page(), true);
          this.statusBusy.set(false);
          this.closeStatus();
          this.notice.set(`${saved.name} is now ${saved.status.toLowerCase()}.`);
        },
        error: (error) => {
          this.statusBusy.set(false);
          this.statusError.set(apiError(error));
        },
      });
  }
}
