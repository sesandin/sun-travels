import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';
import { Icon } from './shared/icon';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly menuOpen = signal(false);
  readonly loggingOut = signal(false);
  readonly router = inject(Router);
  readonly mobileNavigation = signal(false);
  private readonly authService = inject(AuthService);
  readonly auth = this.authService.state;
  private readonly destroyRef = inject(DestroyRef);
  private readonly navigation = viewChild<ElementRef<HTMLElement>>('navigation');
  private readonly navigationToggle = viewChild<ElementRef<HTMLButtonElement>>('navigationToggle');

  // Creates an empty object for fields to be populated after construction.
  constructor() {
    afterNextRender(() => {
      if (typeof window.matchMedia !== 'function') return;
      const viewport = window.matchMedia('(max-width: 760px)');
      // Updates navigation state when the viewport changes.
      const updateViewport = () => {
        this.mobileNavigation.set(viewport.matches);
        if (!viewport.matches) this.menuOpen.set(false);
      };
      updateViewport();
      viewport.addEventListener('change', updateViewport);
      this.destroyRef.onDestroy(() => viewport.removeEventListener('change', updateViewport));
    });
  }

  // Returns the page title for the current route.
  pageTitle(): string {
    const path = this.router.url.split('?')[0];
    if (path === '/reservations/new') return 'Review and confirm';
    if (path.startsWith('/reservations/')) return 'Reservation details';
    const titles: Record<string, string> = {
      '/hotels': 'Hotels',
      '/room-types': 'Room Types',
      '/contracts': 'Contracts',
      '/search': 'Search & Quotations',
      '/customers': 'Customers',
      '/reservations': 'Reservations',
    };
    return titles[path] ?? 'Workspace';
  }

  // Opens or closes the mobile navigation menu.
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

  // Closes mobile navigation and restores focus when requested.
  closeNavigation(restoreFocus = false) {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    if (restoreFocus) queueMicrotask(() => this.navigationToggle()?.nativeElement.focus());
    else if (this.mobileNavigation())
      queueMicrotask(() => document.getElementById('main-content')?.focus());
  }

  // Signs out staff and returns to the sign-in page.
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
