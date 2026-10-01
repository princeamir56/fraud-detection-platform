import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, map } from 'rxjs';
import { Subject } from 'rxjs';
import { Api } from '../core/api.service';
import { AgoPipe, HumanizePipe, MoneyPipe } from '../core/format';
import { Decision, ScoredTransaction, Severity } from '../core/models';
import { toastError } from '../core/notify';
import { Icon } from '../shared/icon';
import { CopyId, EmptyState, Pager, ScoreScale, SeverityMark } from '../shared/marks';
import { TransactionComposer } from '../shared/transaction-composer';
import { TransactionDrawer } from '../shared/transaction-drawer';

const PAGE_SIZE = 25;

@Component({
  selector: 'fd-transactions',
  imports: [
    FormsModule,
    Icon,
    ScoreScale,
    SeverityMark,
    CopyId,
    EmptyState,
    Pager,
    TransactionDrawer,
    TransactionComposer,
    MoneyPipe,
    AgoPipe,
    HumanizePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Transactions</h1>
          <p>Every scored transaction, newest first. Select one to see which rules fired.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-primary" type="button" (click)="composing.set(true)">
            <fd-icon name="plus" /> Test a transaction
          </button>
        </div>
      </header>

      <section class="panel">
        <div class="toolbar">
          <label class="grow search">
            <fd-icon name="search" />
            <span class="sr-only">Search transactions</span>
            <input
              class="input"
              type="search"
              placeholder="Search by merchant category, country, type or merchant ID"
              [ngModel]="text()"
              (ngModelChange)="typed$.next($event)"
            />
          </label>
          <div class="segmented" role="group" aria-label="Decision">
            @for (d of decisions; track d.value) {
              <button type="button" [attr.aria-pressed]="decision() === d.value" (click)="setDecision(d.value)">
                {{ d.label }}
              </button>
            }
          </div>
          <label>
            <span class="sr-only">Severity</span>
            <select class="select" [ngModel]="severity()" (ngModelChange)="setSeverity($event)">
              <option value="">Any severity</option>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </label>
        </div>

        @if (rows().length) {
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th scope="col">Score</th>
                  <th scope="col">Severity</th>
                  <th scope="col" class="num">Amount</th>
                  <th scope="col">Type</th>
                  <th scope="col">Merchant</th>
                  <th scope="col">Country</th>
                  <th scope="col">Decision</th>
                  <th scope="col">Customer</th>
                  <th scope="col">When</th>
                </tr>
              </thead>
              <tbody>
                @for (t of rows(); track t.transactionId) {
                  <tr
                    class="clickable"
                    [class.selected]="openTx() === t.transactionId"
                    tabindex="0"
                    (click)="openTx.set(t.transactionId)"
                    (keydown.enter)="openTx.set(t.transactionId)"
                  >
                    <td><fd-score [score]="t.score" [compact]="true" /></td>
                    <td><fd-sev [value]="t.severity" /></td>
                    <td class="num"><strong>{{ t.amount | money: t.currency }}</strong></td>
                    <td>{{ t.type | humanize }}</td>
                    <td>{{ t.merchantCategory | humanize }}</td>
                    <td>{{ t.countryCode }}</td>
                    <td><span class="decision" [attr.data-d]="t.decision">{{ decisionLabel(t.decision) }}</span></td>
                    <td><fd-id [value]="t.customerId" label="Customer ID" /></td>
                    <td class="muted">{{ t.occurredAt | ago }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <fd-pager [page]="page()" [size]="pageSize" [total]="total()" (pageChange)="page.set($event)" />
        } @else if (!loading()) {
          @if (filtered()) {
            <fd-empty title="No transactions match" text="Try a different search term, or clear the decision and severity filters.">
              <button class="btn" type="button" (click)="clearFilters()">Clear filters</button>
            </fd-empty>
          } @else {
            <fd-empty title="No transactions yet" text="Send a test transaction to watch the rules and risk model score it.">
              <button class="btn btn-primary" type="button" (click)="composing.set(true)">Test a transaction</button>
            </fd-empty>
          }
        }
      </section>
    </div>

    @if (openTx(); as id) {
      <fd-transaction-drawer [transactionId]="id" (close)="openTx.set(null)" />
    }
    @if (composing()) {
      <fd-transaction-composer (close)="composing.set(false)" (scored)="onScored($event)" />
    }
  `,
  styles: `
    .search {
      position: relative;
      display: flex;
      align-items: center;
    }
    .search fd-icon {
      position: absolute;
      left: 10px;
      color: var(--muted);
      width: 16px;
      height: 16px;
    }
    .search .input {
      padding-left: 34px;
      width: 100%;
    }
    .decision {
      font-weight: 600;
      font-size: var(--t-sm);
    }
    .decision[data-d='ALLOW'] { color: var(--engrave); }
    .decision[data-d='REVIEW'] { color: var(--sev-high); }
    .decision[data-d='BLOCK'] { color: var(--sev-critical); }
  `,
})
export class Transactions {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly decisions: { value: Decision | ''; label: string }[] = [
    { value: '', label: 'All' },
    { value: 'ALLOW', label: 'Allowed' },
    { value: 'REVIEW', label: 'Review' },
    { value: 'BLOCK', label: 'Blocked' },
  ];

  protected readonly typed$ = new Subject<string>();
  protected readonly text = toSignal(this.typed$.pipe(debounceTime(300), map((s) => s.trim())), { initialValue: '' });
  protected readonly decision = signal<Decision | ''>('');
  protected readonly severity = signal<Severity | ''>('');
  protected readonly page = signal(0);
  private readonly reload = signal(0);
  protected readonly rows = signal<ScoredTransaction[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly openTx = signal<string | null>(null);
  protected readonly composing = signal(this.route.snapshot.queryParamMap.has('new'));
  protected readonly filtered = computed(() => !!(this.text() || this.decision() || this.severity()));

  constructor() {
    effect(() => {
      const q = {
        text: this.text(),
        decision: this.decision(),
        severity: this.severity(),
        page: this.page(),
        size: PAGE_SIZE,
        reload: this.reload(),
      };
      this.loading.set(true);
      const { reload: _, ...query } = q;
      this.api.searchTransactions(query).subscribe({
        next: (r) => {
          this.rows.set(r.items);
          this.total.set(r.total);
          this.loading.set(false);
        },
        error: (e) => {
          this.loading.set(false);
          toastError(e);
        },
      });
    });
    if (this.composing()) this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  protected setDecision(d: Decision | ''): void {
    this.decision.set(d);
    this.page.set(0);
  }
  protected setSeverity(s: Severity | ''): void {
    this.severity.set(s);
    this.page.set(0);
  }
  protected clearFilters(): void {
    this.typed$.next('');
    this.decision.set('');
    this.severity.set('');
    this.page.set(0);
  }
  protected decisionLabel(d: Decision): string {
    return { ALLOW: 'Allowed', REVIEW: 'Review', BLOCK: 'Blocked' }[d];
  }

  protected onScored(id: string | null): void {
    this.composing.set(false);
    this.page.set(0);
    this.reload.update((n) => n + 1);
    if (id) this.openTx.set(id);
  }
}
