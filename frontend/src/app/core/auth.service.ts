import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, RegisterRequest, Role } from './models';

interface Session {
  token: string;
  subject: string;
  roles: Role[];
  customerId: string | null;
  expiresAt: number;
}

const STORAGE_KEY = 'fd.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());
  private expiryTimer?: ReturnType<typeof setTimeout>;

  readonly user = this.session.asReadonly();
  readonly isAuthenticated = computed(() => !!this.session());
  readonly roles = computed(() => this.session()?.roles ?? []);
  /** Staff = anyone who works the fraud desk rather than a bank customer. */
  readonly isStaff = computed(() => this.roles().some((r) => r !== 'CUSTOMER'));

  constructor() {
    this.scheduleExpiry();
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  hasAnyRole(...roles: Role[]): boolean {
    return this.roles().some((r) => roles.includes(r));
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/v1/auth/login', { username, password })
      .pipe(tap((res) => this.store(res)));
  }

  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/register', body).pipe(tap((res) => this.store(res)));
  }

  logout(reason?: 'expired'): void {
    this.session.set(null);
    clearTimeout(this.expiryTimer);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable */
    }
    this.router.navigate(['/sign-in'], reason ? { queryParams: { reason } } : {});
  }

  homeRoute(): string {
    return this.isStaff() ? '/overview' : '/my-money';
  }

  private store(res: AuthResponse): void {
    const session: Session = {
      token: res.token,
      subject: res.subject,
      roles: res.roles,
      customerId: res.customerId,
      expiresAt: Date.now() + res.expiresInSeconds * 1000,
    };
    this.session.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      /* storage unavailable: session lives for this tab only */
    }
    this.scheduleExpiry();
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as Session;
      return s.expiresAt > Date.now() + 5_000 ? s : null;
    } catch {
      return null;
    }
  }

  private scheduleExpiry(): void {
    clearTimeout(this.expiryTimer);
    const s = this.session();
    if (!s) return;
    this.expiryTimer = setTimeout(() => this.logout('expired'), Math.max(0, s.expiresAt - Date.now()));
  }
}
