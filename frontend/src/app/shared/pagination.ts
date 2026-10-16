import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  template: `<nav class="pagination" aria-label="List pages">
    <button
      type="button"
      class="secondary"
      (click)="pageChange.emit(page() - 1)"
      [disabled]="loading() || page() <= 0"
    >
      Previous
    </button>
    <span aria-live="polite"
      >Page {{ page() + 1 }} of {{ totalPages() || 1 }} &middot; {{ totalElements() }} records</span
    >
    <button
      type="button"
      class="secondary"
      (click)="pageChange.emit(page() + 1)"
      [disabled]="loading() || page() + 1 >= totalPages()"
    >
      Next
    </button>
  </nav>`,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly loading = input(false);
  readonly pageChange = output<number>();
}
