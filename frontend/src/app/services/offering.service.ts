import { DirectoryPage } from '../shared/directory-state';
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
  // Retrieves one filtered page of room offering directory records.
  page(filters: Record<string, string>, page = 0) {
    return this.http.get<DirectoryPage<OfferingDirectoryEntry>>(this.url + '/offerings' + '/page', {
      params: { ...filters, page },
    });
  }

  private readonly http = inject(HttpClient);
  private readonly url = '/api';
  // Lists room offering directory entries with their related details.
  directory() {
    return this.http.get<OfferingDirectoryEntry[]>(this.url + '/offerings');
  }
  // Lists room offering records matching the supplied filters.
  list(id: number) {
    return this.http.get<Offering[]>(this.url + '/contracts/' + id + '/offerings');
  }
  // Retrieves the requested room offering details.
  get(id: number) {
    return this.http.get<Offering>(this.url + '/offerings/' + id);
  }
  // Creates a room offering from the validated request.
  create(id: number, request: OfferingRequest) {
    return this.http.post<Offering>(this.url + '/contracts/' + id + '/offerings', request);
  }
  // Updates the editable fields of the selected room offering.
  update(id: number, request: OfferingRequest) {
    return this.http.put<Offering>(this.url + '/offerings/' + id, request);
  }
  // Updates the selected offering’s base nightly rate.
  rate(id: number, ratePerPersonPerNight: number) {
    return this.http.patch<Offering>(this.url + '/offerings/' + id + '/rate', {
      ratePerPersonPerNight,
    });
  }
  // Changes the selected offering’s sales status.
  status(id: number, status: OfferingStatus) {
    return this.http.patch<Offering>(this.url + '/offerings/' + id + '/status', { status });
  }
  // Lists seasonal rate periods for the selected offering.
  seasons(id: number) {
    return this.http.get<SeasonalRate[]>(this.url + '/offerings/' + id + '/seasonal-rates');
  }
  // Creates a seasonal rate period for the selected offering.
  createSeason(id: number, request: SeasonalRateRequest) {
    return this.http.post<SeasonalRate>(this.url + '/offerings/' + id + '/seasonal-rates', request);
  }
  // Updates an existing seasonal rate period.
  updateSeason(id: number, rateId: number, request: SeasonalRateRequest) {
    return this.http.put<SeasonalRate>(
      this.url + '/offerings/' + id + '/seasonal-rates/' + rateId,
      request,
    );
  }
}
