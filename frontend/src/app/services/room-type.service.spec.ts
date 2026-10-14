import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RoomTypeService } from './room-type.service';
describe('RoomTypeService', () => {
 it('retrieves a room type by ID from the backend', () => {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const row = { roomTypeId: 2, name: 'Standard', description: null };
  let result: unknown;
  TestBed.inject(RoomTypeService).getRoomTypeById(2).subscribe(value => result = value);
  const request = http.expectOne('http://localhost:8080/api/room-types/2');
  expect(request.request.method).toBe('GET'); request.flush(row); expect(result).toEqual(row); http.verify();
 });
});
