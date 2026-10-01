import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom, timer } from 'rxjs';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { money } from '../core/format';
import { Account, Channel, Customer, Transaction, TransactionType } from '../core/models';
import { closeDialogs, describeError, showScoring, showVerdict, toastError } from '../core/notify';
import { Icon } from './icon';

interface Preset {
  label: string;
  hint: string;
  values: {
    amount: number;
    type: TransactionType;
    merchantCategory: string;
    merchantId: string;
    countryCode: string;
    city: string;
    latitude: number | null;
    longitude: number | null;
    deviceId: string;
    channel: Channel;
  };
}

const PRESETS: Preset[] = [
  {
    label: 'Everyday purchase',
    hint: 'Small, local, known device',
    values: { amount: 42.5, type: 'PURCHASE', merchantCategory: 'GROCERY', merchantId: 'merch-grocery-01', countryCode: 'FR', city: 'Paris', latitude: 48.8566, longitude: 2.3522, deviceId: 'device-laptop-01', channel: 'WEB' },
  },
  {
    label: 'Large electronics',
    hint: 'Big amount on a new phone',
    values: { amount: 12000, type: 'PURCHASE', merchantCategory: 'ELECTRONICS', merchantId: 'merch-electro-77', countryCode: 'FR', city: 'Paris', latitude: 48.8566, longitude: 2.3522, deviceId: `device-new-${Date.now() % 1000}`, channel: 'MOBILE' },
  },
  {
    label: 'Crypto cash-out abroad',
    hint: 'High-risk country, impossible travel',
    values: { amount: 25000, type: 'WITHDRAWAL', merchantCategory: 'CRYPTO', merchantId: 'merch-crypto-9', countryCode: 'NG', city: 'Lagos', latitude: 6.5244, longitude: 3.3792, deviceId: `device-unknown-${Date.now() % 1000}`, channel: 'API' },
  },
];

/**
 * Side sheet for submitting a transaction through the real gateway, then waiting for the
 * asynchronous verdict and announcing it with SweetAlert.
 */
