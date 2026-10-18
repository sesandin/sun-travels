import { DirectoryPage } from '../shared/directory-state';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Hotel, HotelCreateRequest, HotelUpdateRequest, HotelStatus } from '../models/hotel.model';

@Injectable({
  providedIn: 'root',
})
export class HotelService {
  // Retrieves one filtered page of hotel directory records.
  page(query = '', status = '', page = 0) {
    return this.http.get<DirectoryPage<Hotel>>(this.apiUrl + '/page', {
      params: { query, status, page },
    });
  }

  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/hotels';

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
