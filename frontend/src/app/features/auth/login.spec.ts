import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Login } from './login';
import { Component } from '@angular/core';

@Component({ template: '' })
class Page {}

describe('Login', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([{ path: 'search', component: Page }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }),
  );
  it('announces session notices with native output while retaining the styled paragraph', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.componentInstance.sessionMessage.set('Your session has expired. Sign in again.');
    fixture.detectChanges();

    const notice: HTMLElement = fixture.nativeElement.querySelector('.notice');
    expect(notice.tagName).toBe('P');
    expect(notice.querySelector('output')?.textContent).toContain('Your session has expired');
    expect(notice.hasAttribute('role')).toBe(false);
  });
  it('validates fields and prevents duplicate login submissions', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.form.setValue({ username: 'admin01', password: 'chosen-secret' });
    fixture.componentInstance.submit();
    fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'csrf' });
    expect(http.match('/api/auth/login').length).toBe(1);
  });
  it('shows generic authentication failures and preserves the username', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ username: 'agent01', password: 'wrong' });
    fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'csrf' });
    http
      .expectOne('/api/auth/login')
      .flush(
        { message: 'Invalid username or password.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Invalid username or password.');
    expect(fixture.componentInstance.form.controls.username.value).toBe('agent01');
  });
  it('stores the safe profile in memory and navigates an agent to search', async () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ username: 'agent01', password: 'chosen-secret' });
    fixture.componentInstance.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'csrf' });
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ username: 'agent01', password: 'chosen-secret' });
    login.flush({ userId: 2, username: 'agent01', fullName: 'Agent', role: 'AGENT' });
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/search');
  });
});
