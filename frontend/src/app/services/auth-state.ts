import { Injectable, signal } from '@angular/core';
import { AuthenticatedUser } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthState {
  readonly user = signal<AuthenticatedUser | null>(null);
  readonly ready = signal(false);
  readonly sessionMessage = signal('');
  setUser(user: AuthenticatedUser | null) {
    this.user.set(user);
  }
  expire() {
    this.user.set(null);
    this.sessionMessage.set('Your session has expired. Sign in again.');
  }
}
