import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { QuotationRequest, QuotationResponse, SearchLimits } from '../models/quotation.model';
@Injectable({ providedIn: 'root' })
export class QuotationSearchService {
  private readonly http = inject(HttpClient);
  private readonly url = 'http://localhost:8080/api/search';
  limits() {
    return this.http.get<SearchLimits>(this.url + '/limits');
  }
  search(request: QuotationRequest) {
    return this.http.post<QuotationResponse>(this.url + '/quotations', request);
  }
}
