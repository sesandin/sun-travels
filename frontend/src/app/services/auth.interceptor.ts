import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthState } from './auth-state';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const state = inject(AuthState);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const authRequest = request.url.startsWith('/api/auth/');
      if (error.status === 401 && !authRequest && state.user()) {
        state.expire();
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
