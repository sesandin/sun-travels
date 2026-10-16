import { DirectoryPage } from '../shared/directory-state';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Contract, ContractRequest } from '../models/contract.model';
@Injectable({ providedIn: 'root' })
export class ContractService {
  page(query = '', status = '', page = 0) {
    return this.http.get<DirectoryPage<Contract>>(this.url + '/page', {
      params: { query, status, page },
    });
  }

  private readonly http = inject(HttpClient);
  private readonly url = '/api/contracts';
  getAllContracts() {
    return this.http.get<Contract[]>(this.url);
  }
  getContractById(id: number) {
    return this.http.get<Contract>(this.url + '/' + id);
  }
  createContract(request: ContractRequest) {
    return this.http.post<Contract>(this.url, request);
  }
  updateContract(id: number, request: ContractRequest) {
    return this.http.put<Contract>(this.url + '/' + id, request);
  }
  updateMarkup(id: number, markupPercentage: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/markup', { markupPercentage });
  }
  approveContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/approve', {});
  }
  reapproveContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/reapprove', {});
  }
  suspendContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/suspend', {});
  }
  terminateContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/terminate', {});
  }
}
