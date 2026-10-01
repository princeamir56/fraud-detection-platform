import { LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { AgoPipe, HumanizePipe, MoneyPipe } from '../core/format';
import { ScoredTransaction } from '../core/models';

interface Dot {
  tx: ScoredTransaction;
  left: number;
  top: number;
  size: number;
}

/** Stable pseudo-random 0..1 from an id, so dots don't jump between refreshes. */
function hash01(id: string): number {
  let h = 2166136261;
  for (let i = 0; i < id.length; i++) {
    h ^= id.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return ((h >>> 0) % 1000) / 1000;
}

/**
 * Every recently scored transaction placed on the 0–100 score scale, over the real severity
 * bands. Dot size follows amount (log scale). Hover or focus a dot to read it; click to open it.
 */
@Component({
  selector: 'fd-verdict-rail',
  imports: [MoneyPipe, AgoPipe, HumanizePipe, LowerCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rail" (mouseleave)="hover.set(null)">
      <div class="bands" aria-hidden="true">
        <div class="band low"><span>Allow</span></div>
        <div class="band medium"><span>Allow, watch</span></div>
        <div class="band high"><span>Review</span></div>
        <div class="band critical"><span>Block</span></div>
      </div>
      <div class="plot" role="list" aria-label="Recent transactions by fraud score">
        @for (d of dots(); track d.tx.transactionId) {
          <button
            type="button"
            role="listitem"
            class="dot"
            [attr.data-sev]="d.tx.severity"
            [style.left.%]="d.left"
            [style.top.%]="d.top"
            [style.width.px]="d.size"
            [style.height.px]="d.size"
            [attr.aria-label]="d.tx.score + ' points, ' + d.tx.amount + ' ' + d.tx.currency + ', ' + d.tx.countryCode"
            (mouseenter)="hover.set(d.tx)"
            (focus)="hover.set(d.tx)"
            (click)="open.emit(d.tx.transactionId)"
          ></button>
        }
      </div>
      <div class="axis" aria-hidden="true">
        <span style="left: 0">0</span>
        <span style="left: 30%">30</span>
        <span style="left: 60%">60</span>
        <span style="left: 80%">80</span>
        <span style="left: 100%">100</span>
      </div>
    </div>
    <p class="readout" aria-live="polite">
      @if (hover(); as t) {
        <strong>{{ t.score }} points</strong>
        <span>{{ t.amount | money: t.currency }} {{ t.type | humanize | lowercase }} in {{ t.countryCode }}</span>
        <span class="muted">{{ t.merchantCategory | humanize }}, {{ t.occurredAt | ago }}</span>
      } @else {
        <span class="muted">Point at a transaction to read it. Select it to see why it scored that way.</span>
      }
    </p>
  `,
  styles: `
    :host {
      display: grid;
      gap: 14px;
    }
    .rail {
      position: relative;
      padding-bottom: 22px;
    }
    .bands {
      display: grid;
      grid-template-columns: 30fr 30fr 20fr 20fr;
      gap: 2px;
      height: 188px;
    }
    .band {
      position: relative;
      background: repeating-linear-gradient(
        -45deg,
        color-mix(in srgb, var(--c) 9%, transparent) 0 6px,
        color-mix(in srgb, var(--c) 4%, transparent) 6px 12px
      );
      border-top: 3px solid var(--c);
    }
    .band span {
      position: absolute;
      top: 8px;
      left: 10px;
      font-size: var(--t-xs);
      font-weight: 600;
      color: var(--c);
    }
    .low { --c: var(--sev-low); border-radius: 6px 0 0 6px; }
    .medium { --c: var(--sev-medium); }
    .high { --c: var(--sev-high); }
    .critical { --c: var(--sev-critical); border-radius: 0 6px 6px 0; }

    .plot {
      position: absolute;
      inset: 34px 0 30px;
    }
    .dot {
      position: absolute;
      transform: translate(-50%, -50%);
      border-radius: 50%;
      border: 1.5px solid var(--panel);
      background: var(--c);
      padding: 0;
      cursor: pointer;
      opacity: 0.85;
      transition: transform 0.12s;
    }
    .dot:hover,
    .dot:focus-visible {
      opacity: 1;
      transform: translate(-50%, -50%) scale(1.5);
      z-index: 2;
    }
    .dot[data-sev='LOW'] { --c: var(--sev-low); }
    .dot[data-sev='MEDIUM'] { --c: var(--sev-medium); }
    .dot[data-sev='HIGH'] { --c: var(--sev-high); }
    .dot[data-sev='CRITICAL'] { --c: var(--sev-critical); }

    .axis {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 18px;
    }
    .axis span {
      position: absolute;
      transform: translateX(-50%);
      font-size: var(--t-xs);
      color: var(--muted);
    }
    .axis span:first-child { transform: none; }
    .axis span:last-child { transform: translateX(-100%); }

    .readout {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 14px;
      min-height: 22px;
      font-size: var(--t-sm);
    }
    @media (max-width: 720px) {
      .band span { display: none; }
      .bands { height: 150px; }
    }
  `,
})
export class VerdictRail {
  readonly items = input.required<ScoredTransaction[]>();
  readonly open = output<string>();
  protected readonly hover = signal<ScoredTransaction | null>(null);

  protected readonly dots = computed<Dot[]>(() =>
    this.items().map((tx) => ({
      tx,
      left: Math.min(99, Math.max(1, tx.score)),
      top: 8 + hash01(tx.transactionId) * 84,
      size: Math.round(8 + Math.min(14, Math.log10(Math.max(1, tx.amount)) * 3)),
    })),
  );
}
