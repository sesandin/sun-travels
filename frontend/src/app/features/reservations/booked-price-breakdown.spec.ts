import { TestBed } from '@angular/core/testing';
import { BookedPriceBreakdown } from './booked-price-breakdown';

describe('Booked price breakdown', () => {
  function render(
    totalPrice: number | null = 369.6,
    nightlyPrices = [
      {
        occupiedDate: '2026-11-10',
        sellingPrice: 123.2,
        bookedRatePerAdult: 55,
        bookedMarkupPercentage: 12,
      },
      {
        occupiedDate: '2026-11-11',
        sellingPrice: 123.2,
        bookedRatePerAdult: 55,
        bookedMarkupPercentage: 12,
      },
      {
        occupiedDate: '2026-11-12',
        sellingPrice: 123.2,
        bookedRatePerAdult: 55,
        bookedMarkupPercentage: 12,
      },
    ],
  ) {
    const fixture = TestBed.createComponent(BookedPriceBreakdown);
    fixture.componentRef.setInput('room', { adults: 2, totalPrice, nightlyPrices });
    fixture.componentRef.setInput('currency', 'USD');
    fixture.componentRef.setInput('nights', 3);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows each booked night and its authoritative total without charging checkout', () => {
    const element = render();
    expect(element.querySelector('details')?.open).toBe(true);
    expect(element.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(element.querySelector('tbody')?.textContent).not.toContain('2026-11-13');
    expect(element.querySelector('tfoot')?.textContent).toContain('369.60 USD');
    expect(element.textContent).toContain('2 adults');
    expect(element.textContent).toContain('including markup');
    expect(element.querySelector('summary')?.textContent).toContain('room 1');
    expect(
      Array.from(element.querySelectorAll('thead th'), (cell) => cell.textContent?.trim()),
    ).toEqual([
      'Occupied night',
      'Booked rate / adult / night',
      'Booked markup',
      'Adults',
      'Selling price / room',
    ]);
    expect(element.querySelector('tbody tr td:nth-child(2)')?.textContent).toContain('55.00 USD');
    expect(element.querySelector('tbody tr td:nth-child(3)')?.textContent).toContain('12%');
    expect(element.querySelector('tbody tr td:nth-child(4)')?.textContent?.trim()).toBe('2');
  });

  it('keeps differing nightly prices visible instead of replacing them with an average', () => {
    const element = render(302.4, [
      {
        occupiedDate: '2026-11-10',
        sellingPrice: 89.6,
        bookedRatePerAdult: 40,
        bookedMarkupPercentage: 12,
      },
      {
        occupiedDate: '2026-11-11',
        sellingPrice: 123.2,
        bookedRatePerAdult: 55,
        bookedMarkupPercentage: 12,
      },
      {
        occupiedDate: '2026-11-12',
        sellingPrice: 89.6,
        bookedRatePerAdult: 40,
        bookedMarkupPercentage: 12,
      },
    ]);
    expect(
      Array.from(element.querySelectorAll('tbody tr td:last-child'), (cell) =>
        cell.textContent?.trim(),
      ),
    ).toEqual(['89.60 USD', '123.20 USD', '89.60 USD']);
    expect(element.querySelector('tfoot')?.textContent).toContain('302.40 USD');
  });

  it('does not invent a total when historical snapshots are incomplete', () => {
    const element = render(null, []);
    expect(element.querySelector('tfoot')).toBeNull();
    expect(element.textContent).toContain('complete room total is unavailable');
    expect(element.textContent).not.toContain('0.00 USD');
  });
});
