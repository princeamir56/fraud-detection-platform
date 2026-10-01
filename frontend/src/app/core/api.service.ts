import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account,
  AccountStatus,
  Alert,
  AlertStatus,
  AuditEvent,
  CreateAccountRequest,
  CreateTransactionRequest,
  Customer,
  CustomerStatus,
  FraudEvent,
  FraudRule,
  FraudRuleUpdate,
  NotificationRecord,
  Page,
  Resolution,
  Role,
  ScoredTransaction,
  SearchResults,
  Transaction,
  UserAccount,
} from './models';

type Query = Record<string, string | number | boolean | null | undefined>;

function params(q: Query): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(q)) {
    if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
  }
  return p;
}

const BASE = '/api/v1';

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // --- Transactions -------------------------------------------------------
  transaction(id: string): Observable<Transaction> {
    return this.http.get<Transaction>(`${BASE}/transactions/${id}`);
  }
  transactionHistory(q: { customerId?: string; accountId?: string; page?: number; size?: number }) {
    return this.http.get<Page<Transaction>>(`${BASE}/transactions`, { params: params(q) });
  }
  createTransaction(body: CreateTransactionRequest, idempotencyKey: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${BASE}/transactions`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  // --- Search (Elasticsearch) --------------------------------------------
  searchTransactions(q: Query): Observable<SearchResults<ScoredTransaction>> {
    return this.http.get<SearchResults<ScoredTransaction>>(`${BASE}/search/transactions`, { params: params(q) });
  }
  searchFraudEvents(q: Query): Observable<SearchResults<FraudEvent>> {
    return this.http.get<SearchResults<FraudEvent>>(`${BASE}/search/fraud-events`, { params: params(q) });
  }

  // --- Alerts -------------------------------------------------------------
  alerts(q: { status?: AlertStatus | ''; severity?: string; customerId?: string; page?: number; size?: number }) {
    return this.http.get<Page<Alert>>(`${BASE}/alerts`, { params: params(q) });
  }
  alert(id: string): Observable<Alert> {
    return this.http.get<Alert>(`${BASE}/alerts/${id}`);
  }
  acknowledgeAlert(id: string): Observable<Alert> {
    return this.http.post<Alert>(`${BASE}/alerts/${id}/acknowledge`, null);
  }
  resolveAlert(id: string, resolution: Resolution, notes: string): Observable<Alert> {
    return this.http.post<Alert>(`${BASE}/alerts/${id}/resolve`, { resolution, notes });
  }

  // --- Rules --------------------------------------------------------------
  rules(): Observable<FraudRule[]> {
    return this.http.get<FraudRule[]>(`${BASE}/fraud-rules`);
  }
  updateRule(code: string, body: FraudRuleUpdate): Observable<FraudRule> {
    return this.http.put<FraudRule>(`${BASE}/fraud-rules/${code}`, body);
  }
  setRuleEnabled(code: string, enabled: boolean): Observable<FraudRule> {
    return this.http.patch<FraudRule>(`${BASE}/fraud-rules/${code}/enabled`, null, {
      params: params({ enabled }),
    });
  }

  // --- Customers & accounts ----------------------------------------------
  customers(q: { status?: CustomerStatus | ''; page?: number; size?: number }) {
    return this.http.get<Page<Customer>>(`${BASE}/customers`, { params: params(q) });
  }
  customer(id: string): Observable<Customer> {
    return this.http.get<Customer>(`${BASE}/customers/${id}`);
  }
  setCustomerStatus(id: string, status: CustomerStatus): Observable<Customer> {
    return this.http.patch<Customer>(`${BASE}/customers/${id}/status`, null, { params: params({ status }) });
  }
  accounts(customerId: string): Observable<Page<Account>> {
    return this.http.get<Page<Account>>(`${BASE}/accounts`, { params: params({ customerId, size: 50 }) });
  }
  createAccount(body: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${BASE}/accounts`, body);
  }
  creditAccount(id: string, amount: number, reason: string): Observable<Account> {
    return this.http.post<Account>(`${BASE}/accounts/${id}/credit`, { amount, reason });
  }
  setAccountStatus(id: string, status: AccountStatus): Observable<Account> {
    return this.http.patch<Account>(`${BASE}/accounts/${id}/status`, null, { params: params({ status }) });
  }

  // --- Notifications, audit, users ---------------------------------------
  notifications(q: { customerId?: string; alertId?: string; page?: number; size?: number }) {
    return this.http.get<Page<NotificationRecord>>(`${BASE}/notifications`, { params: params(q) });
  }
  auditEvents(q: Query): Observable<SearchResults<AuditEvent>> {
    return this.http.get<SearchResults<AuditEvent>>(`${BASE}/audit/events`, { params: params(q) });
  }
  createUser(username: string, password: string, roles: Role[]): Observable<UserAccount> {
    return this.http.post<UserAccount>(`${BASE}/users`, { username, password, roles });
  }
}
