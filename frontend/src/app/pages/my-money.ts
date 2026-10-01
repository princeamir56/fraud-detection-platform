import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Customer } from '../core/models';
import { MoneyPanel } from '../shared/money-panel';

@Component({
  selector: 'fd-my-money',
  imports: [MoneyPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>{{ me() ? 'Hello, ' + me()!.firstName : 'My money' }}</h1>
          <p>Your accounts and payments. Every payment is checked for fraud before it completes.</p>
        </div>
      </header>
      @if (auth.user()?.customerId; as id) {
        <fd-money-panel [customerId]="id" />
      } @else {
        <p>This login isn’t linked to a customer profile, so there are no accounts to show.</p>
      }
    </div>
  `,
})
export class MyMoney {
  protected readonly auth = inject(AuthService);
  protected readonly me = signal<Customer | null>(null);

  constructor() {
    const id = this.auth.user()?.customerId;
    if (id) inject(Api).customer(id).subscribe({ next: (c) => this.me.set(c), error: () => void 0 });
  }
}
