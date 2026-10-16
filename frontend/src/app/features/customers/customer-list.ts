import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Customer } from '../../models/customer.model';
import { apiError } from '../../services/api-error';
import { CustomerService } from '../../services/customer.service';
import { debounceTime, distinctUntilChanged } from 'rxjs';
const notBlank: ValidatorFn = (control) => (!control.value?.trim() ? { required: true } : null);
const contactRequired = (group: AbstractControl): ValidationErrors | null =>
  group.get('phone')?.value?.trim() || group.get('email')?.value?.trim()
    ? null
    : { contactRequired: true };
@Component({
  selector: 'app-customer-list',
  imports: [ReactiveFormsModule],
  templateUrl: './customer-list.html',
  styleUrl: './customer-list.scss',
})
export class CustomerList implements OnInit {
  private readonly service = inject(CustomerService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pendingPage = signal<number | null>(null);
  readonly customers = signal<Customer[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly submissionError = signal('');
  readonly notice = signal('');
  readonly query = signal('');
  readonly appliedQuery = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly selected = signal<Customer | null>(null);
  readonly searchControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(150)],
  });
  readonly editor = viewChild.required<ElementRef<HTMLDialogElement>>('editor');
  readonly form = new FormGroup(
    {
      name: new FormControl('', {
        nonNullable: true,
        validators: [notBlank, Validators.maxLength(150)],
      }),
      phone: new FormControl('', {
        nonNullable: true,
        validators: [Validators.maxLength(20), Validators.pattern(/^(?=.*\d)[+0-9(). -]{5,20}$/)],
      }),
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.maxLength(150), Validators.email],
      }),
    },
    { validators: contactRequired },
  );
  ngOnInit() {
    this.searchControl.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.query.set(value);
        this.search();
      });
    this.load();
  }
  load(page = this.page()) {
    if (this.loading()) {
      this.pendingPage.set(page);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const query = this.appliedQuery();
    this.service
      .search(query, page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          if (query === this.appliedQuery()) {
            this.customers.set(r.customers);
            this.page.set(r.page);
            this.totalPages.set(r.totalPages);
            this.totalElements.set(r.totalElements);
          }
          this.finishLoad();
        },
        error: (e) => {
          if (query === this.appliedQuery()) this.error.set(apiError(e));
          this.finishLoad();
        },
      });
  }
  private finishLoad() {
    this.loading.set(false);
    const pending = this.pendingPage();
    if (pending !== null) {
      this.pendingPage.set(null);
      this.load(pending);
    }
  }
  search() {
    const value = this.searchControl.value.trim();
    this.query.set(value);
    if (value === this.appliedQuery() && !this.error()) return;
    this.appliedQuery.set(value);
    this.page.set(0);
    this.load(0);
  }
  clearSearch() {
    this.searchControl.setValue('', { emitEvent: false });
    this.query.set('');
    this.appliedQuery.set('');
    this.page.set(0);
    this.load(0);
  }
  openEditor(customer: Customer | null = null) {
    this.selected.set(customer);
    this.submissionError.set('');
    this.notice.set('');
    this.form.reset({
      name: customer?.name ?? '',
      phone: customer?.phone ?? '',
      email: customer?.email ?? '',
    });
    this.editor().nativeElement.showModal();
  }
  closeEditor() {
    if (!this.saving()) this.editor().nativeElement.close();
  }
  validationMessage(field: 'name' | 'phone' | 'email') {
    const c = this.form.controls[field];
    if (!c.touched || !c.errors) return '';
    if (c.hasError('required')) return 'Customer name is required.';
    if (c.hasError('maxlength')) {
      const label = { phone: 'Phone', email: 'Email', name: 'Name' }[field];
      return `${label} is too long.`;
    }
    return field === 'phone'
      ? 'Use 5 to 20 international phone characters and include a digit.'
      : 'Provide a valid email address.';
  }
  contactMessage() {
    return this.form.hasError('contactRequired') &&
      (this.form.controls.phone.touched || this.form.controls.email.touched)
      ? 'Provide at least a phone number or email address.'
      : '';
  }
  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.submissionError.set('');
    const raw = this.form.getRawValue();
    const request = {
      name: raw.name.trim(),
      phone: raw.phone.trim() || null,
      email: raw.email.trim() || null,
    };
    const current = this.selected();
    const operation = current
      ? this.service.update(current.customerId, request)
      : this.service.create(request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.closeEditor();
        this.notice.set(
          `${saved.name} ${current ? 'updated' : 'registered'} successfully (customer #${saved.customerId}).`,
        );
        this.load(this.page());
      },
      error: (e) => {
        this.saving.set(false);
        this.submissionError.set(apiError(e));
      },
    });
  }
}
