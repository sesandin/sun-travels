import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OfferingDirectoryEntry } from '../../models/offering.model';
import { OfferingService } from '../../services/offering.service';
import { apiError } from '../../services/api-error';
type SortField = 'hotel' | 'contract' | 'roomType' | 'rate' | 'rooms' | 'validFrom';
@Component({
  selector: 'app-offering-directory',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './offering-directory.html',
  styleUrl: './offering-directory.scss',
})
export class OfferingDirectory implements OnInit {
  private readonly service = inject(OfferingService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });
  readonly rows = signal<OfferingDirectoryEntry[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly page = signal(1);
  readonly pageSize = 20;
  readonly statuses = ['DRAFT', 'APPROVED', 'SUSPENDED', 'TERMINATED'];
  readonly form = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    hotelId: new FormControl('', { nonNullable: true }),
    contractStatus: new FormControl('', { nonNullable: true }),
    roomTypeId: new FormControl('', { nonNullable: true }),
    offeringStatus: new FormControl('', { nonNullable: true }),
    sortBy: new FormControl<SortField>('hotel', { nonNullable: true }),
    direction: new FormControl<'asc' | 'desc'>('asc', { nonNullable: true }),
  });
  readonly criteria = signal(this.form.getRawValue());
  readonly hotels = computed(() =>
    Array.from(
      new Map(this.rows().map((r) => [r.hotelId, { id: r.hotelId, name: r.hotelName }])).values(),
    ).sort((a, b) => this.collator.compare(a.name, b.name)),
  );
  readonly roomTypes = computed(() =>
    Array.from(
      new Map(
        this.rows().map((r) => [r.roomTypeId, { id: r.roomTypeId, name: r.roomTypeName }]),
      ).values(),
    ).sort((a, b) => this.collator.compare(a.name, b.name)),
  );
  readonly filtered = computed(() => {
    const f = this.criteria(),
      q = f.search.trim().toLowerCase();
    return this.rows()
      .filter(
        (r) =>
          (!q ||
            [r.hotelName, r.contractCode, r.roomTypeName].some((v) =>
              v.toLowerCase().includes(q),
            )) &&
          (!f.hotelId || String(r.hotelId) === f.hotelId) &&
          (!f.roomTypeId || String(r.roomTypeId) === f.roomTypeId) &&
          (!f.contractStatus || r.contractStatus === f.contractStatus) &&
          (!f.offeringStatus || r.status === f.offeringStatus),
      )
      .sort(
        (a, b) =>
          (f.direction === 'asc' ? 1 : -1) * this.compare(a, b, f.sortBy) ||
          a.offeringId - b.offeringId,
      );
  });
  readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.filtered().length / this.pageSize)),
  );
  readonly pageRows = computed(() =>
    this.filtered().slice((this.page() - 1) * this.pageSize, this.page() * this.pageSize),
  );
  readonly first = computed(() =>
    this.filtered().length ? (this.page() - 1) * this.pageSize + 1 : 0,
  );
  readonly last = computed(() => Math.min(this.page() * this.pageSize, this.filtered().length));
  ngOnInit() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.criteria.set(this.form.getRawValue());
      this.page.set(1);
    });
    this.load();
  }
  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.service
      .directory()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.page.set(1);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.loading.set(false);
        },
      });
  }
  reset() {
    this.form.reset({
      search: '',
      hotelId: '',
      roomTypeId: '',
      contractStatus: '',
      offeringStatus: '',
      sortBy: 'hotel',
      direction: 'asc',
    });
  }
  changePage(delta: number) {
    this.page.update((p) => Math.max(1, Math.min(this.pageCount(), p + delta)));
  }
  private compare(a: OfferingDirectoryEntry, b: OfferingDirectoryEntry, field: SortField) {
    switch (field) {
      case 'contract':
        return this.collator.compare(a.contractCode, b.contractCode);
      case 'roomType':
        return this.collator.compare(a.roomTypeName, b.roomTypeName);
      case 'rate':
        return (
          this.collator.compare(a.currencyCode, b.currencyCode) ||
          a.ratePerPersonPerNight - b.ratePerPersonPerNight
        );
      case 'rooms':
        return a.contractedRoomCount - b.contractedRoomCount;
      case 'validFrom':
        return a.validFrom.localeCompare(b.validFrom);
      default:
        return this.collator.compare(a.hotelName, b.hotelName);
    }
  }
}
