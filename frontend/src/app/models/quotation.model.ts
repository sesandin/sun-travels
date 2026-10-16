export interface OfferingOccupancy {
  offeringId: number;
  roomTypeId: number;
  roomTypeName: string;
  maxAdults: number;
}
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
  availabilityStatus: 'AVAILABLE';
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
  selectionOfferings?: SelectionOffering[];
  businessDate: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  quotations: AccommodationQuotation[];
  matchingOptions: number;
  truncated: boolean;
  allocationPolicy: string;
  notice: string;
  unavailableOptions: UnavailableAccommodation[];
  matchingUnavailableOptions: number;
  unavailableTruncated: boolean;
}
export interface UnavailableAccommodation {
  hotelId: number;
  hotelName: string;
  city: string | null;
  contractId: number;
  contractCode: string;
  currencyCode: string;
  availabilityStatus: 'NOT_AVAILABLE';
  offerings: { offeringId: number; roomTypeId: number; roomTypeName: string; maxAdults: number; availableRoomsForStay: number }[];
  reasons: { code: 'INSUFFICIENT_INVENTORY' | 'MAX_ADULT_OCCUPANCY' | 'HOTEL_CLOSED'; message: string }[];
  indicativeTotalPrice: number | null;
}

export interface SelectionOffering {
 hotelId: number; hotelName: string; contractId: number; offeringId: number; roomTypeName: string;
 maxAdults: number; availableRoomsForStay: number; currencyCode: string; hotelClosed: boolean;
 rooms: { roomNumber: number; adults: number; totalPrice: number | null }[];
}
