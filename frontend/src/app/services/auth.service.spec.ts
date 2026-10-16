import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
        ),
        provideHttpClientTesting(),
      ],
    }),
  );
  it('restores an existing server session without browser storage', async () => {
    const service = TestBed.inject(AuthService),
      http = TestBed.inject(HttpTestingController);
    const restored = service.restore();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'token' });
    http
      .expectOne('/api/auth/me')
      .flush({ userId: 1, username: 'admin01', fullName: 'Admin', role: 'ADMIN' });
    await restored;
    expect(service.state.user()?.username).toBe('admin01');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
  it('treats a 401 during restoration as a signed-out session', async () => {
    const service = TestBed.inject(AuthService),
      http = TestBed.inject(HttpTestingController);
    const restored = service.restore();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'token' });
    http
      .expectOne('/api/auth/me')
      .flush(
        { message: 'Authentication is required.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await restored;
    expect(service.state.user()).toBeNull();
    expect(service.state.ready()).toBe(true);
  });
  it('logs out through the protected server endpoint and clears the profile', () => {
    const service = TestBed.inject(AuthService),
      http = TestBed.inject(HttpTestingController);
    service.state.setUser({ userId: 2, username: 'agent01', fullName: 'Agent', role: 'AGENT' });
    service.logout().subscribe();
    const request = http.expectOne('/api/auth/logout');
    expect(request.request.method).toBe('POST');
    request.flush(null);
    expect(service.state.user()).toBeNull();
  });
  it('uses the Angular XSRF cookie/header integration for login', () => {
    document.cookie = 'XSRF-TOKEN=client-token; path=/';
    const service = TestBed.inject(AuthService),
      http = TestBed.inject(HttpTestingController);
    service.login({ username: 'admin01', password: 'not-stored' }).subscribe();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'client-token' });
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('client-token');
    request.flush({ userId: 1, username: 'admin01', fullName: 'Admin', role: 'ADMIN' });
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });
});
