import { DirectoryPage } from '../shared/directory-state';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RoomType, RoomTypeRequest } from '../models/room-type.model';
@Injectable({ providedIn: 'root' })
export class RoomTypeService {
  // Retrieves one filtered page of room type directory records.
  page(query = '', page = 0) {
    return this.http.get<DirectoryPage<RoomType>>(this.url + '/page', { params: { query, page } });
  }

  private readonly http = inject(HttpClient);
  private readonly url = '/api/room-types';
  // Returns the all room types.
  getAllRoomTypes() {
    return this.http.get<RoomType[]>(this.url);
  }
  // Returns the room type by id.
  getRoomTypeById(id: number) {
    return this.http.get<RoomType>(this.url + '/' + id);
  }
  // Creates the room type.
  createRoomType(request: RoomTypeRequest) {
    return this.http.post<RoomType>(this.url, request);
  }
  // Updates the room type.
  updateRoomType(id: number, request: RoomTypeRequest) {
    return this.http.put<RoomType>(this.url + '/' + id, request);
  }
}
