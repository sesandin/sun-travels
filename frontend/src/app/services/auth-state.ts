import { Injectable, signal } from '@angular/core';
import { AuthenticatedUser } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthState {
  readonly user = signal<AuthenticatedUser | null>(null);
  readonly ready = signal(false);
  readonly sessionMessage = signal('');
  // Updates the current signed-in staff profile.
  setUser(user: AuthenticatedUser | null) {
    this.user.set(user);
  }
  // Clears the signed-in profile and displays a session-expired message.
  expire() {
    this.user.set(null);
    this.sessionMessage.set('Your session has expired. Sign in again.');
  }
}
