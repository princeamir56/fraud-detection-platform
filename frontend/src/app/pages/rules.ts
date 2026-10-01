import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AgoPipe } from '../core/format';
import { FraudRule } from '../core/models';
import { confirmAction, toast, toastError } from '../core/notify';
import { EmptyState } from '../shared/marks';

@Component({
  selector: 'fd-rules',
  imports: [FormsModule, EmptyState, AgoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Scoring rules</h1>
          <p>
            Each rule that fires adds its weight to the score, capped at 100. Changes apply to the next
            transaction; nothing is redeployed.
          </p>
        </div>
      </header>

      <section class="panel budget">
        <div class="budget-head">
          <div>
            <h2>{{ activeTotal() }} points across {{ activeRules().length }} active rules</h2>
            <p class="muted">
              A score of 80 blocks the transaction and the score stops at 100, so a few strong rules firing together
              are enough to block.
            </p>
          </div>
          @if (!canEdit()) {
            <p class="muted small">Only admins can change rules.</p>
          }
        </div>
        <div class="stack-wrap">
          <div class="stack" role="img" [attr.aria-label]="'Active rule weights total ' + activeTotal()">
            @for (r of activeRules(); track r.code) {
              <span [style.width.%]="(r.weight / scaleMax()) * 100" [title]="r.name + ': ' + r.weight">{{ r.weight }}</span>
            }
          </div>
          <i class="line block" [style.left.%]="(80 / scaleMax()) * 100"><b>Block at 80</b></i>
          <i class="line cap" [style.left.%]="(100 / scaleMax()) * 100"><b>Capped at 100</b></i>
        </div>
      </section>

      @if (rules().length) {
        <section class="panel">
          <ul class="rules">
            @for (r of rules(); track r.code) {
              <li [class.off]="!r.enabled">
                <div class="info">
                  <div class="title">
                    <h3>{{ r.name }}</h3>
                    <span class="code mono">{{ r.code }}</span>
                  </div>
                  <p>{{ r.description }}</p>
                  <p class="params muted">
                    @if (r.thresholdNumeric !== null) { Threshold {{ r.thresholdNumeric }}. }
                    @if (r.thresholdInt !== null) { Count limit {{ r.thresholdInt }}. }
                    Updated {{ r.updatedAt | ago }}.
                  </p>
                </div>

                <div class="weight">
                  <label [attr.for]="'w-' + r.code">Weight</label>
                  <div class="slider">
                    <input
                      [id]="'w-' + r.code"
                      type="range"
                      min="1"
                      max="100"
                      [disabled]="!canEdit() || !r.enabled"
                      [ngModel]="draft()[r.code] ?? r.weight"
                      (ngModelChange)="setDraft(r.code, $event)"
                    />
                    <output>{{ draft()[r.code] ?? r.weight }}</output>
                  </div>
                  @if (draft()[r.code] !== undefined && draft()[r.code] !== r.weight) {
                    <div class="draft-actions">
                      <button class="btn btn-sm" type="button" (click)="discard(r.code)">Discard</button>
                      <button class="btn btn-sm btn-primary" type="button" (click)="saveWeight(r)">Save weight</button>
                    </div>
                  }
                </div>

                <label class="switch" [class.disabled]="!canEdit()">
                  <input type="checkbox" role="switch" [checked]="r.enabled" [disabled]="!canEdit()" (change)="toggle(r, $event)" />
                  <span class="track"><span class="thumb"></span></span>
                  <span class="switch-label">{{ r.enabled ? 'On' : 'Off' }}</span>
                </label>
              </li>
            }
          </ul>
        </section>
      } @else if (loaded()) {
        <section class="panel">
          <fd-empty title="No rules found" text="The fraud-detection service seeds nine rules on first start. Check that it is running." />
        </section>
      }
    </div>
  `,
  styles: `
    .small { font-size: var(--t-sm); }
    .budget {
      padding: 22px 24px 30px;
      display: grid;
      gap: 16px;
    }
    .budget-head {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }
    .budget-head p { margin-top: 4px; }
    .stack-wrap {
      position: relative;
      padding-bottom: 26px;
    }
    .stack {
      display: flex;
      height: 34px;
      border-radius: var(--r-md);
      overflow: hidden;
    }
    .stack span {
      flex: none;
      display: grid;
      place-items: center;
      background: var(--engrave);
      color: var(--engrave-ink);
      font-size: var(--t-xs);
      font-weight: 600;
      border-right: 2px solid var(--panel);
    }
    .stack span:nth-child(even) { opacity: 0.78; }
    .line {
      font-style: normal;
      position: absolute;
      top: -6px;
      bottom: 0;
      border-left: 2px solid var(--ink);
    }
    .line.block { border-color: var(--sev-critical); }
    .line b {
      position: absolute;
      bottom: 0;
      left: 6px;
      white-space: nowrap;
      font-size: var(--t-xs);
      font-weight: 600;
      color: var(--ink-2);
    }
    .line.block b { left: auto; right: 6px; color: var(--sev-critical); }

    .rules {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .rules li {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 260px 90px;
      gap: 28px;
      align-items: center;
      padding: 20px 24px;
      border-bottom: 1px solid var(--rule);
    }
    .rules li:last-child { border-bottom: 0; }
    .rules li.off .info { opacity: 0.55; }
    @media (max-width: 860px) {
      .rules li { grid-template-columns: 1fr; gap: 14px; }
    }
    .title {
      display: flex;
      align-items: baseline;
      gap: 10px;
      flex-wrap: wrap;
    }
    .code {
      color: var(--muted);
      font-size: var(--t-xs);
    }
    .info p {
      margin-top: 4px;
      max-width: 70ch;
      color: var(--ink-2);
    }
    .info .params {
      font-size: var(--t-sm);
      color: var(--muted);
    }
    .weight {
      display: grid;
      gap: 6px;
    }
    .weight label {
      font-size: var(--t-sm);
      color: var(--muted);
    }
    .slider {
      display: grid;
      grid-template-columns: 1fr 40px;
      gap: 12px;
      align-items: center;
    }
    .slider input {
      width: 100%;
      accent-color: var(--engrave);
    }
    output {
      font-weight: 700;
      font-size: var(--t-lg);
      text-align: right;
    }
    .draft-actions {
      display: flex;
      gap: 6px;
      justify-content: flex-end;
    }

    .switch {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      justify-self: end;
    }
    .switch.disabled { cursor: not-allowed; opacity: 0.6; }
    .switch input {
      position: absolute;
      opacity: 0;
      width: 1px;
      height: 1px;
    }
    .track {
      width: 40px;
      height: 22px;
      border-radius: 99px;
      background: var(--rule-strong);
      position: relative;
      transition: background 0.15s;
    }
    .thumb {
      position: absolute;
      top: 3px;
      left: 3px;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: #fff;
      transition: transform 0.15s;
    }
    .switch input:checked + .track { background: var(--engrave); }
    .switch input:checked + .track .thumb { transform: translateX(18px); }
    .switch input:focus-visible + .track {
      outline: 2px solid var(--focus);
      outline-offset: 2px;
    }
    .switch-label {
      min-width: 22px;
      font-weight: 550;
    }
  `,
})
export class Rules {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  protected readonly rules = signal<FraudRule[]>([]);
  protected readonly loaded = signal(false);
  protected readonly draft = signal<Record<string, number>>({});
  protected readonly canEdit = computed(() => this.auth.hasAnyRole('ADMIN'));
  protected readonly activeRules = computed(() =>
    this.rules()
      .filter((r) => r.enabled)
      .sort((a, b) => b.weight - a.weight),
  );
  protected readonly activeTotal = computed(() => this.activeRules().reduce((s, r) => s + r.weight, 0));
  /** The bar and the threshold lines share one scale: 0 to whichever is larger, 100 or the total. */
  protected readonly scaleMax = computed(() => Math.max(100, this.activeTotal()));

  constructor() {
    this.api.rules().subscribe({
      next: (rules) => {
        this.rules.set([...rules].sort((a, b) => b.weight - a.weight));
        this.loaded.set(true);
      },
      error: (e) => {
        this.loaded.set(true);
        toastError(e);
      },
    });
  }

  protected setDraft(code: string, value: number): void {
    this.draft.update((d) => ({ ...d, [code]: Number(value) }));
  }
  protected discard(code: string): void {
    this.draft.update((d) => {
      const { [code]: _, ...rest } = d;
      return rest;
    });
  }

  private replace(updated: FraudRule): void {
    this.rules.update((list) => list.map((r) => (r.code === updated.code ? updated : r)));
  }

  protected async toggle(rule: FraudRule, ev: Event): Promise<void> {
    const input = ev.target as HTMLInputElement;
    const enable = input.checked;
    input.checked = rule.enabled; // reflect server state until confirmed
    if (!enable) {
      const ok = await confirmAction({
        title: `Turn off ${rule.name}?`,
        text: `It stops adding up to ${rule.weight} points to every score, starting with the next transaction. Transactions that relied on it may be allowed.`,
        confirm: 'Turn off rule',
        danger: true,
      });
      if (!ok) return;
    }
    this.api.setRuleEnabled(rule.code, enable).subscribe({
      next: (updated) => {
        this.replace(updated);
        toast(enable ? 'Rule turned on' : 'Rule turned off', rule.name);
      },
      error: toastError,
    });
  }

  protected async saveWeight(rule: FraudRule): Promise<void> {
    const weight = this.draft()[rule.code];
    const ok = await confirmAction({
      title: `Change ${rule.name} to ${weight} points?`,
      text: `It currently adds ${rule.weight}. The new weight applies to the next transaction scored.`,
      confirm: 'Save weight',
    });
    if (!ok) return;
    const { code, createdAt, updatedAt, version, ...body } = rule;
    this.api.updateRule(code, { ...body, weight }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.discard(code);
        toast('Weight saved', `${rule.name} now adds ${updated.weight} points.`);
      },
      error: toastError,
    });
  }
}
