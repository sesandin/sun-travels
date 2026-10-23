import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Reservation } from '../../models/reservation.model';

@Component({
  selector: 'app-booked-price-breakdown',
  imports: [DecimalPipe],
  template: `
    <details open>
      <summary>Price per night for room {{ roomNumber() }}</summary>
      <p class="explanation">
        Prices cover {{ room().adults }} {{ room().adults === 1 ? 'adult' : 'adults' }}.
        The checkout night is not charged.
      </p>
      @if (room().nightlyPrices.length) {
        <div class="prices" tabindex="0" role="region" aria-label="Booked nightly price breakdown">
          <table>
            <thead>
              <tr>
                <th scope="col">Night</th>
                <th scope="col">Rate per adult</th>
                <th scope="col">Markup</th>
                <th scope="col">Adults</th>
                <th scope="col">Room price</th>
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
            @if (showTotal() && room().totalPrice !== null) {
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
          The room total is unavailable because some original prices are missing.
        </p>
      }
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
  readonly showTotal = input(true);
}