@Component({
  selector: 'fd-transaction-composer',
  imports: [ReactiveFormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'close.emit()' },
  template: `
    <div class="scrim" (click)="close.emit()"></div>
    <aside class="sheet" role="dialog" aria-modal="true" aria-labelledby="composer-title">
      <header>
        <div>
          <h2 id="composer-title">{{ fixedCustomerId() ? 'Make a payment' : 'Test a transaction' }}</h2>
          <p class="muted">
            {{ fixedCustomerId()
              ? 'Payments are checked for fraud before they complete.'
              : 'Sent through the gateway like real traffic, so it is scored and can raise alerts.' }}
          </p>
        </div>
        <button class="btn btn-ghost btn-icon" type="button" (click)="close.emit()" aria-label="Close">
          <fd-icon name="close" />
        </button>
      </header>

      <form class="body" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        @if (!fixedCustomerId()) {
          <fieldset>
            <legend>Start from a scenario</legend>
            <div class="presets">
              @for (p of presets; track p.label) {
                <button type="button" class="preset" (click)="apply(p)">
                  <strong>{{ p.label }}</strong>
                  <small>{{ p.hint }}</small>
                </button>
              }
            </div>
          </fieldset>

          <fieldset>
            <legend>Who</legend>
            <label class="field">
              <span>Customer</span>
              <select class="select" formControlName="customerId" (change)="loadAccounts()">
                <option value="" disabled>Choose a customer</option>
                @for (c of customers(); track c.id) {
                  <option [value]="c.id">{{ c.firstName }} {{ c.lastName }} ({{ c.email }})</option>
                }
              </select>
              @if (!customers().length) {
                <small>No customers yet. Customers are created when someone registers.</small>
              }
            </label>
          </fieldset>
        }

        <fieldset>
          <legend>{{ fixedCustomerId() ? 'From' : 'Account' }}</legend>
          <label class="field">
            <span>Account</span>
            <select class="select" formControlName="accountId">
              <option value="" disabled>{{ accounts().length ? 'Choose an account' : 'No accounts for this customer' }}</option>
              @for (a of accounts(); track a.id) {
                <option [value]="a.id" [disabled]="a.status !== 'ACTIVE'">
                  {{ a.type }} {{ a.accountNumber }}, {{ balance(a) }}{{ a.status !== 'ACTIVE' ? ' (' + a.status.toLowerCase() + ')' : '' }}
                </option>
              }
            </select>
          </label>
        </fieldset>

        <fieldset>
          <legend>Payment</legend>
          <div class="grid">
            <label class="field">
              <span>Amount</span>
              <input class="input" type="number" min="0.01" step="0.01" formControlName="amount" inputmode="decimal" />
            </label>
            <label class="field">
              <span>Type</span>
              <select class="select" formControlName="type">
                @for (t of types; track t) {
                  <option [value]="t">{{ t.charAt(0) + t.slice(1).toLowerCase() }}</option>
                }
              </select>
            </label>
            <label class="field">
              <span>Merchant category</span>
              <input class="input" formControlName="merchantCategory" placeholder="GROCERY" />
            </label>
            <label class="field">
              <span>Channel</span>
              <select class="select" formControlName="channel">
                @for (c of channels; track c) {
                  <option [value]="c">{{ c }}</option>
                }
              </select>
            </label>
          </div>
        </fieldset>

        @if (!fixedCustomerId()) {
        <fieldset>
          <legend>Where and how</legend>
          <div class="grid">
            <label class="field">
              <span>Country</span>
              <input class="input" formControlName="countryCode" maxlength="2" placeholder="FR" />
            </label>
            <label class="field">
              <span>City</span>
              <input class="input" formControlName="city" placeholder="Paris" />
            </label>
            <label class="field">
              <span>Device ID</span>
              <input class="input" formControlName="deviceId" placeholder="device-laptop-01" />
              <small>A device the customer hasn’t used before adds risk.</small>
            </label>
            <label class="field">
              <span>Merchant ID</span>
              <input class="input" formControlName="merchantId" />
            </label>
          </div>
        </fieldset>
        }

        <footer>
          <button class="btn" type="button" (click)="close.emit()">Cancel</button>
          <button class="btn btn-primary" type="submit" [disabled]="busy()">
            <fd-icon name="send" /> {{ fixedCustomerId() ? 'Send payment' : 'Send for scoring' }}
          </button>
        </footer>
      </form>
    </aside>
  `,
  styles: `
    :host {
      position: fixed;
      inset: 0;
      z-index: 50;
      display: flex;
      justify-content: flex-end;
    }
    .scrim {
      position: absolute;
      inset: 0;
      background: rgb(15 25 21 / 0.35);
    }
    .sheet {
      position: relative;
      width: min(560px, 100vw);
      height: 100%;
      background: var(--panel);
      border-left: 1px solid var(--rule);
      box-shadow: var(--shadow-pop);
      display: flex;
      flex-direction: column;
      animation: slide 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
    }
    @keyframes slide {
      from { transform: translateX(24px); opacity: 0; }
    }
    header {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      padding: 20px 24px 16px;
      border-bottom: 1px solid var(--rule);
    }
    header p {
      margin-top: 4px;
      font-size: var(--t-sm);
    }
    .body {
      overflow-y: auto;
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    fieldset {
      border: 0;
      border-bottom: 1px solid var(--rule);
      margin: 0;
      padding: 18px 24px 20px;
      display: grid;
      gap: 12px;
    }
    legend {
      float: left;
      width: 100%;
      font-weight: 650;
      font-size: var(--t-md);
      margin-bottom: 12px;
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 14px;
    }
    @media (max-width: 520px) {
      .grid { grid-template-columns: 1fr; }
    }
    .presets {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
    }
    @media (max-width: 520px) {
      .presets { grid-template-columns: 1fr; }
    }
    .preset {
      display: grid;
      gap: 2px;
      text-align: left;
      padding: 10px 12px;
      border-radius: var(--r-md);
      border: 1px solid var(--rule-strong);
      background: var(--panel);
      color: var(--ink);
      font: inherit;
      cursor: pointer;
    }
    .preset:hover {
      border-color: var(--engrave);
      background: var(--engrave-soft);
    }
    .preset small {
      color: var(--muted);
      font-size: var(--t-xs);
      line-height: 1.35;
    }
    footer {
      margin-top: auto;
      position: sticky;
      bottom: 0;
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding: 14px 24px;
      background: var(--panel);
      border-top: 1px solid var(--rule);
    }
  `,
})
export class TransactionComposer {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /** When set (customer portal), the customer can only pay from their own accounts. */
  readonly fixedCustomerId = input<string | null>(null);
  readonly close = output<void>();
  readonly scored = output<string | null>();

