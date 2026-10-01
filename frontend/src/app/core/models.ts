export type Role = 'ADMIN' | 'ANALYST' | 'INVESTIGATOR' | 'CUSTOMER';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Decision = 'ALLOW' | 'REVIEW' | 'BLOCK';
export type TransactionStatus =
  | 'PENDING'
  | 'VALIDATED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'FLAGGED'
  | 'UNDER_REVIEW'
  | 'BLOCKED';
export type TransactionType = 'PURCHASE' | 'WITHDRAWAL' | 'TRANSFER' | 'DEPOSIT' | 'PAYMENT' | 'REFUND';
export type Channel = 'WEB' | 'MOBILE' | 'ATM' | 'POS' | 'API';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type Resolution = 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE' | 'DISMISSED';
export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT' | 'WALLET';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type CustomerStatus = 'ACTIVE' | 'BLOCKED' | 'CLOSED';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface SearchResults<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
  violations?: { field: string; message: string }[];
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  subject: string;
  roles: Role[];
  customerId: string | null;
  expiresInSeconds: number;
}

export interface RegisterRequest {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  countryCode: string;
}

export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  countryCode: string;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Account {
  id: string;
  customerId: string;
  accountNumber: string;
  type: AccountType;
  currency: string;
  balance: number;
  creditLimit: number;
  status: AccountStatus;
  openedAt: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateAccountRequest {
  customerId: string;
  type: AccountType;
  currency: string;
  initialBalance: number;
  creditLimit?: number;
}

export interface Transaction {
  id: string;
  accountId: string;
  customerId: string;
  amount: number;
  currency: string;
  type: TransactionType;
  status: TransactionStatus;
  merchantId: string | null;
  merchantCategory: string | null;
  countryCode: string;
  city: string | null;
  channel: string;
  fraudScore: number | null;
  severity: Severity | null;
  decision: Decision | null;
  reasonCode: string | null;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTransactionRequest {
  accountId: string;
  customerId: string;
  amount: number;
  currency: string;
  type: TransactionType;
  merchantId?: string;
  merchantCategory?: string;
  countryCode: string;
  city?: string;
  latitude?: number;
  longitude?: number;
  deviceId?: string;
  ipAddress?: string;
  channel?: Channel;
}

/** Elasticsearch projection of a scored transaction. */
export interface ScoredTransaction {
  transactionId: string;
  accountId: string;
  customerId: string;
  amount: number;
  currency: string;
  type: TransactionType;
  countryCode: string;
  merchantId: string | null;
  merchantCategory: string | null;
  deviceId: string | null;
  ipAddress: string | null;
  latitude: number | null;
  longitude: number | null;
  score: number;
  severity: Severity;
  decision: Decision;
  modelRiskScore: number | null;
  occurredAt: string;
  correlationId: string;
}

export interface FraudEvent {
  eventId: string;
  transactionId: string;
  customerId: string;
  accountId: string;
  score: number;
  severity: Severity;
  decision: Decision;
  primaryReason: string | null;
  triggeredRuleCodes: string[];
  amount: number;
  currency: string;
  countryCode: string;
  modelRiskScore: number | null;
  occurredAt: string;
  correlationId: string;
}

export interface Alert {
  id: string;
  transactionId: string;
  customerId: string;
  accountId: string;
  severity: Severity;
  score: number;
  status: AlertStatus;
  title: string;
  description: string | null;
  primaryReason: string | null;
  assignedTo: string | null;
  resolution: Resolution | null;
  resolvedBy: string | null;
  resolutionNotes: string | null;
  resolvedAt: string | null;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
}

export interface FraudRule {
  code: string;
  name: string;
  description: string;
  ruleType: string;
  weight: number;
  enabled: boolean;
  thresholdNumeric: number | null;
  thresholdInt: number | null;
  paramsJson: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export type FraudRuleUpdate = Omit<FraudRule, 'code' | 'createdAt' | 'updatedAt' | 'version'>;

export interface NotificationRecord {
  id: string;
  alertId: string;
  transactionId: string;
  customerId: string;
  channel: 'EMAIL' | 'SMS' | 'PUSH';
  recipient: string;
  subject: string;
  severity: Severity;
  status: 'PENDING' | 'SENT' | 'FAILED';
  createdAt: string;
}

export interface AuditEvent {
  eventId: string;
  eventType: string;
  correlationId: string;
  occurredAt: string;
  indexedAt: string;
  transactionId: string | null;
  accountId: string | null;
  customerId: string | null;
  alertId: string | null;
  amount: number | null;
  currency: string | null;
  score: number | null;
  severity: Severity | null;
  decision: Decision | null;
  reasonCode: string | null;
  summary: string | null;
}

export interface UserAccount {
  id: string;
  username: string;
  roles: Role[];
  enabled: boolean;
  customerId: string | null;
  createdAt: string;
}
