export interface QuotationRequest {
  checkIn: string;
  checkOut: string;
  rooms: { adults: number }[];
  city: string | null;
  hotelId: number | null;
  roomTypeId: number | null;
}
export interface SearchLimits {
  businessDate: string;
  maxRooms: number;
  maxAdultsPerRoom: number;
  maxNights: number;
  maxAdvanceDays: number;
  maxResults: number;
  allocationPolicy: string;
}
export interface QuotationSearchFilters {
  hotels: { hotelId: number; name: string; city: string | null }[];
  roomTypes: { roomTypeId: number; name: string }[];
}
export interface NightlyPrice {
  occupiedDate: string;
  source: 'BASE' | 'SEASONAL';
  seasonalRateId: number | null;
  hotelierRate: number;
  markupPercentage: number;
  sellingPrice: number;
}
export interface QuotedRoom {
  roomNumber: number;
  adults: number;
  offeringId: number;
  roomTypeId: number;
  roomTypeName: string;
  contractedRooms: number;
  availableRoomsForStay: number;
  allocatedRoomsForOffering: number;
  remainingRoomsAfterAllocation: number;
  totalPrice: number;
  nightlyPrices: NightlyPrice[];
}
export interface AccommodationQuotation {
  hotelId: number;
  hotelName: string;
  city: string | null;
  contractId: number;
  contractCode: string;
  currencyCode: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  rooms: QuotedRoom[];
  totalPrice: number;
  eligibility: 'INFORMATIONAL';
}
export interface QuotationResponse {
  businessDate: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  quotations: AccommodationQuotation[];
  matchingOptions: number;
  truncated: boolean;
  allocationPolicy: string;
  notice: string;
}
