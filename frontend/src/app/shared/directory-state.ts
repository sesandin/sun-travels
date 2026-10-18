import { DestroyRef, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { apiError } from '../services/api-error';

export interface DirectoryPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  summary: Record<string, unknown>;
}

/** Server pages own filtering, ordering and totals; cancellation prevents stale rows. */
export class DirectoryState<T> {
  readonly items = signal<T[]>([]);
  readonly page = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly summary = signal<Record<string, unknown>>({});
  readonly loading = signal(false);
  readonly error = signal('');
  private request?: Subscription;
  private key = '';

  // Initializes the directory-state and its required dependencies.
  constructor(
    private readonly fetch: (page: number) => Observable<DirectoryPage<T>>,
    destroy: DestroyRef,
    private readonly criteria: () => unknown = () => null,
  ) {
    destroy.onDestroy(() => this.request?.unsubscribe());
  }

  // Cancels stale reads and loads one page using the current filter criteria.
  load(page = this.page(), force = false) {
    const key = JSON.stringify([page, this.criteria()]);
    if (!force && this.loading() && this.key === key) return;
    this.key = key;
    this.request?.unsubscribe();
    this.page.set(Math.max(0, page));
    this.loading.set(true);
    this.error.set('');

    this.request = this.fetch(Math.max(0, page)).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.page.set(result.page);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.summary.set(result.summary);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(apiError(error));
        this.loading.set(false);
      },
    });
  }
}
