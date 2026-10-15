import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { adminGuard, authenticatedGuard } from './auth.guards';
import { AuthState } from './services/auth-state';

@Component({ template: '' }) class Page {}
describe('auth route guards', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([
    { path: 'login', component: Page }, { path: 'search', component: Page, canActivate: [authenticatedGuard] },
    { path: 'contracts', component: Page, canActivate: [adminGuard] },
  ])] }));
  it('redirects an unauthenticated visitor to login with the requested URL', async () => {
    const router = TestBed.inject(Router); await router.navigateByUrl('/search');
    expect(router.url).toBe('/login?returnUrl=%2Fsearch');
  });
  it('prevents an agent from navigating to administration', async () => {
    TestBed.inject(AuthState).setUser({ userId: 2, username: 'agent01', fullName: 'Agent', role: 'AGENT' });
    const router = TestBed.inject(Router); await router.navigateByUrl('/contracts');
    expect(router.url).toBe('/search?forbidden=1');
  });
  it('allows an administrator to navigate to administration', async () => {
    TestBed.inject(AuthState).setUser({ userId: 1, username: 'admin01', fullName: 'Admin', role: 'ADMIN' });
    const router = TestBed.inject(Router); expect(await router.navigateByUrl('/contracts')).toBe(true); expect(router.url).toBe('/contracts');
  });
});
