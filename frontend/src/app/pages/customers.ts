import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../core/api.service';
import { AgoPipe, HumanizePipe, TonePipe } from '../core/format';
import { Customer, CustomerStatus } from '../core/models';
import { toastError } from '../core/notify';
import { CopyId, EmptyState, Pager } from '../shared/marks';

const PAGE_SIZE = 25;

@Component({
  selector: 'fd-customers',
  imports: [CopyId, EmptyState, Pager, AgoPipe, HumanizePipe, TonePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Customers</h1>
          <p>People who bank with you. Open a customer to see their accounts, payments and alerts.</p>
        </div>
      </header>

      <section class="panel">
        <div class="toolbar">
          <div class="segmented" role="group" aria-label="Customer status">
            @for (s of statuses; track s.value) {
              <button type="button" [attr.aria-pressed]="status() === s.value" (click)="status.set(s.value); page.set(0)">
                {{ s.label }}
              </button>
            }
          </div>
        </div>
        @if (rows().length) {
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Email</th>
                  <th scope="col">Country</th>
                  <th scope="col">Status</th>
                  <th scope="col">Customer ID</th>
                  <th scope="col">Joined</th>
                </tr>
              </thead>
              <tbody>
                @for (c of rows(); track c.id) {
                  <tr class="clickable" tabindex="0" (click)="open(c)" (keydown.enter)="open(c)">
                    <td><strong>{{ c.firstName }} {{ c.lastName }}</strong></td>
                    <td>{{ c.email }}</td>
                    <td>{{ c.countryCode }}</td>
                    <td><span [class]="c.status | tone">{{ c.status | humanize }}</span></td>
                    <td><fd-id [value]="c.id" label="Customer ID" /></td>
                    <td class="muted">{{ c.createdAt | ago }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <fd-pager [page]="page()" [size]="pageSize" [total]="total()" (pageChange)="page.set($event)" />
        } @else if (loaded()) {
          <fd-empty title="No customers here" text="Customers appear when someone registers from the sign-in page." />
        }
      </section>
    </div>
  `,
})
export class Customers {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly pageSize = PAGE_SIZE;
  protected readonly statuses: { value: CustomerStatus | ''; label: string }[] = [
    { value: '', label: 'All' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'BLOCKED', label: 'Blocked' },
    { value: 'CLOSED', label: 'Closed' },
  ];
  protected readonly status = signal<CustomerStatus | ''>('');
  protected readonly page = signal(0);
  protected readonly rows = signal<Customer[]>([]);
  protected readonly total = signal(0);
  protected readonly loaded = signal(false);

  constructor() {
    effect(() => {
      this.api.customers({ status: this.status(), page: this.page(), size: PAGE_SIZE }).subscribe({
        next: (p) => {
          this.rows.set(p.content);
          this.total.set(p.totalElements);
          this.loaded.set(true);
        },
        error: (e) => {
          this.loaded.set(true);
          toastError(e);
        },
      });
    });
  }

  protected open(c: Customer): void {
    this.router.navigate(['/customers', c.id]);
  }
}
