import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AgoPipe, HumanizePipe, MoneyPipe, TonePipe } from '../core/format';
import { AuditEvent, FraudEvent, FraudRule, Transaction } from '../core/models';
import { describeError } from '../core/notify';
import { Icon } from './icon';
import { CopyId, ScoreScale, SeverityMark } from './marks';

/**
 * Side sheet explaining one verdict: the score on the scale, each rule that fired with its
 * weight, the model's opinion, and the event trail that produced it.
 */
@Component({
  selector: 'fd-transaction-drawer',
  imports: [Icon, ScoreScale, SeverityMark, CopyId, MoneyPipe, AgoPipe, HumanizePipe, TonePipe, RouterLink, LowerCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'close.emit()' },
  template: `
    <div class="scrim" (click)="close.emit()"></div>
    <aside class="sheet" role="dialog" aria-modal="true" aria-labelledby="tx-title">
      <header>
        <div>
          <h2 id="tx-title">
            @if (tx(); as t) {
              {{ t.amount | money: t.currency }} {{ t.type | humanize | lowercase }}
            } @else {
              Transaction
            }
          </h2>
          <fd-id [value]="transactionId()" [full]="true" label="Transaction ID" />
        </div>
        <button class="btn btn-ghost btn-icon" type="button" (click)="close.emit()" aria-label="Close">
          <fd-icon name="close" />
        </button>
      </header>

      @if (loading()) {
        <div class="body"><p class="muted">Loading the verdict…</p></div>
      } @else if (error()) {
        <div class="body"><p>{{ error() }}</p></div>
      } @else if (tx(); as t) {
        <div class="body">
          <section class="verdict">
            <fd-score [score]="t.fraudScore" />
            <div class="verdict-line">
              <fd-sev [value]="t.severity" />
              <span [class]="t.status | tone">{{ t.status | humanize }}</span>
            </div>
            @if (t.reason) {
              <p class="reason">{{ t.reason }}</p>
            } @else if (t.status === 'PENDING') {
              <p class="reason muted">Still being scored. This usually takes under a second.</p>
            }
          </section>

          <section>
            <h3>Why it scored {{ t.fraudScore ?? '–' }}</h3>
            @if (fraud(); as f) {
              @if (f.triggeredRuleCodes.length) {
                <ol class="rules">
                  @for (code of f.triggeredRuleCodes; track code) {
                    <li>
                      <div>
                        <strong>{{ ruleName(code) }}</strong>
                        <small>{{ ruleDescription(code) }}</small>
                      </div>
                      <span class="weight">+{{ ruleWeight(code) }}</span>
                    </li>
                  }
                </ol>
                @if (rawTotal() > 100) {
                  <p class="note">Rules add up to {{ rawTotal() }} points; the score is capped at 100.</p>
                }
              } @else {
                <p class="muted">No rules fired. The transaction matched this customer’s usual pattern.</p>
              }
              <dl class="model">
                <dt>Risk model</dt>
                <dd>
                  @if (f.modelRiskScore !== null) {
                    {{ (f.modelRiskScore * 100).toFixed(1) }}% likelihood
                    <span class="muted">{{ f.modelRiskScore >= 0.7 ? '(counts toward the score)' : '(below the 70% threshold)' }}</span>
                  } @else {
                    <span class="muted">Unavailable. The verdict used rules only.</span>
                  }
                </dd>
              </dl>
            } @else {
              @if (t.status === 'PENDING') {
                <p class="muted">Rules haven’t run yet.</p>
              } @else if (t.severity === 'LOW' || t.severity === 'MEDIUM') {
                <p class="muted">Rule-by-rule detail is only recorded for high and critical verdicts. This one stayed below 60, so it was allowed.</p>
              } @else {
                <p class="muted">The rule detail hasn’t been indexed yet. Close and reopen in a moment.</p>
              }
            }
          </section>

          <section>
            <h3>Details</h3>
            <dl class="grid">
              <dt>Where</dt><dd>{{ t.city ? t.city + ', ' : '' }}{{ t.countryCode }}</dd>
              <dt>Merchant</dt><dd>{{ t.merchantCategory | humanize }}@if (t.merchantId) { <span class="muted mono">{{ t.merchantId }}</span> }</dd>
              <dt>Channel</dt><dd>{{ t.channel | humanize }}</dd>
              <dt>Customer</dt>
              <dd>
                @if (auth.isStaff()) {
                  <a [routerLink]="['/customers', t.customerId]" (click)="close.emit()"><fd-id [value]="t.customerId" label="Customer ID" /></a>
                } @else {
                  <fd-id [value]="t.customerId" label="Customer ID" />
                }
              </dd>
              <dt>Account</dt><dd><fd-id [value]="t.accountId" label="Account ID" /></dd>
              <dt>Submitted</dt><dd>{{ t.createdAt | ago }}</dd>
            </dl>
          </section>

          @if (trail().length) {
            <section>
              <h3>Event trail</h3>
              <ol class="trail">
                @for (e of trail(); track e.eventId) {
                  <li>
                    <strong>{{ e.eventType }}</strong>
                    <span>{{ e.summary }}</span>
                    <time class="muted">{{ e.occurredAt | ago }}</time>
                  </li>
                }
              </ol>
            </section>
          }
        </div>
      }
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
      animation: fade 0.16s ease-out;
    }
    .sheet {
      position: relative;
      width: min(520px, 100vw);
      height: 100%;
      background: var(--panel);
      border-left: 1px solid var(--rule);
      box-shadow: var(--shadow-pop);
      display: flex;
      flex-direction: column;
      animation: slide 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
      padding: 20px 24px 16px;
      border-bottom: 1px solid var(--rule);
    }
    header h2 {
      margin-bottom: 4px;
    }
    .body {
      overflow-y: auto;
      padding: 8px 24px 32px;
    }
    section {
      padding: 20px 0;
      border-bottom: 1px solid var(--rule);
      display: grid;
      gap: 12px;
    }
    section:last-child {
      border-bottom: 0;
    }
    .verdict-line {
      display: flex;
      gap: 14px;
      align-items: center;
    }
    .reason {
      color: var(--ink-2);
    }
    .rules {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 2px;
    }
    .rules li {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      padding: 10px 12px;
      background: var(--panel-2);
      border-radius: var(--r-md);
    }
    .rules small {
      display: block;
      color: var(--muted);
      font-size: var(--t-sm);
      line-height: 1.4;
    }
    .weight {
      font-weight: 650;
      font-size: var(--t-md);
      color: var(--sev-high);
    }
    .note {
      font-size: var(--t-sm);
      color: var(--muted);
    }
    dl {
      margin: 0;
    }
    .grid {
      display: grid;
      grid-template-columns: 110px 1fr;
      gap: 8px 16px;
    }
    dt {
      color: var(--muted);
    }
    dd {
      margin: 0;
    }
    .model {
      display: flex;
      gap: 16px;
      padding-top: 4px;
    }
    .trail {
      list-style: none;
      margin: 0;
      padding: 0 0 0 16px;
      border-left: 2px solid var(--rule);
      display: grid;
      gap: 14px;
    }
    .trail li {
      position: relative;
      display: grid;
      gap: 2px;
    }
    .trail li::before {
      content: '';
      position: absolute;
      left: -22px;
      top: 6px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--panel);
      border: 2px solid var(--engrave);
    }
    .trail span {
      color: var(--ink-2);
      font-size: var(--t-sm);
    }
    .trail time {
      font-size: var(--t-xs);
    }
    @keyframes slide {
      from { transform: translateX(24px); opacity: 0; }
    }
    @keyframes fade {
      from { opacity: 0; }
    }
  `,
})
export class TransactionDrawer {
  private readonly api = inject(Api);
  protected readonly auth = inject(AuthService);
  readonly transactionId = input.required<string>();
  readonly close = output<void>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly tx = signal<Transaction | null>(null);
  protected readonly fraud = signal<FraudEvent | null>(null);
  protected readonly rules = signal<FraudRule[]>([]);
  protected readonly trail = signal<AuditEvent[]>([]);

