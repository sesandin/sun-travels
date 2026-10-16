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
  imports: [HotelForm],
  templateUrl: './hotel-list.html',
  styleUrl: './hotel-list.scss',
})
export class HotelList implements OnInit {
  private readonly service = inject(HotelService);
  private readonly destroyRef = inject(DestroyRef);
  readonly hotels = signal<Hotel[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
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
  readonly activeCount = computed(() => this.hotels().filter((h) => h.status === 'ACTIVE').length);
  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.hotels().filter(
      (h) =>
        (!this.filter() || h.status === this.filter()) &&
        [h.name, h.city, h.country].some((v) => v.toLowerCase().includes(q)),
    );
  });
  ngOnInit(): void {
    this.loadHotels();
  }
  loadHotels(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.service
      .getAllHotels()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (hotels) => {
          this.hotels.set(hotels);
          this.loading.set(false);
        },
        error: (error) => {
          this.error.set(apiError(error));
          this.loading.set(false);
        },
      });
  }
  openEditor(hotel: Hotel | null = null): void {
    this.selected.set(hotel);
    this.submissionError.set('');
    this.notice.set('');
    this.form().reset(hotel);
    this.editor().nativeElement.showModal();
  }
  closeEditor(): void {
    if (!this.saving()) this.editor().nativeElement.close();
  }
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
        else this.loadHotels();
        this.notice.set(`Hotel ${hotel ? 'updated' : 'registered'} successfully.`);
      },
      error: (error) => {
        this.saving.set(false);
        this.submissionError.set(apiError(error));
      },
    });
  }
  requestStatus(hotel: Hotel): void {
    if (this.statusBusy()) return;
    this.statusTarget.set(hotel);
    this.statusError.set('');
    this.notice.set('');
    this.confirmation().nativeElement.showModal();
  }
  closeStatus(): void {
    if (!this.statusBusy()) this.confirmation().nativeElement.close();
  }
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
