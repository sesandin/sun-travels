export type ContractStatus = 'DRAFT' | 'APPROVED' | 'SUSPENDED' | 'TERMINATED';
export interface ContractRequest {
  hotelId: number;
  validFrom: string;
  validTo: string;
  markupPercentage: number;
  currencyCode: string;
}
export interface Contract extends ContractRequest {
  contractCode: string;
  contractId: number;
  hotelName: string;
  status: ContractStatus;
}
