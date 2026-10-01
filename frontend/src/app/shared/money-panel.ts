import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AgoPipe, HumanizePipe, MoneyPipe, TonePipe, money } from '../core/format';
import { Account, AccountType, Transaction } from '../core/models';
import { confirmAction, promptAmount, toast, toastError } from '../core/notify';
import { Icon } from './icon';
import { EmptyState, Pager, ScoreScale, SeverityMark } from './marks';
import { TransactionComposer } from './transaction-composer';
import { TransactionDrawer } from './transaction-drawer';

const PAGE_SIZE = 15;

/**
 * Accounts and payment history for one customer. Used by staff on the customer page and by
 * customers on "My money", with actions trimmed to what each role may do.
 */
@Component({
  selector: 'fd-money-panel',
  imports: [ReactiveFormsModule, Icon, EmptyState, Pager, ScoreScale, SeverityMark, TransactionDrawer, TransactionComposer, MoneyPipe, AgoPipe, HumanizePipe, TonePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>Accounts</h2>
          <p>{{ accounts().length }} {{ accounts().length === 1 ? 'account' : 'accounts' }}, {{ totalBalance() }}</p>
        </div>
        <div class="page-actions">
          @if (canManage()) {
            <button class="btn btn-sm" type="button" (click)="opening.set(!opening())">
              <fd-icon name="plus" /> Open account
            </button>
          }
          @if (canPay() && accounts().length) {
            <button class="btn btn-sm btn-primary" type="button" (click)="composing.set(true)">
              <fd-icon name="send" /> {{ auth.isStaff() ? 'Test a transaction' : 'Make a payment' }}
            </button>
          }
        </div>
      </div>

      @if (opening()) {
        <form class="open-form" [formGroup]="openForm" (ngSubmit)="openAccount()">
          <label class="field">
            <span>Type</span>
            <select class="select" formControlName="type">
              @for (t of accountTypes; track t) {
                <option [value]="t">{{ t | humanize }}</option>
              }
            </select>
          </label>
          <label class="field">
            <span>Currency</span>
            <input class="input" formControlName="currency" maxlength="3" />
          </label>
          <label class="field">
            <span>Opening balance</span>
            <input class="input" type="number" min="0" step="0.01" formControlName="initialBalance" />
          </label>
          <div class="open-actions">
            <button class="btn" type="button" (click)="opening.set(false)">Cancel</button>
            <button class="btn btn-primary" type="submit">Open account</button>
          </div>
        </form>
      }

      @if (accounts().length) {
        <ul class="accounts">
          @for (a of accounts(); track a.id) {
            <li>
              <div class="acc-main">
                <span class="acc-type">{{ a.type | humanize }}</span>
                <span class="mono acc-no">{{ a.accountNumber }}</span>
              </div>
              <strong class="balance">{{ a.balance | money: a.currency }}</strong>
              <span [class]="a.status | tone">{{ a.status | humanize }}</span>
              @if (canManage()) {
                <div class="acc-actions">
                  <button class="btn btn-sm btn-ghost" type="button" (click)="credit(a)" [disabled]="a.status !== 'ACTIVE'">Add funds</button>
                  @if (a.status === 'ACTIVE') {
                    <button class="btn btn-sm btn-ghost" type="button" (click)="setStatus(a, 'FROZEN')"><fd-icon name="lock" /> Freeze</button>
                  } @else if (a.status === 'FROZEN') {
                    <button class="btn btn-sm btn-ghost" type="button" (click)="setStatus(a, 'ACTIVE')"><fd-icon name="unlock" /> Unfreeze</button>
                  }
                </div>
              }
            </li>
          }
        </ul>
      } @else if (loaded()) {
        <fd-empty title="No accounts yet" [text]="canManage() ? 'Open an account to start sending transactions.' : 'Your bank hasn’t opened an account for you yet.'" />
      }
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>{{ auth.isStaff() ? 'Transactions' : 'Payments' }}</h2>
          <p>Newest first</p>
        </div>
      </div>
      @if (txs().length) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th scope="col" class="num">Amount</th>
                <th scope="col">Type</th>
                <th scope="col">Merchant</th>
                <th scope="col">Where</th>
                <th scope="col">Status</th>
                @if (auth.isStaff()) {
                  <th scope="col">Score</th>
                  <th scope="col">Severity</th>
                }
                <th scope="col">When</th>
              </tr>
            </thead>
            <tbody>
              @for (t of txs(); track t.id) {
                <tr class="clickable" tabindex="0" (click)="openTx.set(t.id)" (keydown.enter)="openTx.set(t.id)">
                  <td class="num"><strong>{{ t.amount | money: t.currency }}</strong></td>
                  <td>{{ t.type | humanize }}</td>
                  <td>{{ t.merchantCategory | humanize }}</td>
                  <td>{{ t.city ? t.city + ', ' : '' }}{{ t.countryCode }}</td>
                  <td><span [class]="t.status | tone">{{ statusLabel(t) }}</span></td>
                  @if (auth.isStaff()) {
                    <td><fd-score [score]="t.fraudScore" [compact]="true" /></td>
                    <td><fd-sev [value]="t.severity" /></td>
                  }
                  <td class="muted">{{ t.createdAt | ago }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <fd-pager [page]="page()" [size]="pageSize" [total]="txTotal()" (pageChange)="page.set($event)" />
      } @else if (loaded()) {
        <fd-empty [title]="auth.isStaff() ? 'No transactions yet' : 'No payments yet'" text="Payments show up here as soon as they are submitted." />
      }
    </section>

    @if (openTx(); as id) {
      <fd-transaction-drawer [transactionId]="id" (close)="openTx.set(null)" />
    }
    @if (composing()) {
      <fd-transaction-composer [fixedCustomerId]="customerId()" (close)="composing.set(false)" (scored)="afterPayment($event)" />
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 20px;
    }
    .open-form {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
      gap: 14px;
      align-items: end;
      padding: 16px 20px;
      background: var(--panel-2);
      border-bottom: 1px solid var(--rule);
    }
    @media (max-width: 760px) {
      .open-form { grid-template-columns: 1fr; }
    }
    .open-actions {
      display: flex;
      gap: 8px;
    }
    .accounts {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .accounts li {
      display: grid;
      grid-template-columns: minmax(160px, 1fr) minmax(120px, auto) 90px auto;
      gap: 20px;
      align-items: center;
      padding: 14px 20px;
      border-bottom: 1px solid var(--rule);
    }
    .accounts li:last-child { border-bottom: 0; }
    @media (max-width: 760px) {
      .accounts li { grid-template-columns: 1fr auto; }
    }
    .acc-main { display: grid; }
    .acc-type { font-weight: 600; }
    .acc-no { color: var(--muted); font-size: var(--t-sm); }
    .balance {
      font-size: var(--t-lg);
      letter-spacing: -0.01em;
      text-align: right;
    }
    .acc-actions {
      display: flex;
      gap: 4px;
      justify-content: flex-end;
    }
  `,
})
export class MoneyPanel {
  private readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
  protected readonly auth = inject(AuthService);

  readonly customerId = input.required<string>();

  protected readonly pageSize = PAGE_SIZE;
  protected readonly accountTypes: AccountType[] = ['CHECKING', 'SAVINGS', 'WALLET', 'CREDIT'];
  protected readonly accounts = signal<Account[]>([]);
  protected readonly txs = signal<Transaction[]>([]);
  protected readonly txTotal = signal(0);
  protected readonly page = signal(0);
  private readonly reload = signal(0);
  protected readonly loaded = signal(false);
  protected readonly opening = signal(false);
  protected readonly composing = signal(false);
  protected readonly openTx = signal<string | null>(null);

  protected readonly canManage = computed(() => this.auth.hasAnyRole('ADMIN', 'ANALYST'));
  protected readonly canPay = computed(() => this.auth.hasAnyRole('ADMIN', 'ANALYST', 'CUSTOMER'));
  protected readonly totalBalance = computed(() => {
    const byCurrency = new Map<string, number>();
    for (const a of this.accounts()) byCurrency.set(a.currency, (byCurrency.get(a.currency) ?? 0) + Number(a.balance));
    if (!byCurrency.size) return 'no balance';
    return [...byCurrency.entries()]
      .map(([c, v]) => money(v, c))
      .join(' and ');
  });

  protected readonly openForm = this.fb.nonNullable.group({
    type: ['CHECKING' as AccountType],
    currency: ['EUR', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    initialBalance: [1000, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    effect(() => {
      const id = this.customerId();
      this.reload();
      this.api.accounts(id).subscribe({
        next: (p) => this.accounts.set(p.content),
        error: toastError,
      });
    });
    effect(() => {
      const id = this.customerId();
      const page = this.page();
      this.reload();
      this.api.transactionHistory({ customerId: id, page, size: PAGE_SIZE }).subscribe({
        next: (p) => {
          this.txs.set(p.content);
          this.txTotal.set(p.totalElements);
          this.loaded.set(true);
        },
        error: (e) => {
          this.loaded.set(true);
          toastError(e);
        },
      });
    });
  }

  protected statusLabel(t: Transaction): string {
    if (this.auth.isStaff()) return t.status.charAt(0) + t.status.slice(1).toLowerCase().replace(/_/g, ' ');
    return (
      { COMPLETED: 'Completed', PENDING: 'Processing', BLOCKED: 'Blocked', FLAGGED: 'On hold', UNDER_REVIEW: 'On hold', REJECTED: 'Declined', VALIDATED: 'Processing' } as Record<string, string>
    )[t.status];
  }

  protected openAccount(): void {
    if (this.openForm.invalid) {
      this.openForm.markAllAsTouched();
      return;
    }
    const v = this.openForm.getRawValue();
    this.api
      .createAccount({ customerId: this.customerId(), type: v.type, currency: v.currency.toUpperCase(), initialBalance: Number(v.initialBalance) })
      .subscribe({
        next: (a) => {
          this.opening.set(false);
          this.reload.update((n) => n + 1);
          toast('Account opened', `${a.accountNumber} with ${money(a.balance, a.currency)}.`);
        },
        error: toastError,
      });
  }

  protected async credit(a: Account): Promise<void> {
    const res = await promptAmount(`Add funds to ${a.accountNumber}`, 'The balance increases immediately.', a.currency);
    if (!res) return;
    this.api.creditAccount(a.id, res.amount, res.reason || 'Manual credit').subscribe({
      next: () => {
        this.reload.update((n) => n + 1);
        toast('Funds added');
      },
      error: toastError,
    });
  }

  protected async setStatus(a: Account, status: 'FROZEN' | 'ACTIVE'): Promise<void> {
    const freezing = status === 'FROZEN';
    const ok = await confirmAction({
      title: freezing ? `Freeze account ${a.accountNumber}?` : `Unfreeze account ${a.accountNumber}?`,
      text: freezing ? 'The account is marked frozen for the whole team until someone unfreezes it.' : 'The account returns to active.',
      confirm: freezing ? 'Freeze account' : 'Unfreeze account',
      danger: freezing,
    });
    if (!ok) return;
    this.api.setAccountStatus(a.id, status).subscribe({
      next: () => {
        this.reload.update((n) => n + 1);
        toast(freezing ? 'Account frozen' : 'Account unfrozen');
      },
      error: toastError,
    });
  }

  protected afterPayment(id: string | null): void {
    this.composing.set(false);
    this.page.set(0);
    this.reload.update((n) => n + 1);
    if (id) this.openTx.set(id);
  }
}
