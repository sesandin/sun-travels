import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuotationSearch } from './features/search/quotation-search';
import { OfferingDirectory } from './features/room-offerings/offering-directory';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { AuthState } from './services/auth-state';
const admin = { userId: 1, username: 'admin01', fullName: 'Administrator', role: 'ADMIN' as const };
describe('App', () => {
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
  it('activates Room Offerings navigation and closes the mobile menu', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'room-offerings', component: OfferingDirectory }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthState).setUser(admin);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/room-offerings');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/offerings').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector(
      'a[href="/room-offerings"]',
    );
    expect(link.classList.contains('selected')).toBe(true);
    expect(link.getAttribute('aria-current')).toBe('page');
    fixture.componentInstance.menuOpen.set(true);
    fixture.detectChanges();
    link.click();
    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No room offerings yet');
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
    const offeringsLink = fixture.nativeElement.querySelector('a[href="/room-offerings"]');
    expect(offeringsLink?.textContent).toContain('Room Offerings');
    expect(offeringsLink?.textContent).not.toContain('Soon');
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
    expect(fixture.nativeElement.textContent).toContain('AGENT');
  });
});