  protected readonly presets = PRESETS;
  protected readonly types: TransactionType[] = ['PURCHASE', 'PAYMENT', 'TRANSFER', 'WITHDRAWAL', 'DEPOSIT', 'REFUND'];
  protected readonly channels: Channel[] = ['WEB', 'MOBILE', 'POS', 'ATM', 'API'];
  protected readonly customers = signal<Customer[]>([]);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly busy = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    customerId: ['', Validators.required],
    accountId: ['', Validators.required],
    amount: [42.5, [Validators.required, Validators.min(0.01)]],
    type: ['PURCHASE' as TransactionType, Validators.required],
    merchantCategory: ['GROCERY'],
    merchantId: ['merch-grocery-01'],
    countryCode: ['FR', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    city: ['Paris'],
    latitude: [48.8566 as number | null],
    longitude: [2.3522 as number | null],
    deviceId: ['device-laptop-01'],
    channel: ['WEB' as Channel],
  });

  constructor() {
    queueMicrotask(() => {
      const fixed = this.fixedCustomerId();
      if (fixed) {
        this.form.controls.customerId.setValue(fixed);
        this.loadAccounts();
      } else {
        this.api.customers({ size: 100 }).subscribe({
          next: (p) => this.customers.set(p.content),
          error: toastError,
        });
      }
    });
  }

  protected balance(a: Account): string {
    return money(a.balance, a.currency);
  }

  protected apply(p: Preset): void {
    this.form.patchValue(p.values);
  }

  protected loadAccounts(): void {
    const id = this.form.controls.customerId.value;
    this.form.controls.accountId.setValue('');
    if (!id) return;
    this.api.accounts(id).subscribe({
      next: (p) => {
        this.accounts.set(p.content);
        const first = p.content.find((a) => a.status === 'ACTIVE');
        if (first) this.form.controls.accountId.setValue(first.id);
      },
      error: toastError,
    });
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      toastError(new Error('Choose a customer and account, and enter an amount and a two-letter country.'));
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    showScoring();
    try {
      const created = await firstValueFrom(
        this.api.createTransaction(
          {
            accountId: v.accountId,
            customerId: v.customerId,
            amount: Number(v.amount),
            currency: this.accounts().find((a) => a.id === v.accountId)?.currency ?? 'EUR',
            type: v.type,
            merchantCategory: v.merchantCategory.trim().toUpperCase() || undefined,
            merchantId: v.merchantId.trim() || undefined,
            countryCode: v.countryCode.toUpperCase(),
            city: v.city.trim() || undefined,
            latitude: v.latitude ?? undefined,
            longitude: v.longitude ?? undefined,
            deviceId: v.deviceId.trim() || undefined,
            channel: v.channel,
          },
          crypto.randomUUID(),
        ),
      );
      const final = await this.waitForVerdict(created.id);
      const view = await showVerdict(this.verdictDialog(final));
      this.scored.emit(view && this.auth.isStaff() ? final.id : null);
    } catch (err) {
      closeDialogs();
      const { title, detail } = describeError(err);
      toastError(err);
      console.warn(title, detail);
    } finally {
      this.busy.set(false);
    }
  }

  private async waitForVerdict(id: string): Promise<Transaction> {
    let latest = await firstValueFrom(this.api.transaction(id));
    for (let i = 0; i < 20 && latest.status === 'PENDING'; i++) {
      await firstValueFrom(timer(i < 5 ? 400 : 1000));
      latest = await firstValueFrom(this.api.transaction(id));
    }
    return latest;
  }

  private verdictDialog(t: Transaction) {
    const amount = money(t.amount, t.currency);
    const staff = this.auth.isStaff();
    const viewLabel = staff ? 'See why' : 'View payment';
    if (t.status === 'PENDING') {
      return {
        title: 'Still scoring',
        html: `<p>${amount} was accepted but hasn’t been scored yet. It will update in the list shortly.</p>`,
        icon: 'info' as const,
        viewLabel,
      };
    }
    const score = `<p style="margin-top:8px"><strong style="font-size:1.6rem">${t.fraudScore ?? '–'}</strong> <span class="muted">/ 100, ${(t.severity ?? '').toLowerCase()} risk</span></p>`;
    switch (t.decision) {
      case 'BLOCK':
        return {
          title: staff ? 'Blocked' : 'Payment blocked',
          html: `<p>${amount} was stopped by the fraud engine.${staff ? ' An alert is open for review.' : ' Contact your bank if you made this payment.'}</p>${score}`,
          icon: 'error' as const,
          viewLabel,
        };
      case 'REVIEW':
        return {
          title: staff ? 'Sent to review' : 'Payment on hold',
          html: `<p>${amount} needs a manual check before it completes.</p>${score}`,
          icon: 'warning' as const,
          viewLabel,
        };
      default:
        return {
          title: staff ? 'Allowed' : 'Payment sent',
          html: `<p>${amount} passed the fraud checks and completed.</p>${score}`,
          icon: 'success' as const,
          viewLabel,
        };
    }
  }
}
