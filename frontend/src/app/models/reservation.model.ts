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
    nightlyPrices: (SellingNight & {
      bookedRatePerAdult?: number | null;
      bookedMarkupPercentage?: number | null;
    })[];
  }[];
}
export interface ReservationSummary {
  reservationId: number;
  customerId: number;
  customerName: string;
  checkIn: string;
  checkOut: string;
  status: 'CONFIRMED' | 'CANCELLED';
  currencyCode: string | null;
  totalPrice: number | null;
  rooms: { hotelId: number; hotelName: string; roomTypeName: string; adults: number }[];
  hasOperationalWarnings: boolean;
}
export interface ReservationPage {
  reservations: ReservationSummary[];
  page: number;
  size: number;
  totalElements: number;
}

export type StayPeriod = '' | 'UPCOMING' | 'ONGOING' | 'PAST';
