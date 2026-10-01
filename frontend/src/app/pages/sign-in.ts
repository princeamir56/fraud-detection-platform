import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { describeError, toast } from '../core/notify';
import { Guilloche } from '../shared/guilloche';

@Component({
  selector: 'fd-sign-in',
  imports: [ReactiveFormsModule, Guilloche],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <section class="art" aria-hidden="true">
        <fd-guilloche class="rosette" [lines]="26" [lobes]="11" [strokeWidth]="0.28" />
        <div class="art-copy">
          <h2>Every transaction gets a verdict in under a second. You decide the ones that matter.</h2>
        </div>
      </section>

      <section class="form-side">
        <div class="form-box">
          <h1>{{ mode() === 'sign-in' ? 'Sign in to Fraud Desk' : 'Open a customer account' }}</h1>
          <p class="lede">
            {{ mode() === 'sign-in'
              ? 'Analysts and investigators work alerts here. Customers see their own money and payments.'
              : 'Create a login and customer profile. You can open accounts after signing in.' }}
          </p>

          @if (expired()) {
            <p class="notice" role="status">Your session ended. Sign in again to continue.</p>
          }

          @if (mode() === 'sign-in') {
            <form [formGroup]="signInForm" (ngSubmit)="signIn()" novalidate>
              <label class="field">
                <span>Username</span>
                <input class="input" formControlName="username" autocomplete="username" autofocus />
              </label>
              <label class="field">
                <span>Password</span>
                <input class="input" type="password" formControlName="password" autocomplete="current-password" />
              </label>
              @if (error()) {
                <p class="error" role="alert">{{ error() }}</p>
              }
              <button class="btn btn-primary submit" type="submit" [disabled]="busy()">
                {{ busy() ? 'Signing in…' : 'Sign in' }}
              </button>
            </form>
            <p class="switch">
              New customer?
              <button type="button" class="link" (click)="setMode('register')">Open an account</button>
            </p>
            <details class="demo">
              <summary>Using the local demo stack?</summary>
              <p>The seeded admin is <b class="mono">admin</b> with password <b class="mono">admin-change-me</b>.</p>
              <button type="button" class="btn btn-sm" (click)="fillDemo()">Fill in admin login</button>
            </details>
          } @else {
            <form [formGroup]="registerForm" (ngSubmit)="register()" novalidate>
              <div class="two">
                <label class="field"><span>First name</span><input class="input" formControlName="firstName" autocomplete="given-name" /></label>
                <label class="field"><span>Last name</span><input class="input" formControlName="lastName" autocomplete="family-name" /></label>
              </div>
              <label class="field"><span>Email</span><input class="input" type="email" formControlName="email" autocomplete="email" /></label>
              <div class="two">
                <label class="field"><span>Phone</span><input class="input" formControlName="phone" autocomplete="tel" placeholder="+33600000000" /></label>
                <label class="field">
                  <span>Country</span>
                  <input class="input" formControlName="countryCode" maxlength="2" placeholder="FR" autocomplete="country" />
                  <small>Two-letter code</small>
                </label>
              </div>
              <label class="field">
                <span>Username</span>
                <input class="input" formControlName="username" autocomplete="username" />
                <small>Letters, digits, dot, underscore or hyphen</small>
              </label>
              <label class="field">
                <span>Password</span>
                <input class="input" type="password" formControlName="password" autocomplete="new-password" />
                <small>At least 8 characters</small>
              </label>
              @if (error()) {
                <p class="error" role="alert">{{ error() }}</p>
              }
              <button class="btn btn-primary submit" type="submit" [disabled]="busy()">
                {{ busy() ? 'Creating account…' : 'Create account' }}
              </button>
            </form>
            <p class="switch">
              Already have a login?
              <button type="button" class="link" (click)="setMode('sign-in')">Sign in</button>
            </p>
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    .wrap {
      display: grid;
      grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
      min-height: 100vh;
    }
    .art {
      position: relative;
      overflow: hidden;
      background: var(--rail);
      color: var(--rail-ink);
      display: flex;
      align-items: flex-end;
      padding: 48px;
    }
    .rosette {
      position: absolute;
      width: 130%;
      aspect-ratio: 1;
      top: -32%;
      right: -44%;
      color: #8fc3ad;
      opacity: 0.55;
      animation: turn 240s linear infinite;
    }
    @keyframes turn {
      to { transform: rotate(360deg); }
    }
    .art-copy {
      position: relative;
      max-width: 30ch;
      display: grid;
      gap: 16px;
    }
    .art h2 {
      font-size: var(--t-2xl);
      font-weight: 600;
      letter-spacing: -0.02em;
      line-height: 1.15;
    }
    .form-side {
      display: grid;
      place-items: center;
      padding: 40px 24px;
    }
    .form-box {
      width: min(400px, 100%);
      display: grid;
      gap: 20px;
    }
    .lede {
      color: var(--muted);
      margin-top: -8px;
    }
    form {
      display: grid;
      gap: 14px;
    }
    .two {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .submit {
      height: 42px;
      margin-top: 6px;
    }
    .error {
      color: var(--sev-critical);
      font-size: var(--t-sm);
      white-space: pre-line;
    }
    .notice {
      padding: 10px 12px;
      border-radius: var(--r-md);
      background: var(--sev-medium-soft);
      color: var(--ink);
      font-size: var(--t-sm);
    }
    .switch {
      color: var(--muted);
    }
    .link {
      border: 0;
      background: none;
      padding: 0;
      color: var(--engrave);
      font: inherit;
      font-weight: 600;
      cursor: pointer;
      text-decoration: underline;
      text-underline-offset: 3px;
    }
    .demo {
      border-top: 1px solid var(--rule);
      padding-top: 16px;
      color: var(--muted);
      font-size: var(--t-sm);
    }
    .demo summary {
      cursor: pointer;
      color: var(--ink-2);
      font-weight: 550;
    }
    .demo p {
      margin: 10px 0;
    }
    .demo b {
      color: var(--ink);
    }
    @media (max-width: 860px) {
      .wrap {
        grid-template-columns: 1fr;
      }
      .art {
        min-height: 200px;
        padding: 28px 20px;
      }
      .art h2 {
        font-size: var(--t-lg);
      }
      .rosette {
        width: 90%;
        top: -50%;
        right: -30%;
      }
    }
  `,
})
export class SignIn {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly mode = signal<'sign-in' | 'register'>('sign-in');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expired = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('reason') === 'expired');

  protected readonly signInForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected readonly registerForm = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    countryCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.pattern(/^[A-Za-z0-9._-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected setMode(mode: 'sign-in' | 'register'): void {
    this.mode.set(mode);
    this.error.set(null);
  }

  protected fillDemo(): void {
    this.signInForm.setValue({ username: 'admin', password: 'admin-change-me' });
  }

  protected signIn(): void {
    if (this.signInForm.invalid) {
      this.error.set('Enter your username and password.');
      return;
    }
    const { username, password } = this.signInForm.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(username, password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homeRoute()),
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(err.status === 401 ? 'That username and password don’t match an account.' : describeError(err).title);
      },
    });
  }

  protected register(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      this.error.set('Fill in the required fields. Country is a two-letter code, password at least 8 characters.');
      return;
    }
    const v = this.registerForm.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth
      .register({ ...v, countryCode: v.countryCode.toUpperCase(), phone: v.phone || undefined })
      .subscribe({
        next: () => {
          toast('Account created', `Welcome, ${v.firstName}.`);
          this.router.navigateByUrl(this.auth.homeRoute());
        },
        error: (err) => {
          this.busy.set(false);
          const { title, detail } = describeError(err);
          this.error.set(detail ? `${title}\n${detail}` : title);
        },
      });
  }
}
