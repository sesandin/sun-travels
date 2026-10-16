import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly authService = inject(AuthService);
  readonly auth = this.authService.state;
  readonly menuOpen = signal(false);
  readonly loggingOut = signal(false);
  readonly router = inject(Router);
  readonly mobileNavigation = signal(false);
  private readonly destroyRef = inject(DestroyRef);
  private readonly navigation = viewChild<ElementRef<HTMLElement>>('navigation');
  private readonly navigationToggle = viewChild<ElementRef<HTMLButtonElement>>('navigationToggle');

  constructor() {
    afterNextRender(() => {
      if (typeof window.matchMedia !== 'function') return;
      const viewport = window.matchMedia('(max-width: 760px)');
      const updateViewport = () => {
        this.mobileNavigation.set(viewport.matches);
        if (!viewport.matches) this.menuOpen.set(false);
      };
      updateViewport();
      viewport.addEventListener('change', updateViewport);
      this.destroyRef.onDestroy(() => viewport.removeEventListener('change', updateViewport));
    });
  }

  pageTitle(): string {
    const path = this.router.url.split('?')[0];
    if (path === '/reservations/new') return 'Review and confirm';
    if (path.startsWith('/reservations/')) return 'Reservation details';
    const titles: Record<string, string> = {
      '/hotels': 'Hotels',
      '/room-types': 'Room Types',
      '/contracts': 'Contracts',
      '/room-offerings': 'Room Offerings',
      '/search': 'Search & Quotations',
      '/customers': 'Customers',
      '/reservations': 'Reservations',
    };
    return titles[path] ?? 'Workspace';
  }

  toggleNavigation() {
    if (this.menuOpen()) {
      this.closeNavigation(true);
      return;
    }
    this.menuOpen.set(true);
    queueMicrotask(() =>
      this.navigation()?.nativeElement.querySelector<HTMLElement>('nav a')?.focus(),
    );
  }

  closeNavigation(restoreFocus = false) {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    if (restoreFocus) queueMicrotask(() => this.navigationToggle()?.nativeElement.focus());
    else if (this.mobileNavigation())
      queueMicrotask(() => document.getElementById('main-content')?.focus());
  }

  logout() {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    this.authService.logout().subscribe({
      next: () => location.assign('/login'),
      error: () => {
        this.auth.expire();
        location.assign('/login');
      },
    });
  }
}
