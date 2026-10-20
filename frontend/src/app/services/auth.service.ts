import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, firstValueFrom, map, of, switchMap, tap } from 'rxjs';
import { AuthenticatedUser, LoginRequest } from '../models/auth.model';
import { AuthState } from './auth-state';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly state = inject(AuthState);
  private readonly http = inject(HttpClient);
  private restorePromise?: Promise<void>;

  // Restores the current staff session once and marks authentication state ready.
  restore(): Promise<void> {
    if (this.restorePromise) return this.restorePromise;
    this.restorePromise = firstValueFrom(
      this.csrf().pipe(
        switchMap(() => this.http.get<AuthenticatedUser>('/api/auth/me')),
        tap((user) => this.state.setUser(user)),
        catchError((error: HttpErrorResponse) => {
          this.state.setUser(null);
          if (error.status !== 401)
            this.state.sessionMessage.set('Unable to restore your session.');
          return of(null);
        }),
        tap(() => this.state.ready.set(true)),
        map(() => void 0),
      ),
    );
    return this.restorePromise;
  }

  // Signs in staff with CSRF protection and updates the authenticated profile.
  login(request: LoginRequest) {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthenticatedUser>('/api/auth/login', request)),
      tap((user) => {
        this.state.setUser(user);
        this.state.sessionMessage.set('');
      }),
    );
  }
  // Signs out the current staff session and clears local authentication state.
  logout() {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      tap(() => {
        this.state.setUser(null);
        this.state.sessionMessage.set('');
      }),
    );
  }
  // Requests a fresh CSRF token for protected API writes.
  private csrf() {
    return this.http.get<{ headerName: string; token: string }>('/api/auth/csrf');
  }
}
