import { QuotationSearch } from './features/search/quotation-search';
import { OfferingDirectory } from './features/room-offerings/offering-directory';
import { ContractList } from './features/contracts/contract-list';
import { Routes } from '@angular/router';
import { HotelList } from './features/hotels/hotel-list/hotel-list';

import { RoomTypeList } from './features/room-types/room-type-list';
import { Login } from './features/auth/login';
import { CustomerList } from './features/customers/customer-list';
import { ReservationBooking } from './features/reservations/reservation-booking';
import { ReservationDirectory } from './features/reservations/reservation-directory';
import { ReservationDetail } from './features/reservations/reservation-detail';
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
  { path: 'reservations/new', component: ReservationBooking, canActivate: [authenticatedGuard] },
  { path: 'reservations', component: ReservationDirectory, canActivate: [authenticatedGuard] },
  { path: 'reservations/:id', component: ReservationDetail, canActivate: [authenticatedGuard] },
  { path: '**', redirectTo: 'search' },
];
