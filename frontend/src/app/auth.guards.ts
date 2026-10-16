import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthState } from './services/auth-state';

export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthState),
    router = inject(Router);
  return auth.user()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthState),
    router = inject(Router);
  if (!auth.user()) return router.createUrlTree(['/login']);
  return auth.user()?.role === 'ADMIN'
    ? true
    : router.createUrlTree(['/search'], { queryParams: { forbidden: 1 } });
};
export const loginGuard: CanActivateFn = () => {
  const auth = inject(AuthState),
    router = inject(Router);
  return auth.user()
    ? router.createUrlTree([auth.user()?.role === 'ADMIN' ? '/hotels' : '/search'])
    : true;
};
