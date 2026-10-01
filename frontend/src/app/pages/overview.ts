import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Api } from '../core/api.service';
import { AgoPipe, LOCALE, MoneyPipe } from '../core/format';
import { Alert, FraudEvent, FraudRule, ScoredTransaction } from '../core/models';
import { toastError } from '../core/notify';
import { ChartHost, cssVar } from '../shared/chart';
import { Icon } from '../shared/icon';
import { EmptyState } from '../shared/marks';
import { TransactionDrawer } from '../shared/transaction-drawer';
import { VerdictRail } from '../shared/verdict-rail';

@Component({
  selector: 'fd-overview',
  imports: [VerdictRail, ChartHost, EmptyState, TransactionDrawer, RouterLink, Icon, MoneyPipe, AgoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Overview</h1>
          <p>The last {{ txs().length }} scored transactions, and what’s waiting for a decision.</p>
        </div>
        <div class="page-actions">
          <span class="live" [class.stale]="!live()">{{ live() ? 'Updating every 15 seconds' : 'Paused' }}</span>
          <button class="btn" type="button" (click)="live.set(!live())">{{ live() ? 'Pause' : 'Resume' }}</button>
        </div>
      </header>

      <section class="panel hero">
        <div class="hero-head">
          <div class="verdicts">
            <div>
              <strong>{{ counts().ALLOW }}</strong>
              <span>allowed</span>
            </div>
            <div class="review">
              <strong>{{ counts().REVIEW }}</strong>
              <span>sent to review</span>
            </div>
            <div class="block">
              <strong>{{ counts().BLOCK }}</strong>
              <span>blocked</span>
            </div>
          </div>
          <p class="rate">
            @if (txs().length) {
              {{ interventionRate() }}% needed intervention.
              <span class="muted">Blocked value {{ blockedValue() | money: 'EUR' }}.</span>
            }
          </p>
        </div>
        @if (loaded() && !txs().length) {
          <fd-empty title="No transactions scored yet" text="Once transactions flow through the gateway they appear here on the score scale.">
            <a class="btn btn-primary" routerLink="/transactions" [queryParams]="{ new: 1 }">Send a test transaction</a>
          </fd-empty>
        } @else {
          <fd-verdict-rail [items]="txs()" (open)="openTx.set($event)" />
        }
      </section>

      <div class="split">
        <section class="panel queue">
          <div class="panel-head">
            <div>
              <h2>Waiting for a decision</h2>
              <p>{{ openTotal() }} open, highest score first</p>
            </div>
            <a class="btn btn-sm" routerLink="/alerts">Open alert queue</a>
          </div>
          @if (queue().length) {
            <ul>
              @for (a of queue(); track a.id) {
                <li>
                  <a [routerLink]="['/alerts']" [queryParams]="{ id: a.id }">
                    <span class="score" [attr.data-sev]="a.severity">{{ a.score }}</span>
                    <span class="what">
                      <strong>{{ a.primaryReason || a.title }}</strong>
                      <small>Raised {{ a.createdAt | ago }}</small>
                    </span>
                    <fd-icon name="chevron" />
                  </a>
                </li>
              }
            </ul>
          } @else if (loaded()) {
            <fd-empty title="Queue is clear" text="No open alerts. New HIGH and CRITICAL verdicts land here automatically." />
          }
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <h2>What’s triggering</h2>
              <p>Rules that fired across recent fraud verdicts</p>
            </div>
            <a class="btn btn-sm" routerLink="/rules">Tune rules</a>
          </div>
          <div class="panel-body">
            @if (ruleHits().length) {
              <ol class="hits">
                @for (r of ruleHits(); track r.code) {
                  <li>
                    <span class="name">{{ r.name }}</span>
                    <span class="bar"><i [style.width.%]="(r.count / ruleHits()[0].count) * 100"></i></span>
                    <span class="n">{{ r.count }}</span>
                  </li>
                }
              </ol>
            } @else if (loaded()) {
              <p class="muted">No rules have fired yet.</p>
            }
          </div>
        </section>
      </div>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>Verdicts by hour</h2>
            <p>Last 24 hours</p>
          </div>
        </div>
        <div class="panel-body">
          <fd-chart [config]="hourly()" label="Transactions per hour, stacked by decision" />
        </div>
      </section>
    </div>

    @if (openTx(); as id) {
      <fd-transaction-drawer [transactionId]="id" (close)="openTx.set(null)" />
    }
  `,
  styles: `
    .live {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      color: var(--muted);
      font-size: var(--t-sm);
      margin-right: 4px;
    }
    .live::before {
      content: '';
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--engrave);
      animation: pulse 2s ease-in-out infinite;
    }
    .live.stale::before {
      background: var(--muted);
      animation: none;
    }
    @keyframes pulse {
      50% { opacity: 0.35; }
    }

    .hero {
      padding: 24px;
      display: grid;
      gap: 20px;
    }
    .hero-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      gap: 16px;
      flex-wrap: wrap;
    }
    .verdicts {
      display: flex;
      gap: 40px;
      flex-wrap: wrap;
    }
    .verdicts div {
      display: grid;
    }
    .verdicts strong {
      font-size: var(--t-3xl);
      font-weight: 700;
      letter-spacing: -0.04em;
      line-height: 1;
      color: var(--engrave);
    }
    .verdicts .review strong { color: var(--sev-high); }
    .verdicts .block strong { color: var(--sev-critical); }
    .verdicts span {
      color: var(--muted);
      margin-top: 4px;
    }
    .rate {
      max-width: 40ch;
      text-align: right;
    }

    .split {
      display: grid;
      grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
      gap: 20px;
    }
    @media (max-width: 1080px) {
      .split { grid-template-columns: 1fr; }
      .rate { text-align: left; }
    }

    .queue ul {
      list-style: none;
      margin: 0;
      padding: 6px;
    }
    .queue li a {
      display: grid;
      grid-template-columns: 44px 1fr 18px;
      gap: 14px;
      align-items: center;
      padding: 10px 12px;
      border-radius: var(--r-md);
      color: inherit;
      text-decoration: none;
    }
    .queue li a:hover {
      background: var(--panel-2);
    }
    .queue fd-icon {
      color: var(--muted);
    }
    .score {
      display: grid;
      place-items: center;
      height: 36px;
      border-radius: var(--r-md);
      font-weight: 700;
      color: #fff;
      background: var(--sev-high);
    }
    .score[data-sev='CRITICAL'] { background: var(--sev-critical); }
    .score[data-sev='MEDIUM'] { background: var(--sev-medium); }
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
    .what small {
      color: var(--muted);
    }

    .hits {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 12px;
    }
    .hits li {
      display: grid;
      grid-template-columns: minmax(120px, 1fr) 1.2fr 32px;
      gap: 12px;
      align-items: center;
    }
    .name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .bar {
      height: 8px;
      background: var(--panel-2);
      border-radius: 99px;
      overflow: hidden;
    }
    .bar i {
      display: block;
      height: 100%;
      background: var(--engrave);
      border-radius: 99px;
    }
    .n {
      text-align: right;
      font-weight: 600;
    }
  `,
})
export class Overview {
  private readonly api = inject(Api);

  protected readonly txs = signal<ScoredTransaction[]>([]);
  protected readonly events = signal<FraudEvent[]>([]);
  protected readonly rules = signal<FraudRule[]>([]);
  protected readonly queue = signal<Alert[]>([]);
  protected readonly openTotal = signal(0);
  protected readonly loaded = signal(false);
  protected readonly live = signal(true);
  protected readonly openTx = signal<string | null>(null);

  protected readonly counts = computed(() => {
    const c = { ALLOW: 0, REVIEW: 0, BLOCK: 0 };
    for (const t of this.txs()) c[t.decision] = (c[t.decision] ?? 0) + 1;
    return c;
  });
  protected readonly interventionRate = computed(() => {
    const n = this.txs().length;
    return n ? Math.round(((this.counts().REVIEW + this.counts().BLOCK) / n) * 100) : 0;
  });
  protected readonly blockedValue = computed(() =>
    this.txs()
      .filter((t) => t.decision === 'BLOCK')
      .reduce((s, t) => s + t.amount, 0),
  );

  protected readonly ruleHits = computed(() => {
    const counts = new Map<string, number>();
    for (const e of this.events()) for (const c of e.triggeredRuleCodes ?? []) counts.set(c, (counts.get(c) ?? 0) + 1);
    return [...counts.entries()]
      .map(([code, count]) => ({ code, count, name: this.rules().find((r) => r.code === code)?.name ?? code }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 7);
  });

  protected readonly hourly = computed(() => {
    const txs = this.txs();
    return (): ChartConfiguration => {
      const now = new Date();
      now.setMinutes(0, 0, 0);
      const start = now.getTime() - 23 * 3600_000;
      const labels: string[] = [];
      const buckets = { ALLOW: Array(24).fill(0), REVIEW: Array(24).fill(0), BLOCK: Array(24).fill(0) };
      for (let i = 0; i < 24; i++) {
        labels.push(new Date(start + i * 3600_000).toLocaleTimeString(LOCALE, { hour: '2-digit', minute: '2-digit' }));
      }
      for (const t of txs) {
        const i = Math.floor((new Date(t.occurredAt).getTime() - start) / 3600_000);
        if (i >= 0 && i < 24) buckets[t.decision][i]++;
      }
      const ds = (label: string, data: number[], color: string) => ({
        label,
        data,
        backgroundColor: color,
        borderRadius: 3,
        maxBarThickness: 22,
      });
      return {
        type: 'bar',
        data: {
          labels,
          datasets: [
            ds('Allowed', buckets.ALLOW, cssVar('--engrave')),
            ds('Review', buckets.REVIEW, cssVar('--sev-high')),
            ds('Blocked', buckets.BLOCK, cssVar('--sev-critical')),
          ],
        },
        options: {
          maintainAspectRatio: false,
          animation: false,
          plugins: {
            legend: { position: 'bottom', align: 'start', labels: { boxWidth: 10, boxHeight: 10, useBorderRadius: true, borderRadius: 2 } },
            tooltip: { mode: 'index', intersect: false },
          },
          scales: {
            x: { stacked: true, grid: { display: false } },
            y: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { color: cssVar('--rule') }, border: { display: false } },
          },
        },
      };
    };
  });

  constructor() {
    this.refresh(true);
    const timer = setInterval(() => this.live() && this.refresh(false), 15_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  private refresh(first: boolean): void {
    forkJoin({
      txs: this.api.searchTransactions({ size: 200 }),
      events: this.api.searchFraudEvents({ size: 200 }).pipe(catchError(() => of(null))),
      rules: first ? this.api.rules().pipe(catchError(() => of(null))) : of(null),
      open: this.api.alerts({ status: 'OPEN', size: 100 }),
    }).subscribe({
      next: ({ txs, events, rules, open }) => {
        this.txs.set(txs.items);
        if (events) this.events.set(events.items);
        if (rules) this.rules.set(rules);
        this.openTotal.set(open.totalElements);
        this.queue.set([...open.content].sort((a, b) => b.score - a.score).slice(0, 6));
        this.loaded.set(true);
      },
      error: (err) => {
        this.loaded.set(true);
        if (first) toastError(err);
      },
    });
  }
}
