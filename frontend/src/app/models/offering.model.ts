export type OfferingStatus = 'ENABLED' | 'SUSPENDED';
export interface OfferingRequest {
  roomTypeId: number;
  ratePerPersonPerNight: number;
  contractedRoomCount: number;
  maxAdults: number;
  status: OfferingStatus;
}
export interface Offering extends OfferingRequest {
  offeringId: number;
  contractId: number;
  roomTypeName: string;
  currencyCode: string;
}
export interface SeasonalRateRequest {
  validFrom: string;
  validTo: string;
  ratePerPersonPerNight: number;
}
export interface SeasonalRate extends SeasonalRateRequest {
  seasonalRateId: number;
  offeringId: number;
}

export interface OfferingDirectoryEntry {
  offeringId: number;
  hotelId: number;
  hotelName: string;
  hotelStatus: 'ACTIVE' | 'INACTIVE';
  contractId: number;
  contractCode: string;
  contractStatus: import('./contract.model').ContractStatus;
  validFrom: string;
  validTo: string;
  roomTypeId: number;
  roomTypeName: string;
  ratePerPersonPerNight: number;
  currencyCode: string;
  contractedRoomCount: number;
  maxAdults: number;
  status: OfferingStatus;
  seasonalRateCount: number;
}
