export type HotelStatus = 'ACTIVE' | 'INACTIVE';

export interface Hotel {
  hotelId: number;
  name: string;
  address: string;
  city: string;
  country: string;
  phone: string | null;
  email: string | null;
  starRating: number;
  status: HotelStatus;
}

export type HotelCreateRequest = Omit<Hotel, 'hotelId'>;

export type HotelUpdateRequest = Omit<Hotel, 'hotelId' | 'status'>;

export interface HotelStatusUpdateRequest {
  status: HotelStatus;
}
