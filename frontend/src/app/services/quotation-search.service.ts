import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { QuotationRequest, QuotationResponse, QuotationSearchFilters, SearchLimits } from '../models/quotation.model';
@Injectable({ providedIn: 'root' })
export class QuotationSearchService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/search';
  limits() {
    return this.http.get<SearchLimits>(this.url + '/limits');
  }
  filters() { return this.http.get<QuotationSearchFilters>(this.url + '/filters'); }
  search(request: QuotationRequest) {
    return this.http.post<QuotationResponse>(this.url + '/quotations', request);
  }
}
