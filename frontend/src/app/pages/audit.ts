import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Api } from '../core/api.service';
import { AgoPipe, MoneyPipe } from '../core/format';
import { AuditEvent } from '../core/models';
import { toastError } from '../core/notify';
import { Icon } from '../shared/icon';
import { CopyId, EmptyState, Pager, SeverityMark } from '../shared/marks';
import { TransactionDrawer } from '../shared/transaction-drawer';

type Field = 'text' | 'correlationId' | 'transactionId' | 'customerId';
const PAGE_SIZE = 40;

@Component({
  selector: 'fd-audit',
  imports: [FormsModule, Icon, CopyId, EmptyState, Pager, SeverityMark, TransactionDrawer, AgoPipe, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Audit trail</h1>
          <p>Every domain event, append-only. Search by correlation ID to follow one request across every service.</p>
        </div>
      </header>

      <section class="panel">
        <form class="toolbar" (ngSubmit)="search(0)">
          <label>
            <span class="sr-only">Search in</span>
            <select class="select" [(ngModel)]="field" name="field">
              <option value="text">Summary or event type</option>
              <option value="correlationId">Correlation ID</option>
              <option value="transactionId">Transaction ID</option>
              <option value="customerId">Customer ID</option>
            </select>
          </label>
          <label class="grow">
            <span class="sr-only">Search term</span>
            <input class="input" type="search" [(ngModel)]="term" name="term" [placeholder]="placeholder()" />
          </label>
          <button class="btn btn-primary" type="submit"><fd-icon name="search" /> Search</button>
          @if (active()) {
            <button class="btn btn-ghost" type="button" (click)="clear()">Clear</button>
          }
        </form>

        @if (groups().length) {
          <div class="groups">
            @for (g of groups(); track g.correlationId) {
              <article class="group">
                <header>
                  <span class="muted small">Request</span>
                  <fd-id [value]="g.correlationId" label="Correlation ID" />
                  @if (g.transactionId; as tx) {
                    <button type="button" class="btn btn-sm" (click)="openTx.set(tx)">Open transaction</button>
                  }
                  <button type="button" class="btn btn-sm btn-ghost" (click)="followRequest(g.correlationId)">Show only this request</button>
                </header>
                <ol>
                  @for (e of g.events; track e.eventId) {
                    <li [attr.data-type]="kind(e.eventType)">
                      <div class="line">
                        <strong>{{ e.eventType }}</strong>
                        <span class="summary">{{ e.summary }}</span>
                      </div>
                      <div class="meta">
                        @if (e.amount !== null) { <span>{{ e.amount | money: e.currency }}</span> }
                        @if (e.severity) { <fd-sev [value]="e.severity" /> }
                        <time class="muted" [attr.datetime]="e.occurredAt" [title]="e.occurredAt">{{ e.occurredAt | ago }}</time>
                      </div>
                    </li>
                  }
                </ol>
              </article>
            }
          </div>
          <fd-pager [page]="page()" [size]="pageSize" [total]="total()" (pageChange)="search($event)" />
        } @else if (loaded()) {
          <fd-empty
            [title]="active() ? 'No events match' : 'No events recorded yet'"
            [text]="active() ? 'Check the ID, or search a different field.' : 'Events appear as soon as transactions are submitted.'"
          />
        }
      </section>
    </div>

    @if (openTx(); as id) {
      <fd-transaction-drawer [transactionId]="id" (close)="openTx.set(null)" />
    }
  `,
  styles: `
    .small { font-size: var(--t-sm); }
    .groups {
      display: grid;
    }
    .group {
      padding: 16px 20px 18px;
      border-bottom: 1px solid var(--rule);
    }
    .group > header {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }
    .group > header .btn:first-of-type { margin-left: auto; }
    ol {
      list-style: none;
      margin: 0;
      padding: 0 0 0 18px;
      border-left: 2px solid var(--rule);
      display: grid;
      gap: 12px;
    }
    li {
      position: relative;
      display: grid;
      gap: 4px;
    }
    li::before {
      content: '';
      position: absolute;
      left: -24px;
      top: 5px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--panel);
      border: 2px solid var(--muted);
    }
    li[data-type='fraud']::before { border-color: var(--sev-high); }
    li[data-type='alert']::before { border-color: var(--sev-critical); }
    li[data-type='ok']::before { border-color: var(--engrave); }
    .line {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      align-items: baseline;
    }
    .summary { color: var(--ink-2); }
    .meta {
      display: flex;
      gap: 14px;
      align-items: center;
      flex-wrap: wrap;
      font-size: var(--t-sm);
    }
  `,
})
export class Audit {
  private readonly api = inject(Api);
  protected readonly pageSize = PAGE_SIZE;

  protected field: Field = 'text';
  protected term = '';
  protected readonly items = signal<AuditEvent[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loaded = signal(false);
  protected readonly active = signal(false);
  protected readonly placeholder = signal('Search summaries, e.g. blocked');
  protected readonly openTx = signal<string | null>(null);

  /** Consecutive events that share a correlation id read as one request. */
  protected readonly groups = computed(() => {
    const out: { correlationId: string; transactionId: string | null; events: AuditEvent[] }[] = [];
    for (const e of this.items()) {
      const last = out[out.length - 1];
      if (last && last.correlationId === e.correlationId) last.events.push(e);
      else out.push({ correlationId: e.correlationId, transactionId: null, events: [e] });
    }
    for (const g of out) {
      g.events.sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
      g.transactionId = g.events.find((e) => e.transactionId)?.transactionId ?? null;
    }
    return out;
  });

  constructor() {
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    for (const f of ['correlationId', 'transactionId', 'customerId'] as Field[]) {
      const v = q.get(f);
      if (v) {
        this.field = f;
        this.term = v;
      }
    }
    this.search(0);
  }

  protected kind(type: string): string {
    if (type.startsWith('Alert')) return 'alert';
    if (type.startsWith('Fraud') || type === 'TransactionRejected') return 'fraud';
    if (type === 'TransactionCompleted') return 'ok';
    return '';
  }

  protected followRequest(id: string): void {
    this.field = 'correlationId';
    this.term = id;
    this.search(0);
  }

  protected clear(): void {
    this.term = '';
    this.field = 'text';
    this.search(0);
  }

  protected search(page: number): void {
    const term = this.term.trim();
    this.active.set(!!term);
    this.page.set(page);
    this.placeholder.set(
      { text: 'Search summaries, e.g. blocked', correlationId: 'Paste a correlation ID', transactionId: 'Paste a transaction ID', customerId: 'Paste a customer ID' }[this.field],
    );
    this.api.auditEvents({ [this.field]: term, page, size: PAGE_SIZE }).subscribe({
      next: (r) => {
        this.items.set(r.items);
        this.total.set(r.total);
        this.loaded.set(true);
      },
      error: (e) => {
        this.loaded.set(true);
        toastError(e);
      },
    });
  }
}
