import { DirectoryPage } from '../shared/directory-state';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Contract, ContractRequest } from '../models/contract.model';
@Injectable({ providedIn: 'root' })
export class ContractService {
  // Retrieves one filtered page of contract directory records.
  page(query = '', status = '', page = 0) {
    return this.http.get<DirectoryPage<Contract>>(this.url + '/page', {
      params: { query, status, page },
    });
  }

  private readonly http = inject(HttpClient);
  private readonly url = '/api/contracts';
  // Returns the all contracts.
  getAllContracts() {
    return this.http.get<Contract[]>(this.url);
  }
  // Returns the contract by id.
  getContractById(id: number) {
    return this.http.get<Contract>(this.url + '/' + id);
  }
  // Creates the contract.
  createContract(request: ContractRequest) {
    return this.http.post<Contract>(this.url, request);
  }
  // Updates the contract.
  updateContract(id: number, request: ContractRequest) {
    return this.http.put<Contract>(this.url + '/' + id, request);
  }
  // Updates the markup.
  updateMarkup(id: number, markupPercentage: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/markup', { markupPercentage });
  }
  // Approves the contract.
  approveContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/approve', {});
  }
  // Reapproves the contract.
  reapproveContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/reapprove', {});
  }
  // Suspends the contract.
  suspendContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/suspend', {});
  }
  // Terminates the contract.
  terminateContract(id: number) {
    return this.http.patch<Contract>(this.url + '/' + id + '/terminate', {});
  }
}
