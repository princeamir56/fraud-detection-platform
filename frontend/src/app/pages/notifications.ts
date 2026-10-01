import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';
import { AgoPipe, HumanizePipe, TonePipe } from '../core/format';
import { NotificationRecord } from '../core/models';
import { toastError } from '../core/notify';
import { CopyId, EmptyState, Pager, SeverityMark } from '../shared/marks';

const PAGE_SIZE = 30;

@Component({
  selector: 'fd-notifications',
  imports: [RouterLink, CopyId, EmptyState, Pager, SeverityMark, AgoPipe, HumanizePipe, TonePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Notifications</h1>
          <p>Messages sent to customers when an alert is raised on their account. Delivery is simulated in this environment.</p>
        </div>
      </header>

      <section class="panel">
        @if (rows().length) {
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th scope="col">Channel</th>
                  <th scope="col">Message</th>
                  <th scope="col">Severity</th>
                  <th scope="col">Delivery</th>
                  <th scope="col">Customer</th>
                  <th scope="col">Alert</th>
                  <th scope="col">Sent</th>
                </tr>
              </thead>
              <tbody>
                @for (n of rows(); track n.id) {
                  <tr>
                    <td><strong>{{ n.channel | humanize }}</strong></td>
                    <td class="subject" [title]="n.subject">{{ n.subject }}</td>
                    <td><fd-sev [value]="n.severity" /></td>
                    <td><span [class]="n.status | tone">{{ n.status | humanize }}</span></td>
                    <td><a [routerLink]="['/customers', n.customerId]"><fd-id [value]="n.customerId" label="Customer ID" /></a></td>
                    <td><a [routerLink]="['/alerts']" [queryParams]="{ id: n.alertId }">Open alert</a></td>
                    <td class="muted">{{ n.createdAt | ago }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <fd-pager [page]="page()" [size]="pageSize" [total]="total()" (pageChange)="page.set($event)" />
        } @else if (loaded()) {
          <fd-empty title="No notifications sent" text="Customers are notified automatically when a HIGH or CRITICAL alert is raised." />
        }
      </section>
    </div>
  `,
  styles: `
    .subject {
      max-width: 360px;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `,
})
export class Notifications {
  private readonly api = inject(Api);
  protected readonly pageSize = PAGE_SIZE;
  protected readonly page = signal(0);
  protected readonly rows = signal<NotificationRecord[]>([]);
  protected readonly total = signal(0);
  protected readonly loaded = signal(false);

  constructor() {
    effect(() => {
      this.api.notifications({ page: this.page(), size: PAGE_SIZE }).subscribe({
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
}
