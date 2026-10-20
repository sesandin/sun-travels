import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Reservation } from '../../models/reservation.model';

@Component({
  selector: 'app-booked-price-breakdown',
  imports: [DecimalPipe],
  template: `
    <details open>
      <summary>Nightly price breakdown for room {{ roomNumber() }}</summary>
      <p class="explanation">
        These are the saved prices for this room at booking, including markup and all
        {{ room().adults }} {{ room().adults === 1 ? 'adult' : 'adults' }}. Nightly prices may
        differ when seasonal rates apply. Checkout day is not charged.
      </p>
      @if (room().nightlyPrices.length) {
        <div class="prices" tabindex="0" role="region" aria-label="Booked nightly price breakdown">
          <table>
            <thead>
              <tr>
                <th scope="col">Occupied night</th>
                <th scope="col">Booked rate / adult / night</th>
                <th scope="col">Booked markup</th>
                <th scope="col">Adults</th>
                <th scope="col">Selling price / room</th>
              </tr>
            </thead>
            <tbody>
              @for (night of room().nightlyPrices; track night.occupiedDate) {
                <tr>
                  <td>{{ night.occupiedDate }}</td>
                  <td>
                    @if (night.bookedRatePerAdult != null) {
                      {{ night.bookedRatePerAdult | number: '1.2-2' }} {{ currency() }}
                    } @else {
                      Unavailable
                    }
                  </td>
                  <td>
                    @if (night.bookedMarkupPercentage != null) {
                      {{ night.bookedMarkupPercentage | number: '1.0-4' }}%
                    } @else {
                      Unavailable
                    }
                  </td>
                  <td>{{ room().adults }}</td>
                  <td>{{ night.sellingPrice | number: '1.2-2' }} {{ currency() }}</td>
                </tr>
              }
            </tbody>
            @if (room().totalPrice !== null) {
              <tfoot>
                <tr>
                  <th scope="row" colspan="4">
                    Room total · {{ nights() }} {{ nights() === 1 ? 'night' : 'nights' }}
                  </th>
                  <td>{{ room().totalPrice | number: '1.2-2' }} {{ currency() }}</td>
                </tr>
              </tfoot>
            }
          </table>
        </div>
      }
      @if (room().totalPrice === null) {
        <p class="explanation">
          Some booked nightly prices are missing, so a complete room total is unavailable.
        </p>
      }
      <p class="explanation">
        Each night's room price = booked rate per adult × adults × (1 + booked markup ÷ 100),
        rounded to 2 decimal places. The room total adds these nightly prices. These are the rates
        saved at confirmation, including any seasonal rate that applied. Later rate changes do not
        change your booked price.
      </p>
    </details>
  `,
  styles: `
    :host {
      display: block;
      margin: 12px 0 20px;
    }
    details {
      padding: 8px 0;
    }
    summary {
      cursor: pointer;
      font-weight: 650;
      color: var(--primary);
    }
    .explanation {
      color: var(--muted);
      font-size: 12px;
      line-height: 1.6;
      margin: 12px 0 0;
    }
    .prices {
      overflow-x: auto;
      margin-top: 12px;
    }
    table {
      width: 100%;
      min-width: 480px;
      border-collapse: collapse;
      font-size: 13px;
    }
    th,
    td {
      padding: 16px 20px;
      text-align: left;
      border-bottom: 1px solid var(--border);
    }
    thead {
      background: #f3f7f6;
    }
    thead th {
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 0.6px;
    }
    td:last-child {
      white-space: nowrap;
    }
    tfoot {
      font-weight: 650;
    }
  `,
})
export class BookedPriceBreakdown {
  readonly room = input.required<Reservation['rooms'][number]>();
  readonly currency = input.required<string>();
  readonly nights = input.required<number>();
  readonly roomNumber = input(1);
}
