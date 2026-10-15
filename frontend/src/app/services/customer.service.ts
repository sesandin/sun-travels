import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Customer, CustomerPage, CustomerRequest } from '../models/customer.model';
@Injectable({ providedIn: 'root' })
export class CustomerService {
 private readonly http=inject(HttpClient); private readonly url='/api/customers';
 search(query='',page=0,size=20) { let params=new HttpParams().set('page',page).set('size',size); if(query.trim()) params=params.set('query',query.trim()); return this.http.get<CustomerPage>(this.url,{params}); }
 get(id:number) { return this.http.get<Customer>(`${this.url}/${id}`); }
 create(request:CustomerRequest) { return this.http.post<Customer>(this.url,request); }
 update(id:number,request:CustomerRequest) { return this.http.put<Customer>(`${this.url}/${id}`,request); }
}
