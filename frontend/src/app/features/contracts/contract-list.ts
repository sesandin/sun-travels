import { DirectoryState } from '../../shared/directory-state';
import { Pagination } from '../../shared/pagination';
import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { Subscription, Subject, switchMap, timer, of, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Contract, ContractStatus } from '../../models/contract.model';
import { ContractService } from '../../services/contract.service';
import { HotelService, ManagementHotelOption } from '../../services/hotel.service';
import { OfferingPanel } from './offering-panel';
import { isContractExpired } from './contract-expiration';
import { positiveInteger } from './offering-validators';
import { apiError } from '../../services/api-error';
// Validates the allowed decimal precision for contract markup.
const decimal: ValidatorFn = (c) =>
  /^\d{1,3}(\.\d{1,2})?$/.test(String(c.value)) ? null : { decimal: true };
// Checks that the contract end date is not before its start date.
const dateRange: ValidatorFn = (group) => {
  const from = group.get('validFrom')?.value,
    to = group.get('validTo')?.value;
  return from && to && from > to ? { dateRange: true } : null;
};
@Component({
  selector: 'app-contract-list',
  imports: [Pagination, ReactiveFormsModule, OfferingPanel],
  templateUrl: './contract-list.html',
  styleUrl: './contract-list.scss',
  host: { '(document:click)': 'dismissHotelLookup($event)' },
})
export class ContractList implements OnInit {
  readonly hotelLookupOpen = signal(false);
  private readonly hotelPicker = viewChild<ElementRef<HTMLElement>>('hotelPicker');

  dismissHotelLookup(event: MouseEvent): void {
    if (event.target instanceof Node && !this.hotelPicker()?.nativeElement.contains(event.target)) {
      this.hotelLookupOpen.set(false);
    }
  }

