import { TestBed } from '@angular/core/testing';
import { Pagination } from './pagination';

describe('Shared list pagination', () => {
  it('shows totals, disables boundary controls and emits zero-based pages', () => {
    TestBed.configureTestingModule({ imports: [Pagination] });
    const fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('page', 0);
    fixture.componentRef.setInput('totalPages', 3);
    fixture.componentRef.setInput('totalElements', 25);
    fixture.detectChanges();
    const changes = vi.fn();
    fixture.componentInstance.pageChange.subscribe(changes);
    const buttons = fixture.nativeElement.querySelectorAll(
      'button',
    ) as NodeListOf<HTMLButtonElement>;
    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Page 1 of 3');
    expect(fixture.nativeElement.textContent).toContain('25 records');
    buttons[1].click();
    expect(changes).toHaveBeenCalledWith(1);
    fixture.componentRef.setInput('page', 2);
    fixture.detectChanges();
    expect(buttons[0].disabled).toBe(false);
    expect(buttons[1].disabled).toBe(true);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(buttons[0].disabled).toBe(true);
  });

  it('shows an empty count without enabling navigation', () => {
    TestBed.configureTestingModule({ imports: [Pagination] });
    const fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('page', 0);
    fixture.componentRef.setInput('totalPages', 0);
    fixture.componentRef.setInput('totalElements', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('0 records');
    expect(
      Array.from(
        fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
      ).every((button) => button.disabled),
    ).toBe(true);
  });
});
