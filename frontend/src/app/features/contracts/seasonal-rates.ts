import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Contract } from '../../models/contract.model';
import { Offering, SeasonalRate } from '../../models/offering.model';
import { OfferingService } from '../../services/offering.service';
import { apiError } from '../../services/api-error';
import { money, period } from './offering-validators';
@Component({
  selector: 'app-seasonal-rates',
  imports: [ReactiveFormsModule],
  templateUrl: './seasonal-rates.html',
  styleUrl: './offering-panel.scss',
})
export class SeasonalRates implements OnInit {
  private readonly injector = inject(Injector);
  private readonly editor = viewChild<ElementRef<HTMLElement>>('editor');
  readonly removalDialog = viewChild.required<ElementRef<HTMLDialogElement>>('removalDialog');
  readonly removing = signal(false);
  readonly removal = signal<SeasonalRate | null>(null);
  readonly removalError = signal('');
  readonly contract = input.required<Contract>();
  readonly offering = input.required<Offering>();
  private readonly service = inject(OfferingService);
  private readonly destroyRef = inject(DestroyRef);
  readonly rows = signal<SeasonalRate[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly editing = signal(false);
  readonly selected = signal<SeasonalRate | null>(null);
  readonly saving = signal(false);
  readonly failure = signal('');
  readonly notice = signal('');
  readonly form = new FormGroup(
    {
      validFrom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      validTo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      ratePerPersonPerNight: new FormControl<number | null>(null, [Validators.required, money]),
    },
    { validators: period },
  );
  ngOnInit() {
    this.load();
  }
  canMaintain() {
    return this.contract().status === 'DRAFT' || this.contract().status === 'APPROVED';
  }
  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.service
      .seasons(this.offering().offeringId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(apiError(e));
          this.loading.set(false);
        },
      });
  }
  open(row: SeasonalRate | null = null) {
    if (!this.canMaintain() || this.saving() || this.removing() || this.removal()) return;
    this.selected.set(row);
    this.editing.set(true);
    this.failure.set('');
    this.notice.set('');
    this.form.reset({
      validFrom: row?.validFrom ?? '',
      validTo: row?.validTo ?? '',
      ratePerPersonPerNight: row?.ratePerPersonPerNight ?? null,
    });
    afterNextRender(() => this.editor()?.nativeElement.focus(), { injector: this.injector });
  }
  cancel() {
    if (!this.saving()) this.editing.set(false);
  }
  openRemoval(row: SeasonalRate) {
    if (!this.canMaintain() || this.saving() || this.removing() || this.loading()) return;
    this.removal.set(row);
    this.removalError.set('');
    this.notice.set('');
    this.removalDialog().nativeElement.showModal();
  }
  closeRemoval() {
    if (this.removing()) return;
    this.removalDialog().nativeElement.close();
    this.removal.set(null);
  }
  remove() {
    const row = this.removal();
    if (!row || !this.canMaintain() || this.saving() || this.removing()) return;
    this.removing.set(true);
    this.removalError.set('');
    this.service
      .removeSeason(this.offering().offeringId, row.seasonalRateId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.removing.set(false);
          this.closeRemoval();
          this.editing.set(false);
          this.rows.update((rows) =>
            rows.filter((rate) => rate.seasonalRateId !== row.seasonalRateId),
          );
          this.notice.set(
            'Seasonal rate removed. The base rate now applies to these dates. Confirmed reservation prices remain unchanged.',
          );
          this.load();
        },
        error: (e) => {
          this.removing.set(false);
          this.removalError.set(apiError(e));
        },
      });
  }
  save() {
    if (
      !this.canMaintain() ||
      this.saving() ||
      this.removing() ||
      this.removal() ||
      !this.editing()
    )
      return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const raw = this.form.getRawValue(),
      c = this.contract();
    if (raw.validFrom < c.validFrom || raw.validTo > c.validTo) {
      this.failure.set('Seasonal dates must remain within the contract validity period.');
      return;
    }
    const payload = { ...raw, ratePerPersonPerNight: raw.ratePerPersonPerNight! },
      row = this.selected();
    const req = row
      ? this.service.updateSeason(this.offering().offeringId, row.seasonalRateId, payload)
      : this.service.createSeason(this.offering().offeringId, payload);
    this.saving.set(true);
    this.failure.set('');
    req.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.rows.update((rows) =>
          (row
            ? rows.map((r) => (r.seasonalRateId === saved.seasonalRateId ? saved : r))
            : [...rows, saved]
          ).sort((a, b) => a.validFrom.localeCompare(b.validFrom)),
        );
        this.saving.set(false);
        this.editing.set(false);
        this.notice.set('Seasonal rate saved. Confirmed reservation prices remain unchanged.');
      },
      error: (e) => {
        this.saving.set(false);
        this.failure.set(apiError(e));
      },
    });
  }
}