  leaveHotelLookup(event: FocusEvent): void {
    if (
      !(event.relatedTarget instanceof Node) ||
      !(event.currentTarget as HTMLElement).contains(event.relatedTarget)
    ) {
      this.hotelLookupOpen.set(false);
    }
  }
  readonly linkedOffering = signal<number | null>(null);
  readonly linkError = signal('');
  readonly hotels = signal<ManagementHotelOption[]>([]);
  readonly hotelQuery = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(150)],
  });
  readonly selectedHotel = signal<ManagementHotelOption | null>(null);
  readonly hotelLookupDone = signal(false);
  private readonly hotelLookup = new Subject<void>();
  private hotelResolution?: Subscription;
  readonly hotelsLoading = signal(false);
  readonly saving = signal(false);
  readonly hotelError = signal('');
  readonly submissionError = signal('');
  readonly notice = signal('');
  readonly query = signal('');
  readonly statusFilter = signal('');
  readonly statuses: ContractStatus[] = ['DRAFT', 'APPROVED', 'SUSPENDED', 'TERMINATED'];
  readonly selected = signal<Contract | null>(null);
  readonly mode = signal<
    | 'create'
    | 'edit'
    | 'markup'
    | 'details'
    | 'approve'
    | 'reapprove'
    | 'suspend'
    | 'terminate'
    | null
  >(null);
  readonly detailsLoading = signal(false);
  readonly form = new FormGroup(
    {
      hotelId: new FormControl<number | null>(null, [Validators.required, positiveInteger]),
      validFrom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      validTo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      markupPercentage: new FormControl<number | null>(0, [Validators.required, decimal]),
      currencyCode: new FormControl('', {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.maxLength(3),
          Validators.pattern(/^[A-Z]{3}$/),
        ],
      }),
    },
    { validators: dateRange },
  );
  readonly markupForm = new FormGroup({
    markupPercentage: new FormControl<number | null>(0, [Validators.required, decimal]),
  });
  private readonly route = inject(ActivatedRoute, { optional: true });
  private detailRequest?: Subscription;
  private readonly service = inject(ContractService);
  private readonly hotelService = inject(HotelService);
  private readonly destroyRef = inject(DestroyRef);
  readonly directory = new DirectoryState<Contract>(
    (page) => this.service.page(this.query(), this.statusFilter(), page),
    this.destroyRef,
    () => [this.query(), this.statusFilter()],
  );
  readonly contracts = this.directory.items;
  readonly loading = this.directory.loading;
  readonly error = this.directory.error;
  readonly filtered = this.contracts;
  private readonly injector = inject(Injector);
  private readonly editor = viewChild<ElementRef<HTMLElement>>('editor');

  isExpired(validTo: string): boolean {
    return isContractExpired(validTo);
  }

  // Loads the initial data and subscriptions needed by this component.
  ngOnInit() {
    this.loadContracts();
    this.hotelLookup
      .pipe(
        switchMap(() => {
          this.hotels.set([]);
          this.hotelError.set('');
          this.hotelLookupDone.set(false);
          this.hotelsLoading.set(false);
          const term = this.hotelQuery.value.trim();
          if (this.hotelQuery.invalid || term.length < 2) return of([]);
          return timer(300).pipe(
            switchMap(() => {
              this.hotelsLoading.set(true);
              return this.hotelService.options(term).pipe(
                catchError((e) => {
                  this.hotelError.set(apiError(e));
                  return of([]);
                }),
              );
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((rows) => {
        this.hotels.set(rows);
        this.hotelsLoading.set(false);
        this.hotelLookupDone.set(this.hotelQuery.value.trim().length >= 2 && this.hotelQuery.valid);
      });
    this.hotelQuery.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.hotelResolution?.unsubscribe();
      this.selectedHotel.set(null);
      this.form.controls.hotelId.setValue(null);
      this.loadHotels();
    });
    this.route?.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.openLink(params));
  }
  // Loads the contracts.
  loadContracts(page = this.directory.page()) {
    this.directory.load(page);
  }
  // Loads the hotels.
  loadHotels() {
    this.hotelLookup.next();
  }
  retryHotels() {
    const row = this.selected();
    if (row?.status === 'DRAFT' && !this.hotelQuery.value.trim() && !this.selectedHotel())
      this.resolveHotel(row.hotelId);
    else this.loadHotels();
  }
  selectHotel(hotel: ManagementHotelOption) {
    this.hotelLookupOpen.set(false);
    this.hotelResolution?.unsubscribe();
    this.hotelQuery.setValue('', { emitEvent: false });
    this.loadHotels();
    this.hotelQuery.setValue(hotel.name, { emitEvent: false });
    this.selectedHotel.set(hotel);
    this.form.controls.hotelId.setValue(hotel.hotelId);
  }
  clearHotel() {
    this.hotelQuery.setValue('');
  }
  private resolveHotel(id: number) {
    this.hotelError.set('');
    this.hotelsLoading.set(true);
    this.hotelResolution = this.hotelService
      .option(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (hotel) => {
          this.selectHotel(hotel);
          this.hotelsLoading.set(false);
        },
        error: (e) => {
          this.hotelsLoading.set(false);
          this.hotelError.set(apiError(e));
        },
      });
  }
  // Opens contract creation, draft editing, or approved markup maintenance.
  openEditor(row: Contract | null = null) {
    if (this.saving() || this.detailsLoading()) return;
    if (row && row.status !== 'DRAFT' && row.status !== 'APPROVED') return;
    this.selected.set(row);
    if (!row) this.mode.set('create');
    else this.mode.set(row.status === 'DRAFT' ? 'edit' : 'markup');
    this.submissionError.set('');
    this.notice.set('');
    this.form.reset({
      hotelId: row?.hotelId ?? null,
      validFrom: row?.validFrom ?? '',
      validTo: row?.validTo ?? '',
      markupPercentage: row?.markupPercentage ?? 0,
      currencyCode: row?.currencyCode ?? '',
    });
    this.clearHotel();
    if (row && row.status === 'DRAFT') this.resolveHotel(row.hotelId);
    this.markupForm.reset({ markupPercentage: row?.markupPercentage ?? 0 });
    this.focusEditor();
  }
  // Loads and displays the selected contract and its offerings.
  showDetails(row: Contract) {
    if (this.saving() || this.detailsLoading()) return;
    this.linkedOffering.set(null);
    this.linkError.set('');
    this.fetchDetails(row.contractId, row);
  }

  // Closes the editor and clears its selection when no save is pending.
  closeEditor() {
    if (!this.saving() && !this.detailsLoading()) {
      this.clearHotel();
      this.mode.set(null);
    }
  }

  // Opens confirmation for the selected contract lifecycle action.
  confirmAction(row: Contract, action: 'approve' | 'reapprove' | 'suspend' | 'terminate') {
    if (this.saving() || this.detailsLoading()) return;
    if (
      (action === 'approve' && row.status !== 'DRAFT') ||
      (action === 'reapprove' && row.status !== 'SUSPENDED')
    )
      return;
    if ((action === 'suspend' && row.status !== 'APPROVED') || row.status === 'TERMINATED') return;
    this.selected.set(row);
    this.mode.set(action);
    this.submissionError.set('');
    this.notice.set('');
    this.focusEditor();
  }

  // Submits validated contract changes or the selected lifecycle action.
  save() {
    if (this.saving() || this.detailsLoading()) return;
    const mode = this.mode(),
      row = this.selected();
    let request;
    if (mode === 'markup' && row?.status === 'APPROVED') {
      this.markupForm.markAllAsTouched();
      if (this.markupForm.invalid) return;
      request = this.service.updateMarkup(
        row.contractId,
        this.markupForm.getRawValue().markupPercentage!,
      );
    } else if (mode === 'create' || (mode === 'edit' && row?.status === 'DRAFT')) {
      this.form.markAllAsTouched();
      if (this.form.invalid || this.hotelsLoading() || this.hotelError()) return;
      const raw = this.form.getRawValue();
      const payload = { ...raw, hotelId: raw.hotelId!, markupPercentage: raw.markupPercentage! };
      request =
        mode === 'create'
          ? this.service.createContract(payload)
          : this.service.updateContract(row!.contractId, payload);
    } else {
      request = this.lifecycleRequest(mode, row);
      if (!request) return;
    }
    this.saving.set(true);
    this.submissionError.set('');
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.contracts.update((rows) =>
          row ? rows.map((c) => (c.contractId === saved.contractId ? saved : c)) : [saved, ...rows],
        );
        this.directory.load(this.directory.page(), true);
        this.saving.set(false);
        this.mode.set(null);
        this.selected.set(saved);
        this.notice.set('Contract ' + saved.contractCode + ' saved successfully.');
      },
      error: (e) => {
        this.saving.set(false);
        this.submissionError.set(apiError(e));
      },
    });
  }

  // Reads linked contract and offering references from route parameters.
  private openLink(params: ParamMap) {
    if (!params.has('contractId') && !params.has('offeringId')) return;
    // Parses a valid positive record ID from a route value.
    const parseId = (value: string | null) =>
      value && /^[1-9]\d*$/.test(value) && Number(value) <= 2147483647 ? Number(value) : null;
    const contractId = parseId(params.get('contractId')),
      offeringId = parseId(params.get('offeringId'));
    if (!contractId || (params.has('offeringId') && !offeringId)) {
      this.linkError.set(
        'Invalid contract or offering link. Select a contract from Contracts to view its room offerings.',
      );
      return;
    }
    if (this.saving()) {
      this.linkError.set('Finish the current save before opening another contract.');
      return;
    }
    this.linkError.set('');
    this.linkedOffering.set(offeringId);
    this.fetchDetails(contractId);
  }

  // Loads the selected contract details while preventing stale responses.
  private fetchDetails(id: number, row: Contract | null = null) {
    this.detailRequest?.unsubscribe();
    this.selected.set(row);
    this.mode.set('details');
    this.submissionError.set('');
    this.detailsLoading.set(true);
    this.focusEditor();
    this.detailRequest = this.service
      .getContractById(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.selected.set(detail);
          this.detailsLoading.set(false);
        },
        error: (e) => {
          this.submissionError.set(apiError(e));
          this.detailsLoading.set(false);
        },
      });
  }

  // Moves keyboard focus to the editor after it is rendered.
  private focusEditor() {
    afterNextRender(() => this.editor()?.nativeElement.focus(), { injector: this.injector });
  }

  // Selects the API request for the requested contract lifecycle action.
  private lifecycleRequest(mode: ReturnType<ContractList['mode']>, row: Contract | null) {
    if (mode === 'approve' && row?.status === 'DRAFT')
      return this.service.approveContract(row.contractId);
    if (mode === 'reapprove' && row?.status === 'SUSPENDED')
      return this.service.reapproveContract(row.contractId);
    if (mode === 'suspend' && row?.status === 'APPROVED')
      return this.service.suspendContract(row.contractId);
    if (mode === 'terminate' && row && row.status !== 'TERMINATED')
      return this.service.terminateContract(row.contractId);
    return undefined;
  }
}