  protected readonly rawTotal = computed(() =>
    (this.fraud()?.triggeredRuleCodes ?? []).reduce((sum, c) => sum + this.ruleWeight(c), 0),
  );

  constructor() {
    effect(() => this.load(this.transactionId()));
  }

  private load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.transaction(id).subscribe({
      next: (t) => {
        this.tx.set(t);
        this.loading.set(false);
        if (!this.auth.isStaff()) return;
        forkJoin({
          events: this.api.searchFraudEvents({ customerId: t.customerId, size: 200 }).pipe(catchError(() => of(null))),
          rules: this.api.rules().pipe(catchError(() => of([] as FraudRule[]))),
          trail: this.auth.hasAnyRole('ADMIN', 'INVESTIGATOR')
            ? this.api.auditEvents({ transactionId: id, size: 50 }).pipe(catchError(() => of(null)))
            : of(null),
        }).subscribe(({ events, rules, trail }) => {
          this.fraud.set(events?.items.find((e) => e.transactionId === id) ?? null);
          this.rules.set(rules);
          this.trail.set([...(trail?.items ?? [])].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt)));
        });
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(err.status === 404 ? 'This transaction no longer exists.' : describeError(err).title);
      },
    });
  }

  protected ruleName(code: string): string {
    return this.rules().find((r) => r.code === code)?.name ?? code.replace(/_/g, ' ').toLowerCase();
  }
  protected ruleDescription(code: string): string {
    return this.rules().find((r) => r.code === code)?.description ?? '';
  }
  protected ruleWeight(code: string): number {
    return this.rules().find((r) => r.code === code)?.weight ?? 0;
  }
}
