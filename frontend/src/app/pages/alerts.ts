import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AgoPipe, HumanizePipe, MoneyPipe, TonePipe } from '../core/format';
import { Account, Alert, AlertStatus, NotificationRecord, Severity } from '../core/models';
import { confirmAction, promptResolution, toast, toastError } from '../core/notify';
import { Icon } from '../shared/icon';
import { CopyId, EmptyState, Pager, ScoreScale, SeverityMark } from '../shared/marks';
import { TransactionDrawer } from '../shared/transaction-drawer';

const PAGE_SIZE = 30;

@Component({
  selector: 'fd-alerts',
  imports: [Icon, ScoreScale, SeverityMark, CopyId, EmptyState, Pager, TransactionDrawer, RouterLink, AgoPipe, HumanizePipe, MoneyPipe, TonePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Alerts</h1>
          <p>HIGH and CRITICAL verdicts become cases. Acknowledge one to take it, then resolve it with an outcome.</p>
        </div>
        <div class="page-actions">
          <button class="btn" type="button" (click)="refresh()"><fd-icon name="refresh" /> Refresh</button>
        </div>
      </header>

      <div class="workspace">
        <section class="panel list">
          <div class="toolbar">
            <div class="segmented" role="group" aria-label="Alert status">
              @for (s of statuses; track s.value) {
                <button type="button" [attr.aria-pressed]="status() === s.value" (click)="setStatus(s.value)">
                  {{ s.label }}
                </button>
              }
            </div>
            <div class="segmented" role="group" aria-label="Severity">
              <button type="button" [attr.aria-pressed]="severity() === ''" (click)="setSeverity('')">Any</button>
              <button type="button" [attr.aria-pressed]="severity() === 'CRITICAL'" (click)="setSeverity('CRITICAL')">Critical</button>
              <button type="button" [attr.aria-pressed]="severity() === 'HIGH'" (click)="setSeverity('HIGH')">High</button>
            </div>
          </div>

          @if (alerts().length) {
            <ul role="listbox" aria-label="Alerts">
              @for (a of alerts(); track a.id) {
                <li>
                  <button
                    type="button"
                    role="option"
                    [attr.aria-selected]="selected()?.id === a.id"
                    [class.active]="selected()?.id === a.id"
                    (click)="select(a)"
                  >
                    <span class="score" [attr.data-sev]="a.severity">{{ a.score }}</span>
                    <span class="what">
                      <strong>{{ a.primaryReason || a.title }}</strong>
                      <small>{{ a.status | humanize }}{{ a.assignedTo ? ', ' + a.assignedTo : '' }}, {{ a.createdAt | ago }}</small>
                    </span>
                  </button>
                </li>
              }
            </ul>
            <fd-pager [page]="page()" [size]="pageSize" [total]="total()" (pageChange)="page.set($event)" />
          } @else if (loaded()) {
            <fd-empty
              [title]="status() === 'OPEN' ? 'No open alerts' : 'Nothing here'"
              [text]="status() === 'OPEN' ? 'Every HIGH and CRITICAL verdict has been picked up.' : 'No alerts match these filters.'"
            />
          }
        </section>

        <section class="panel detail" aria-live="polite">
          @if (selected(); as a) {
            <header class="d-head">
              <div class="d-title">
                <fd-sev [value]="a.severity" />
                <span [class]="a.status | tone">{{ a.status | humanize }}</span>
              </div>
              <h2>{{ a.primaryReason || a.title }}</h2>
              <p class="muted">{{ a.title }}. Raised {{ a.createdAt | ago }}.</p>
            </header>

            <div class="d-body">
              <div class="d-score">
                <fd-score [score]="a.score" />
              </div>

              @if (canWork()) {
                <div class="actions">
                  @if (a.status === 'OPEN') {
                    <button class="btn btn-primary" type="button" (click)="acknowledge(a)" [disabled]="busy()">
                      <fd-icon name="check" /> Take this case
                    </button>
                  }
                  @if (a.status !== 'RESOLVED') {
                    <button class="btn" [class.btn-primary]="a.status === 'ACKNOWLEDGED'" type="button" (click)="resolve(a)" [disabled]="busy()">
                      Resolve alert
                    </button>
                  }
                  <button class="btn" type="button" (click)="openTx.set(a.transactionId)">See why it scored {{ a.score }}</button>
                </div>
              } @else {
                <div class="actions">
                  <button class="btn" type="button" (click)="openTx.set(a.transactionId)">See why it scored {{ a.score }}</button>
                  <p class="muted small">Only investigators and admins can take or resolve cases.</p>
                </div>
              }

              @if (a.status === 'RESOLVED') {
                <div class="outcome" [attr.data-r]="a.resolution">
                  <strong>{{ a.resolution | humanize }}</strong>
                  <span>by {{ a.resolvedBy }}, {{ a.resolvedAt | ago }}</span>
                  @if (a.resolutionNotes) {
                    <p>{{ a.resolutionNotes }}</p>
                  }
                </div>
              }

              <dl class="facts">
                <dt>Customer</dt>
                <dd><a [routerLink]="['/customers', a.customerId]"><fd-id [value]="a.customerId" label="Customer ID" /></a></dd>
                <dt>Transaction</dt>
                <dd><fd-id [value]="a.transactionId" label="Transaction ID" /></dd>
                <dt>Assigned to</dt>
                <dd>{{ a.assignedTo || 'Nobody yet' }}</dd>
                <dt>Correlation</dt>
                <dd><fd-id [value]="a.correlationId" label="Correlation ID" /></dd>
              </dl>

              @if (account(); as acc) {
                <div class="account">
                  <div>
                    <h3>Account {{ acc.accountNumber }}</h3>
                    <p class="muted">{{ acc.type | humanize }}, {{ acc.balance | money: acc.currency }}, <span [class]="acc.status | tone">{{ acc.status | humanize }}</span></p>
                  </div>
                  @if (canFreeze()) {
                    @if (acc.status === 'ACTIVE') {
                      <button class="btn btn-danger btn-sm" type="button" (click)="setFrozen(acc, true)"><fd-icon name="lock" /> Freeze account</button>
                    } @else if (acc.status === 'FROZEN') {
                      <button class="btn btn-sm" type="button" (click)="setFrozen(acc, false)"><fd-icon name="unlock" /> Unfreeze</button>
                    }
                  }
                </div>
              }

              @if (notifications().length) {
                <div>
                  <h3>Customer notified</h3>
                  <ul class="notes">
                    @for (n of notifications(); track n.id) {
                      <li>
                        <span class="channel">{{ n.channel | humanize }}</span>
                        <span class="muted">{{ n.recipient }}</span>
                        <span [class]="n.status | tone">{{ n.status | humanize }}</span>
                      </li>
                    }
                  </ul>
                </div>
              }
            </div>
          } @else {
            <fd-empty title="Choose an alert" text="Select an alert on the left to see the score, the account and who was notified." />
          }
        </section>
      </div>
    </div>

    @if (openTx(); as id) {
      <fd-transaction-drawer [transactionId]="id" (close)="openTx.set(null)" />
    }
  `,
  styles: `
    .workspace {
      display: grid;
      grid-template-columns: minmax(300px, 420px) minmax(0, 1fr);
      gap: 20px;
      align-items: start;
    }
    @media (max-width: 1000px) {
      .workspace { grid-template-columns: 1fr; }
    }
    .list ul {
      list-style: none;
      margin: 0;
      padding: 6px;
      max-height: calc(100vh - 290px);
      overflow-y: auto;
    }
    .list li button {
      width: 100%;
      display: grid;
      grid-template-columns: 42px 1fr;
      gap: 12px;
      align-items: center;
      padding: 10px;
      border: 0;
      border-radius: var(--r-md);
      background: transparent;
      color: inherit;
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .list li button:hover {
      background: var(--panel-2);
    }
    .list li button.active {
      background: var(--engrave-soft);
      box-shadow: inset 3px 0 0 var(--engrave);
    }
    .score {
      display: grid;
      place-items: center;
      height: 34px;
      border-radius: var(--r-md);
      font-weight: 700;
      color: #fff;
      background: var(--sev-high);
    }
    .score[data-sev='CRITICAL'] { background: var(--sev-critical); }
    .what {
      display: grid;
      min-width: 0;
    }
    .what strong,
    .what small {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .what small { color: var(--muted); }

    .detail {
      position: sticky;
      top: 20px;
    }
    .d-head {
      padding: 22px 24px 18px;
      border-bottom: 1px solid var(--rule);
      display: grid;
      gap: 8px;
    }
    .d-title {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    .d-head h2 {
      font-size: var(--t-xl);
      letter-spacing: -0.02em;
      max-width: 40ch;
    }
    .d-body {
      padding: 22px 24px 26px;
      display: grid;
      gap: 24px;
    }
    .d-score {
      max-width: 420px;
    }
    .actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      align-items: center;
    }
    .small { font-size: var(--t-sm); }
    .outcome {
      display: grid;
      gap: 4px;
      padding: 14px 16px;
      border-radius: var(--r-md);
      background: var(--panel-2);
      border-left: 3px solid var(--muted);
    }
    .outcome[data-r='CONFIRMED_FRAUD'] { border-color: var(--sev-critical); }
    .outcome[data-r='FALSE_POSITIVE'] { border-color: var(--engrave); }
    .outcome span { color: var(--muted); font-size: var(--t-sm); }
    .outcome p { margin-top: 6px; color: var(--ink-2); }
    .facts {
      display: grid;
      grid-template-columns: 120px 1fr;
      gap: 10px 16px;
      margin: 0;
    }
    .facts dt { color: var(--muted); }
    .facts dd { margin: 0; }
    .account {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      padding: 14px 16px;
      border: 1px solid var(--rule);
      border-radius: var(--r-md);
    }
    .notes {
      list-style: none;
      margin: 10px 0 0;
      padding: 0;
      display: grid;
      gap: 6px;
    }
    .notes li {
      display: grid;
      grid-template-columns: 70px 1fr auto;
      gap: 12px;
      font-size: var(--t-sm);
    }
    .notes .muted {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .channel { font-weight: 600; }
  `,
})
export class Alerts {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly statuses: { value: AlertStatus; label: string }[] = [
    { value: 'OPEN', label: 'Open' },
    { value: 'ACKNOWLEDGED', label: 'In progress' },
    { value: 'RESOLVED', label: 'Resolved' },
  ];

  protected readonly status = signal<AlertStatus>('OPEN');
  protected readonly severity = signal<Severity | ''>('');
  protected readonly page = signal(0);
  private readonly reload = signal(0);
  protected readonly alerts = signal<Alert[]>([]);
  protected readonly total = signal(0);
  protected readonly loaded = signal(false);
  protected readonly selected = signal<Alert | null>(null);
  protected readonly account = signal<Account | null>(null);
  protected readonly notifications = signal<NotificationRecord[]>([]);
  protected readonly busy = signal(false);
  protected readonly openTx = signal<string | null>(null);

  protected readonly canWork = computed(() => this.auth.hasAnyRole('ADMIN', 'INVESTIGATOR'));
  protected readonly canFreeze = computed(() => this.auth.hasAnyRole('ADMIN', 'ANALYST', 'INVESTIGATOR'));

  constructor() {
    const deepLink = this.route.snapshot.queryParamMap.get('id');
    if (deepLink) {
      this.api.alert(deepLink).subscribe({
        next: (a) => {
          this.status.set(a.status);
          this.select(a);
        },
        error: toastError,
      });
    }

    effect(() => {
      const q = { status: this.status(), severity: this.severity(), page: this.page(), size: PAGE_SIZE };
      this.reload();
      this.api.alerts(q).subscribe({
        next: (p) => {
          const sorted = q.status === 'RESOLVED' ? p.content : [...p.content].sort((a, b) => b.score - a.score);
          this.alerts.set(sorted);
          this.total.set(p.totalElements);
          this.loaded.set(true);
          if (!this.selected() && sorted.length && window.innerWidth > 1000) this.select(sorted[0]);
        },
        error: (e) => {
          this.loaded.set(true);
          toastError(e);
        },
      });
    });
  }

  protected refresh(): void {
    this.reload.update((n) => n + 1);
  }
  protected setStatus(s: AlertStatus): void {
    this.status.set(s);
    this.page.set(0);
    this.selected.set(null);
  }
  protected setSeverity(s: Severity | ''): void {
    this.severity.set(s);
    this.page.set(0);
  }

  protected select(a: Alert): void {
    this.selected.set(a);
    this.account.set(null);
    this.notifications.set([]);
    this.router.navigate([], { queryParams: { id: a.id }, replaceUrl: true });
    forkJoin({
      customerAccounts: this.api.accounts(a.customerId).pipe(catchError(() => of(null))),
      notes: this.api.notifications({ alertId: a.id, size: 10 }).pipe(catchError(() => of(null))),
    }).subscribe(({ customerAccounts, notes }) => {
      if (this.selected()?.id !== a.id) return;
      this.account.set(customerAccounts?.content.find((x) => x.id === a.accountId) ?? null);
      this.notifications.set(notes?.content ?? []);
    });
  }

  protected acknowledge(a: Alert): void {
    this.busy.set(true);
    this.api.acknowledgeAlert(a.id).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.selected.set(updated);
        toast('Case taken', `Assigned to ${updated.assignedTo ?? 'you'}.`);
        this.refresh();
      },
      error: (e) => {
        this.busy.set(false);
        toastError(e);
      },
    });
  }

  protected async resolve(a: Alert): Promise<void> {
    const result = await promptResolution(a.primaryReason || a.title);
    if (!result) return;
    this.busy.set(true);
    this.api.resolveAlert(a.id, result.resolution, result.notes).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.selected.set(updated);
        toast('Alert resolved', `Marked as ${result.resolution.replace(/_/g, ' ').toLowerCase()}.`);
        this.refresh();
      },
      error: (e) => {
        this.busy.set(false);
        toastError(e);
      },
    });
  }

  protected async setFrozen(acc: Account, freeze: boolean): Promise<void> {
    const ok = await confirmAction(
      freeze
        ? {
            title: `Freeze account ${acc.accountNumber}?`,
            text: 'The account is marked frozen for the whole team. You can unfreeze it at any time.',
            confirm: 'Freeze account',
            danger: true,
          }
        : {
            title: `Unfreeze account ${acc.accountNumber}?`,
            text: 'The account returns to active.',
            confirm: 'Unfreeze account',
          },
    );
    if (!ok) return;
    this.api.setAccountStatus(acc.id, freeze ? 'FROZEN' : 'ACTIVE').subscribe({
      next: (updated) => {
        this.account.set(updated);
        toast(freeze ? 'Account frozen' : 'Account unfrozen');
      },
      error: toastError,
    });
  }
}
