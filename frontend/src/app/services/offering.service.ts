import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  Offering,
  OfferingDirectoryEntry,
  OfferingRequest,
  OfferingStatus,
  SeasonalRate,
  SeasonalRateRequest,
} from '../models/offering.model';
@Injectable({ providedIn: 'root' })
export class OfferingService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api';
  directory() {
    return this.http.get<OfferingDirectoryEntry[]>(this.url + '/offerings');
  }
  list(id: number) {
    return this.http.get<Offering[]>(this.url + '/contracts/' + id + '/offerings');
  }
  get(id: number) {
    return this.http.get<Offering>(this.url + '/offerings/' + id);
  }
  create(id: number, request: OfferingRequest) {
    return this.http.post<Offering>(this.url + '/contracts/' + id + '/offerings', request);
  }
  update(id: number, request: OfferingRequest) {
    return this.http.put<Offering>(this.url + '/offerings/' + id, request);
  }
  rate(id: number, ratePerPersonPerNight: number) {
    return this.http.patch<Offering>(this.url + '/offerings/' + id + '/rate', {
      ratePerPersonPerNight,
    });
  }
  status(id: number, status: OfferingStatus) {
    return this.http.patch<Offering>(this.url + '/offerings/' + id + '/status', { status });
  }
  seasons(id: number) {
    return this.http.get<SeasonalRate[]>(this.url + '/offerings/' + id + '/seasonal-rates');
  }
  createSeason(id: number, request: SeasonalRateRequest) {
    return this.http.post<SeasonalRate>(this.url + '/offerings/' + id + '/seasonal-rates', request);
  }
  removeSeason(id: number, rateId: number) {
    return this.http.delete<void>(this.url + '/offerings/' + id + '/seasonal-rates/' + rateId);
  }
  updateSeason(id: number, rateId: number, request: SeasonalRateRequest) {
    return this.http.put<SeasonalRate>(
      this.url + '/offerings/' + id + '/seasonal-rates/' + rateId,
      request,
    );
  }
}
