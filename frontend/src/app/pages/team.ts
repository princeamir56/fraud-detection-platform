import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../core/api.service';
import { Role, UserAccount } from '../core/models';
import { describeError, toast } from '../core/notify';

const ROLES: { value: Role; label: string; text: string }[] = [
  { value: 'ANALYST', label: 'Analyst', text: 'Watches the overview, opens accounts, sends test transactions, freezes accounts.' },
  { value: 'INVESTIGATOR', label: 'Investigator', text: 'Takes and resolves alert cases, reads the audit trail.' },
  { value: 'ADMIN', label: 'Admin', text: 'Everything above, plus changing scoring rules and adding team members.' },
];

@Component({
  selector: 'fd-team',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Team access</h1>
          <p>Add someone to the fraud desk. They sign in with the username and password you set here.</p>
        </div>
      </header>

      <div class="layout">
        <section class="panel">
          <form class="panel-body" [formGroup]="form" (ngSubmit)="create()" novalidate>
            <label class="field">
              <span>Username</span>
              <input class="input" formControlName="username" autocomplete="off" />
              <small>Letters, digits, dot, underscore or hyphen. At least 3 characters.</small>
            </label>
            <label class="field">
              <span>Temporary password</span>
              <input class="input" type="password" formControlName="password" autocomplete="new-password" />
              <small>At least 8 characters. Share it privately.</small>
            </label>

            <fieldset>
              <legend>Roles</legend>
              <div class="fd-choice">
                @for (r of roles; track r.value) {
                  <label>
                    <input type="checkbox" [checked]="picked().includes(r.value)" (change)="toggle(r.value)" />
                    <span><strong>{{ r.label }}</strong><small>{{ r.text }}</small></span>
                  </label>
                }
              </div>
            </fieldset>

            @if (error()) {
              <p class="error" role="alert">{{ error() }}</p>
            }
            <div>
              <button class="btn btn-primary" type="submit" [disabled]="busy()">{{ busy() ? 'Adding…' : 'Add team member' }}</button>
            </div>
          </form>
        </section>

        @if (added().length) {
          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>Added this session</h2>
                <p>The platform has no endpoint for listing staff, so only people added here are shown.</p>
              </div>
            </div>
            <ul class="added">
              @for (u of added(); track u.id) {
                <li>
                  <strong>{{ u.username }}</strong>
                  <span class="muted">{{ roleNames(u.roles) }}</span>
                </li>
              }
            </ul>
          </section>
        }
      </div>
    </div>
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 560px) minmax(0, 1fr);
      gap: 20px;
      align-items: start;
    }
    @media (max-width: 1000px) {
      .layout { grid-template-columns: 1fr; }
    }
    form {
      display: grid;
      gap: 18px;
    }
    fieldset {
      border: 0;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 8px;
    }
    legend {
      font-size: var(--t-sm);
      font-weight: 550;
      color: var(--ink-2);
      margin-bottom: 8px;
    }
    .error {
      color: var(--sev-critical);
      white-space: pre-line;
    }
    .added {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .added li {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 20px;
      border-bottom: 1px solid var(--rule);
    }
    .added li:last-child { border-bottom: 0; }
  `,
})
export class Team {
  private readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
  protected readonly roles = ROLES;
  protected readonly picked = signal<Role[]>(['ANALYST']);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly added = signal<UserAccount[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.pattern(/^[A-Za-z0-9._-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected toggle(role: Role): void {
    this.picked.update((p) => (p.includes(role) ? p.filter((r) => r !== role) : [...p, role]));
  }

  protected roleNames(roles: Role[]): string {
    return roles.map((r) => ROLES.find((x) => x.value === r)?.label ?? r).join(', ');
  }

  protected create(): void {
    if (this.form.invalid || !this.picked().length) {
      this.form.markAllAsTouched();
      this.error.set('Enter a username (3+ characters), a password (8+ characters), and pick at least one role.');
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api.createUser(username, password, this.picked()).subscribe({
      next: (u) => {
        this.busy.set(false);
        this.added.update((list) => [u, ...list]);
        this.form.reset();
        this.picked.set(['ANALYST']);
        toast('Team member added', `${u.username} can sign in now.`);
      },
      error: (e) => {
        this.busy.set(false);
        const { title, detail } = describeError(e);
        this.error.set(detail ? `${title}\n${detail}` : title);
      },
    });
  }
}
