import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Customer, CustomerPage, CustomerRequest } from '../models/customer.model';

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/customers';
  // Searches customer records using the supplied criteria.
  search(query = '', page = 0, size = 10) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) params = params.set('query', query.trim());
    return this.http.get<CustomerPage>(this.url, { params });
  }
  // Retrieves the requested customer details.
  get(id: number) {
    return this.http.get<Customer>(`${this.url}/${id}`);
  }
  // Creates a customer from the validated request.
  create(request: CustomerRequest) {
    return this.http.post<Customer>(this.url, request);
  }
  // Updates the editable fields of the selected customer.
  update(id: number, request: CustomerRequest) {
    return this.http.put<Customer>(`${this.url}/${id}`, request);
  }
}
