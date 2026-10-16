import { Component, inject, signal } from '@angular/core';
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
