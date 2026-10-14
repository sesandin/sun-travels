import { Routes } from '@angular/router';
import { HotelList } from './features/hotels/hotel-list/hotel-list';

import { RoomTypeList } from './features/room-types/room-type-list';

export const routes: Routes = [
  { path: '', redirectTo: 'hotels', pathMatch: 'full' },
  { path: 'hotels', component: HotelList },
  { path: 'room-types', component: RoomTypeList },
  { path: '**', redirectTo: 'hotels' }
];