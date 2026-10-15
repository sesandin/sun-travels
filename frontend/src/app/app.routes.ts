import { QuotationSearch } from './features/search/quotation-search';
import { OfferingDirectory } from './features/room-offerings/offering-directory';
import { ContractList } from './features/contracts/contract-list';
import { Routes } from '@angular/router';
import { HotelList } from './features/hotels/hotel-list/hotel-list';

import { RoomTypeList } from './features/room-types/room-type-list';
import { Login } from './features/auth/login';
import { CustomerList } from './features/customers/customer-list';
import { adminGuard, authenticatedGuard, loginGuard } from './auth.guards';

export const routes: Routes = [
  { path: '', redirectTo: 'search', pathMatch: 'full' },
  { path: 'login', component: Login, canActivate: [loginGuard] },
  { path: 'hotels', component: HotelList, canActivate: [adminGuard] },
  { path: 'room-types', component: RoomTypeList, canActivate: [adminGuard] },
  { path: 'contracts', component: ContractList, canActivate: [adminGuard] },
  { path: 'room-offerings', component: OfferingDirectory, canActivate: [adminGuard] },
  { path: 'search', component: QuotationSearch, canActivate: [authenticatedGuard] },
  { path: 'customers', component: CustomerList, canActivate: [authenticatedGuard] },
  { path: '**', redirectTo: 'search' },
];
