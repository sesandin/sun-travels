import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RoomType, RoomTypeRequest } from '../models/room-type.model';
@Injectable({ providedIn: 'root' })
export class RoomTypeService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/room-types';
  getAllRoomTypes() {
    return this.http.get<RoomType[]>(this.url);
  }
  getRoomTypeById(id: number) {
    return this.http.get<RoomType>(this.url + '/' + id);
  }
  createRoomType(request: RoomTypeRequest) {
    return this.http.post<RoomType>(this.url, request);
  }
  updateRoomType(id: number, request: RoomTypeRequest) {
    return this.http.put<RoomType>(this.url + '/' + id, request);
  }
}
