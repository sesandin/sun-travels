import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  StayPeriod,
  Reservation,
  ReservationPage,
  ReservationQuote,
  ReservationRequest,
} from '../models/reservation.model';
@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/reservations';
  // Confirms the reservation using the accepted quotation and current availability.
  confirm(request: ReservationRequest) {
    return this.http.post<Reservation>(this.url, request);
  }
  // Lists reservation records matching the supplied filters.
  list(query = '', status = '', page = 0, stayFrom = '', stayTo = '', stayPeriod: StayPeriod = '') {
    let params = new HttpParams()
      .set('query', query)
      .set('status', status)
      .set('page', page)
      .set('size', 10);
    if (stayPeriod) params = params.set('stayPeriod', stayPeriod);
    if (stayFrom) params = params.set('stayFrom', stayFrom);
    if (stayTo) params = params.set('stayTo', stayTo);
    return this.http.get<ReservationPage>(this.url, { params });
  }
  // Retrieves the requested reservation details.
  get(id: number) {
    return this.http.get<Reservation>(`${this.url}/${id}`);
  }
  // Cancels the selected reservation while preserving its history.
  cancel(id: number) {
    return this.http.post<Reservation>(`${this.url}/${id}/cancel`, {});
  }
  // Tab-scoped draft survives customer registration and refresh. It is never authoritative pricing.
  saveDraft(quote: ReservationQuote) {
    const selling: ReservationQuote = {
      hotelId: quote.hotelId,
      hotelName: quote.hotelName,
      contractId: quote.contractId,
      checkIn: quote.checkIn,
      checkOut: quote.checkOut,
      nights: quote.nights,
      currencyCode: quote.currencyCode,
      totalPrice: quote.totalPrice,
      rooms: quote.rooms.map((r) => ({
        offeringId: r.offeringId,
        roomTypeName: r.roomTypeName,
        adults: r.adults,
        totalPrice: r.totalPrice,
        nightlyPrices: r.nightlyPrices.map((n) => ({
          occupiedDate: n.occupiedDate,
          sellingPrice: n.sellingPrice,
        })),
      })),
    };
    sessionStorage.setItem('suntravels.booking', JSON.stringify(selling));
  }
  // Returns the quotation currently held for reservation review.
  draft(): ReservationQuote | null {
    try {
      const draft = JSON.parse(sessionStorage.getItem('suntravels.booking') ?? 'null');
      return draft && Array.isArray(draft.rooms) && draft.rooms.length ? draft : null;
    } catch {
      return null;
    }
  }
  // Clears the locally held reservation quotation.
  clearDraft() {
    sessionStorage.removeItem('suntravels.booking');
  }
}
