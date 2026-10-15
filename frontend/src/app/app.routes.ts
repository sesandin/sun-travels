import { QuotationSearch } from './features/search/quotation-search';
import { OfferingDirectory } from './features/room-offerings/offering-directory';
import { ContractList } from './features/contracts/contract-list';
import { Routes } from '@angular/router';
import { HotelList } from './features/hotels/hotel-list/hotel-list';

import { RoomTypeList } from './features/room-types/room-type-list';

export const routes: Routes = [
  { path: '', redirectTo: 'hotels', pathMatch: 'full' },
  { path: 'hotels', component: HotelList },
  { path: 'room-types', component: RoomTypeList },
  { path: 'contracts', component: ContractList },
  { path: 'room-offerings', component: OfferingDirectory },
  { path: 'search', component: QuotationSearch },
  { path: '**', redirectTo: 'hotels' },
];
