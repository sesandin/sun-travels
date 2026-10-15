import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { catchError, firstValueFrom, map, of, switchMap, tap } from 'rxjs';
import { AuthenticatedUser, LoginRequest } from '../models/auth.model';
import { AuthState } from './auth-state';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly state = inject(AuthState);
  private restorePromise?: Promise<void>;

  restore(): Promise<void> {
    if (this.restorePromise) return this.restorePromise;
    this.restorePromise = firstValueFrom(
      this.csrf().pipe(
        switchMap(() => this.http.get<AuthenticatedUser>('/api/auth/me')),
        tap((user) => this.state.setUser(user)),
        catchError((error: HttpErrorResponse) => {
          this.state.setUser(null);
          if (error.status !== 401) this.state.sessionMessage.set('Unable to restore your session.');
          return of(null);
        }),
        tap(() => this.state.ready.set(true)),
        map(() => void 0),
      ),
    );
    return this.restorePromise;
  }

  login(request: LoginRequest) {
    return this.csrf().pipe(
      switchMap(() => this.http.post<AuthenticatedUser>('/api/auth/login', request)),
      tap((user) => { this.state.setUser(user); this.state.sessionMessage.set(''); }),
    );
  }
  logout() {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      tap(() => { this.state.setUser(null); this.state.sessionMessage.set(''); }),
    );
  }
  private csrf() { return this.http.get<{ headerName: string; token: string }>('/api/auth/csrf'); }
}
