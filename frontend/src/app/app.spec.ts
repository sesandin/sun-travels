import { directoryPage } from './testing/directory-page';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuotationSearch } from './features/search/quotation-search';
import { ContractList } from './features/contracts/contract-list';
import { routes } from './app.routes';
import { adminGuard } from './auth.guards';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { AuthState } from './services/auth-state';

const admin = { userId: 1, username: 'admin01', fullName: 'Administrator', role: 'ADMIN' as const };
describe('App', () => {
  it('removes the standalone offering route and keeps Contracts admin-only', () => {
    expect(routes.some((route) => route.path === 'room-offerings')).toBe(false);
    const contracts = routes.find((route) => route.path === 'contracts');
    expect(contracts?.component).toBe(ContractList);
    expect(contracts?.canActivate).toEqual([adminGuard]);
  });
  it('activates Search and Quotations navigation without a Soon duplicate', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'search', component: QuotationSearch }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthState).setUser(admin);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/search');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/search/limits').flush({
      businessDate: '2026-10-15',
      maxRooms: 8,
      maxNights: 31,
      maxAdvanceDays: 730,
      maxResults: 20,
      allocationPolicy: 'INDIVIDUAL_ROOM_TYPES',
    });
    http.expectOne('/api/search/filters').flush({ hotels: [], roomTypes: [] });
    await fixture.whenStable();
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/search"]');
    expect(link.classList.contains('selected')).toBe(true);
    expect(link.getAttribute('aria-current')).toBe('page');
    expect(link.textContent).not.toContain('Soon');
    fixture.componentInstance.menuOpen.set(true);
    link.click();
    expect(fixture.componentInstance.menuOpen()).toBe(false);
    http.verify();
  });
  it('activates Contracts navigation and closes the mobile menu', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthState).setUser(admin);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/contracts');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === '/api/contracts/page').flush(directoryPage([]));
    http.expectOne('/api/hotels').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('nav a[href="/contracts"]');
    expect(link.classList.contains('selected')).toBe(true);
    expect(link.getAttribute('aria-current')).toBe('page');
    fixture.componentInstance.menuOpen.set(true);
    fixture.detectChanges();
    link.click();
    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect(fixture.componentInstance.pageTitle()).toBe('Contracts');
    expect(fixture.nativeElement.querySelector('a[href="/room-offerings"]')).toBeNull();
    http.verify();
  });
  it('renders branding and activates reservation navigation', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthState).setUser(admin);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sun Travels');
    expect(fixture.nativeElement.querySelectorAll('.upcoming').length).toBe(0);
    expect(fixture.nativeElement.querySelector('a[href="/reservations"]')?.textContent).toContain(
      'Reservations',
    );
    expect(fixture.nativeElement.querySelector('a[href="/customers"]')?.textContent).toContain(
      'Customers',
    );
    expect(fixture.nativeElement.querySelector('a[href="/room-types"]')?.textContent).toContain(
      'Room Types',
    );
    expect(fixture.nativeElement.querySelector('a[href="/contracts"]')?.textContent).toContain(
      'Contracts',
    );
    expect(fixture.nativeElement.querySelector('a[href="/room-offerings"]')).toBeNull();
    const administrationLinks = Array.from(
      fixture.nativeElement.querySelectorAll('nav a'),
      (link) => (link as HTMLAnchorElement).getAttribute('href'),
    ).slice(0, 3);
    expect(administrationLinks).toEqual(['/hotels', '/contracts', '/room-types']);
    fixture.componentInstance.menuOpen.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.sidebar').classList.contains('open')).toBe(true);
  });
  it('shows an agent only the shared search navigation', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthState).setUser({
      userId: 2,
      username: 'agent01',
      fullName: 'Agent',
      role: 'AGENT',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a[href="/search"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/customers"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/contracts"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/hotels"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/room-types"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/room-offerings"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('AGENT');
  });
  it('keeps closed mobile navigation inert and returns focus after Escape', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthState).setUser(admin);
    fixture.detectChanges();
    fixture.componentInstance.mobileNavigation.set(true);
    fixture.detectChanges();
    const sidebar: HTMLElement = fixture.nativeElement.querySelector('.sidebar');
    const workspace: HTMLElement = fixture.nativeElement.querySelector('.workspace');
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('.menu-button');
    expect(sidebar.inert).toBe(true);
    toggle.click();
    fixture.detectChanges();
    expect(sidebar.inert).toBe(false);
    expect(workspace.inert).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    sidebar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    await Promise.resolve();
    expect(sidebar.inert).toBe(true);
    expect(workspace.inert).toBe(false);
    expect(document.activeElement).toBe(toggle);
  });
});
