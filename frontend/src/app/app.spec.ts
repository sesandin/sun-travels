import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
describe('App', () => {
  it('renders branding and clearly marks upcoming modules', async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(App); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sun Travels');
    expect(fixture.nativeElement.querySelectorAll('.upcoming').length).toBe(6);
    fixture.componentInstance.menuOpen.set(true); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.sidebar').classList.contains('open')).toBe(true);
  });
});
