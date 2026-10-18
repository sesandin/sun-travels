import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthState } from './services/auth-state';

// Allows signed-in staff to enter protected routes.
export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthState),
    router = inject(Router);
  return auth.user()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
// Restricts administration routes to signed-in administrators.
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthState),
    router = inject(Router);
  if (!auth.user()) return router.createUrlTree(['/login']);
  return auth.user()?.role === 'ADMIN'
    ? true
    : router.createUrlTree(['/search'], { queryParams: { forbidden: 1 } });
};
// Redirects signed-in staff away from the sign-in page.
export const loginGuard: CanActivateFn = () => {
  const auth = inject(AuthState),
    router = inject(Router);
  return auth.user()
    ? router.createUrlTree([auth.user()?.role === 'ADMIN' ? '/hotels' : '/search'])
    : true;
};
