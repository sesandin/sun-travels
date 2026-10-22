import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map } from 'rxjs';
import {
  AccommodationQuotation,
  HotelPage,
  HotelSuggestion,
  CitySuggestion,
  OfferingOccupancy,
  QuotationRequest,
  QuotationResponse,
  QuotationSearchFilters,
  SearchLimits,
} from '../models/quotation.model';

@Injectable({ providedIn: 'root' })
export class QuotationSearchService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/search';
  page(request: QuotationRequest, afterHotelId: number | null = null) {
    const params =
      afterHotelId === null ? new HttpParams() : new HttpParams().set('afterHotelId', afterHotelId);
    return this.http.post<HotelPage>(this.url + '/hotel-pages', request, { params });
  }
  // Retrieves business dates and supported quotation-search limits.
  limits() {
    return this.http.get<SearchLimits>(this.url + '/limits');
  }
  // Retrieves the available hotel and room-type search filters.
  filters() {
    return this.http.get<QuotationSearchFilters>(this.url + '/filters');
  }
  roomTypes() {
    return this.http.get<QuotationSearchFilters['roomTypes']>(this.url + '/room-types');
  }
  cities(term: string, country?: string | null) {
    return this.http.get<CitySuggestion[]>(this.url + '/cities', {
      params: { term, ...(country ? { country } : {}) },
    });
  }
  hotels(term: string, city?: string | null, country?: string | null) {
    return this.http.get<HotelSuggestion[]>(this.url + '/hotels', {
      params: { term, ...(city ? { city } : {}), ...(country ? { country } : {}) },
    });
  }
  hotel(id: number) {
    return this.http.get<HotelSuggestion>(this.url + '/hotels/' + id);
  }
  // Retrieves adult-capacity guidance for eligible room offerings.
  occupancy(request: {
    hotelId: number;
    roomTypeId: number;
    checkIn: string;
    checkOut: string;
    city: string;
  }) {
    return this.http.get<OfferingOccupancy[]>(this.url + '/occupancy', {
      params: new HttpParams({ fromObject: request }),
    });
  }
  // Requests a quotation for the exact selected room offerings.
  customize(request: {
    hotelId: number;
    contractId: number;
    checkIn: string;
    checkOut: string;
    rooms: { offeringId: number; adults: number }[];
  }) {
    return this.http.post<AccommodationQuotation>(this.url + '/selection', request);
  }
  // Requests stay quotations and availability explanations from the API.
  search(request: QuotationRequest) {
    return this.http.post<QuotationResponse>(this.url + '/quotations', request).pipe(
      map((response) => ({
        ...response,
        // Older search responses contain only complete, available quotations.
        quotations: response.quotations
          .map((quote) => ({
            ...quote,
            availabilityStatus: quote.availabilityStatus ?? ('AVAILABLE' as const),
          }))
          .filter((quote) => quote.availabilityStatus === 'AVAILABLE'),
        unavailableOptions: response.unavailableOptions ?? [],
        matchingUnavailableOptions: response.matchingUnavailableOptions ?? 0,
        unavailableTruncated: response.unavailableTruncated ?? false,
      })),
    );
  }
}
