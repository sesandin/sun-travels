export interface SellingNight {
  occupiedDate: string;
  sellingPrice: number;
}
export interface ReservationQuote {
  hotelId: number;
  hotelName: string;
  contractId: number;
  checkIn: string;
  checkOut: string;
  nights: number;
  currencyCode: string;
  totalPrice: number;
  rooms: {
    offeringId: number;
    roomTypeName: string;
    adults: number;
    totalPrice: number;
    nightlyPrices: SellingNight[];
  }[];
}
export interface ReservationRequest {
  customerId: number;
  hotelId: number;
  contractId: number;
  checkIn: string;
  checkOut: string;
  rooms: { offeringId: number; adults: number }[];
  acceptedTotal: number;
  acceptedCurrency: string;
}
export interface ReservationWarning {
  hotelId: number;
  hotelName: string;
  code: 'HOTEL_INACTIVE' | 'CONTRACT_SUSPENDED' | 'OFFERING_SUSPENDED';
  message: string;
  contractId?: number | null;
  offeringId?: number | null;
  roomTypeName?: string | null;
}
export interface Reservation {
  warnings?: ReservationWarning[];
  reservationId: number;
  customerId: number;
  customerName: string;
  phone: string | null;
  email: string | null;
  checkIn: string;
  checkOut: string;
  nights: number;
  status: 'CONFIRMED' | 'CANCELLED';
  createdAt: string;
  currencyCode: string;
  totalPrice: number | null;
  rooms: {
    reservationItemId: number;
    offeringId: number;
    hotelId: number;
    hotelName: string;
    contractId: number;
    roomTypeName: string;
    adults: number;
    totalPrice: number | null;
    nightlyPrices: SellingNight[];
  }[];
}
export interface ReservationPage {
  reservations: Reservation[];
  page: number;
  size: number;
  totalElements: number;
}

export type StayPeriod = '' | 'UPCOMING' | 'ONGOING' | 'PAST';
