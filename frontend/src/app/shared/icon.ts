import { Component, input } from '@angular/core';

const paths = {
  hotel: 'M4 21V3h16v18M2 21h20M9 21v-4h6v4M8 7h1m6 0h1M8 11h1m6 0h1',
  contract: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Zm0 0v6h6M8 12h8M8 16h8',
  bed: 'M3 18v3m18-3v3M3 18h18v-6H3v6ZM5 12V5h14v7M8 12V9h8v3',
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  customers: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M15 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  reservation: 'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm4 12 2 2 4-4',
  sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0Z',
  menu: 'M3 6h18M3 12h18M3 18h18',
  close: 'M6 6l12 12M6 18 18 6',
  star: 'm12 3 2.78 5.63L21 9.54l-4.5 4.39 1.06 6.2L12 17.2l-5.56 2.93 1.06-6.2L3 9.54l6.22-.91L12 3Z',
} as const;

@Component({
  selector: 'app-icon',
  template: `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
      stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      <path [attr.d]="paths[name()]" />
    </svg>
  `,
  host: { 'aria-hidden': 'true' },
  styles: `
    :host { display: inline-flex; width: 1em; height: 1em; flex-shrink: 0; vertical-align: -0.125em; }
    svg { display: block; width: 100%; height: 100%; }
  `,
})
export class Icon {
  readonly name = input.required<keyof typeof paths>();
  protected readonly paths = paths;
}
