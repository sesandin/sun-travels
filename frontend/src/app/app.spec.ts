import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OfferingDirectory } from './features/room-offerings/offering-directory';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
describe('App', () => {
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
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/room-offerings');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost:8080/api/offerings').flush([]);
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
  it('renders branding and clearly marks upcoming modules', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sun Travels');
    expect(fixture.nativeElement.querySelectorAll('.upcoming').length).toBe(3);
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
});
