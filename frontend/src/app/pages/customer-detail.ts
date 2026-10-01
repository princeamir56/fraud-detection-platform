import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AgoPipe, HumanizePipe, TonePipe } from '../core/format';
import { Alert, Customer, CustomerStatus } from '../core/models';
import { confirmAction, toast, toastError } from '../core/notify';
import { CopyId } from '../shared/marks';
import { MoneyPanel } from '../shared/money-panel';

@Component({
  selector: 'fd-customer-detail',
  imports: [RouterLink, CopyId, MoneyPanel, AgoPipe, HumanizePipe, TonePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <nav class="crumbs" aria-label="Breadcrumb"><a routerLink="/customers">Customers</a></nav>

      @if (customer(); as c) {
        <header class="page-head">
          <div>
            <h1>{{ c.firstName }} {{ c.lastName }}</h1>
            <p class="meta">
              <span>{{ c.email }}</span>
              @if (c.phone) { <span>{{ c.phone }}</span> }
              <span>{{ c.countryCode }}</span>
              <span>Joined {{ c.createdAt | ago }}</span>
              <fd-id [value]="c.id" label="Customer ID" />
            </p>
          </div>
          <div class="page-actions">
            <span [class]="c.status | tone">{{ c.status | humanize }}</span>
            @if (canChangeStatus()) {
              @if (c.status === 'ACTIVE') {
                <button class="btn btn-danger" type="button" (click)="setStatus(c, 'BLOCKED')">Block customer</button>
              } @else if (c.status === 'BLOCKED') {
                <button class="btn" type="button" (click)="setStatus(c, 'ACTIVE')">Unblock customer</button>
              }
            }
          </div>
        </header>

        @if (alerts().length) {
          <section class="panel alerts">
            <div class="panel-head">
              <div>
                <h2>Alerts on this customer</h2>
                <p>{{ openAlerts() }} still open</p>
              </div>
            </div>
            <ul>
              @for (a of alerts(); track a.id) {
                <li>
                  <a [routerLink]="['/alerts']" [queryParams]="{ id: a.id }">
                    <span class="score" [attr.data-sev]="a.severity">{{ a.score }}</span>
                    <span class="what"><strong>{{ a.primaryReason || a.title }}</strong><small>{{ a.createdAt | ago }}</small></span>
                    <span [class]="a.status | tone">{{ a.status | humanize }}</span>
                  </a>
                </li>
              }
            </ul>
          </section>
        }

        <fd-money-panel [customerId]="c.id" />
      } @else if (notFound()) {
        <header class="page-head">
          <div>
            <h1>Customer not found</h1>
            <p>No customer has this ID. It may have been typed or copied incorrectly.</p>
          </div>
        </header>
      }
    </div>
  `,
  styles: `
    .crumbs a {
      color: var(--muted);
      text-decoration: none;
      font-size: var(--t-sm);
    }
    .crumbs a::before { content: '‹ '; }
    .crumbs a:hover { color: var(--ink); }
    .page { gap: 20px; }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 18px;
      align-items: center;
    }
    .page-actions { align-items: center; gap: 14px; }
    .alerts ul {
      list-style: none;
      margin: 0;
      padding: 6px;
    }
    .alerts a {
      display: grid;
      grid-template-columns: 42px 1fr auto;
      gap: 14px;
      align-items: center;
      padding: 8px 10px;
      border-radius: var(--r-md);
      color: inherit;
      text-decoration: none;
    }
    .alerts a:hover { background: var(--panel-2); }
    .score {
      display: grid;
      place-items: center;
      height: 32px;
      border-radius: var(--r-md);
      font-weight: 700;
      color: #fff;
      background: var(--sev-high);
    }
    .score[data-sev='CRITICAL'] { background: var(--sev-critical); }
    .what { display: grid; min-width: 0; }
    .what strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .what small { color: var(--muted); }
  `,
})
export class CustomerDetail {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly customer = signal<Customer | null>(null);
  protected readonly alerts = signal<Alert[]>([]);
  protected readonly notFound = signal(false);
  protected readonly canChangeStatus = computed(() => this.auth.hasAnyRole('ADMIN', 'ANALYST', 'INVESTIGATOR'));
  protected readonly openAlerts = computed(() => this.alerts().filter((a) => a.status !== 'RESOLVED').length);

  constructor() {
    effect(() => {
      const id = this.id();
      this.customer.set(null);
      this.notFound.set(false);
      this.api.customer(id).subscribe({
        next: (c) => this.customer.set(c),
        error: (e) => (e.status === 404 ? this.notFound.set(true) : toastError(e)),
      });
      this.api.alerts({ customerId: id, size: 20 }).subscribe({
        next: (p) => this.alerts.set(p.content),
        error: () => this.alerts.set([]),
      });
    });
  }

  protected async setStatus(c: Customer, status: CustomerStatus): Promise<void> {
    const blocking = status === 'BLOCKED';
    const ok = await confirmAction({
      title: blocking ? `Block ${c.firstName} ${c.lastName}?` : `Unblock ${c.firstName} ${c.lastName}?`,
      text: blocking
        ? 'The customer is marked blocked across the platform. Their accounts keep their own status.'
        : 'The customer returns to active.',
      confirm: blocking ? 'Block customer' : 'Unblock customer',
      danger: blocking,
    });
    if (!ok) return;
    this.api.setCustomerStatus(c.id, status).subscribe({
      next: (updated) => {
        this.customer.set(updated);
        toast(blocking ? 'Customer blocked' : 'Customer unblocked');
      },
      error: toastError,
    });
  }
}
