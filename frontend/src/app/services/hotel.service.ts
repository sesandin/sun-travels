import { DirectoryPage } from '../shared/directory-state';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Hotel, HotelCreateRequest, HotelStatus, HotelUpdateRequest } from '../models/hotel.model';
export type ManagementHotelOption = Pick<Hotel, 'hotelId' | 'name' | 'city' | 'country' | 'status'>;

@Injectable({
  providedIn: 'root',
})
export class HotelService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/hotels';

  // Retrieves one filtered page of hotel directory records.
  page(query = '', status = '', page = 0) {
    return this.http.get<DirectoryPage<Hotel>>(this.apiUrl + '/page', {
      params: { query, status, page },
    });
  }

  // Retrieves a bounded management selector result, including inactive hotels.
  options(term: string, status = '') {
    return this.http.get<ManagementHotelOption[]>(this.apiUrl + '/options', {
      params: status ? { term, status } : { term },
    });
  }
  option(id: number) {
    return this.http.get<ManagementHotelOption>(this.apiUrl + '/options/' + id);
  }

  // Returns the all hotels.
  getAllHotels(): Observable<Hotel[]> {
    return this.http.get<Hotel[]>(this.apiUrl);
  }

  // Returns the hotel by id.
  getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.apiUrl}/${id}`);
  }

  // Creates the hotel.
  createHotel(request: HotelCreateRequest): Observable<Hotel> {
    return this.http.post<Hotel>(this.apiUrl, request);
  }

  // Updates the hotel.
  updateHotel(id: number, request: HotelUpdateRequest): Observable<Hotel> {
    return this.http.put<Hotel>(`${this.apiUrl}/${id}`, request);
  }

  // Updates the hotel status.
  updateHotelStatus(id: number, status: HotelStatus): Observable<Hotel> {
    return this.http.patch<Hotel>(`${this.apiUrl}/${id}/status`, { status });
  }
}
