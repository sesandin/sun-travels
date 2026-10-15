import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  Hotel,
  HotelCreateRequest,
  HotelUpdateRequest,
  HotelStatus,
} from '../models/hotel.model';

@Injectable({
  providedIn: 'root'
})
export class HotelService {

  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/hotels';

  getAllHotels(): Observable<Hotel[]> {
    return this.http.get<Hotel[]>(this.apiUrl);
  }

  getHotelById(id: number): Observable<Hotel> {
    return this.http.get<Hotel>(`${this.apiUrl}/${id}`);
  }

  createHotel(request: HotelCreateRequest): Observable<Hotel> {
    return this.http.post<Hotel>(this.apiUrl, request);
  }

  updateHotel(id: number, request: HotelUpdateRequest): Observable<Hotel> {
    return this.http.put<Hotel>(`${this.apiUrl}/${id}`, request);
  }

  updateHotelStatus(id: number, status: HotelStatus): Observable<Hotel> {
    return this.http.patch<Hotel>(
      `${this.apiUrl}/${id}/status`,
      { status }
    );
  }
}
