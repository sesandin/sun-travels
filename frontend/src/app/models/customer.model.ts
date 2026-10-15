export interface Customer { customerId: number; name: string; phone: string | null; email: string | null; }
export interface CustomerRequest { name: string; phone: string | null; email: string | null; }
export interface CustomerPage { customers: Customer[]; page: number; size: number; totalElements: number; totalPages: number; }
