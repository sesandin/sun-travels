import { Routes } from '@angular/router';
import { HotelList } from './features/hotels/hotel-list/hotel-list';

export const routes: Routes = [
  { path: '', redirectTo: 'hotels', pathMatch: 'full' },
  { path: 'hotels', component: HotelList },
  { path: '**', redirectTo: 'hotels' }
];