import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { humanize, severityFromScore } from '../core/format';
import { toast } from '../core/notify';
import { Severity } from '../core/models';
import { Icon } from './icon';

@Component({
  selector: 'fd-sev',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (value()) {
    <span class="sev" [class]="'sev sev-' + value()!.toLowerCase()">{{ label() }}</span>
  } @else {
    <span class="muted">Not scored</span>
  }`,
})
export class SeverityMark {
  readonly value = input<Severity | null | undefined>();
  protected readonly label = computed(() => humanize(this.value()));
}

/**
 * The console's signature element: the real severity bands at their true proportions
 * (0–29, 30–59, 60–79, 80–100) with a marker where this score lands.
 */
@Component({
  selector: 'fd-score',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="score" [class.compact]="compact()" [attr.data-sev]="sev()">
      @if (!compact()) {
        <div class="figure">
          <strong>{{ score() ?? '–' }}</strong><span>/ 100</span>
        </div>
      }
      <div
        class="track"
        role="meter"
        aria-valuemin="0"
        aria-valuemax="100"
        [attr.aria-valuenow]="score()"
        [attr.aria-label]="'Fraud score ' + (score() ?? 'pending')"
      >
        <i class="band low"></i><i class="band medium"></i><i class="band high"></i><i class="band critical"></i>
        @if (score() !== null && score() !== undefined) {
          <b class="marker" [style.left.%]="score()"></b>
        }
      </div>
      @if (compact()) {
        <span class="n">{{ score() ?? '–' }}</span>
      }
    </div>
  `,
  styles: `
    .score {
      display: grid;
      gap: 10px;
    }
    .figure {
      display: flex;
      align-items: baseline;
      gap: 6px;
    }
    .figure strong {
      font-variant-numeric: tabular-nums;
      font-size: var(--t-3xl);
      font-weight: 700;
      letter-spacing: -0.04em;
      line-height: 1;
    }
    .figure span {
      color: var(--muted);
    }
    [data-sev='LOW'] strong { color: var(--sev-low); }
    [data-sev='MEDIUM'] strong { color: var(--sev-medium); }
    [data-sev='HIGH'] strong { color: var(--sev-high); }
    [data-sev='CRITICAL'] strong { color: var(--sev-critical); }

    .track {
      position: relative;
      display: grid;
      grid-template-columns: 30fr 30fr 20fr 21fr;
      gap: 2px;
      height: 10px;
    }
    .band {
      border-radius: 2px;
      opacity: 0.28;
    }
    .band.low { background: var(--sev-low); }
    .band.medium { background: var(--sev-medium); }
    .band.high { background: var(--sev-high); }
    .band.critical { background: var(--sev-critical); }
    [data-sev='LOW'] .low,
    [data-sev='MEDIUM'] .medium,
    [data-sev='HIGH'] .high,
    [data-sev='CRITICAL'] .critical {
      opacity: 1;
    }
    .marker {
      position: absolute;
      top: -4px;
      width: 3px;
      height: 18px;
      margin-left: -1.5px;
      background: var(--ink);
      border-radius: 2px;
      box-shadow: 0 0 0 2px var(--panel);
    }

    .compact {
      grid-template-columns: 88px 26px;
      align-items: center;
      gap: 10px;
    }
    .compact .track {
      height: 6px;
    }
    .compact .marker {
      top: -4px;
      height: 14px;
      width: 2px;
      margin-left: -1px;
    }
    .n {
      font-variant-numeric: tabular-nums;
      font-weight: 600;
      text-align: right;
    }
  `,
})
export class ScoreScale {
  readonly score = input<number | null | undefined>();
  readonly compact = input(false);
  protected readonly sev = computed(() => {
    const s = this.score();
    return s === null || s === undefined ? null : severityFromScore(s);
  });
}

@Component({
  selector: 'fd-id',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="id">
    <span class="mono" [title]="value()">{{ display() }}</span>
    @if (value()) {
      <button type="button" class="copy" (click)="copy($event)" [attr.aria-label]="'Copy ' + label()">
        <fd-icon name="copy" />
      </button>
    }
  </span>`,
  styles: `
    .id {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: var(--ink-2);
    }
    .copy {
      display: inline-flex;
      border: 0;
      background: none;
      color: var(--muted);
      padding: 2px;
      border-radius: 4px;
      cursor: pointer;
      opacity: 0;
    }
    .copy fd-icon {
      width: 14px;
      height: 14px;
    }
    .id:hover .copy,
    .copy:focus-visible {
      opacity: 1;
    }
    .copy:hover {
      color: var(--ink);
    }
    @media (hover: none) {
      .copy { opacity: 1; }
    }
  `,
})
export class CopyId {
  readonly value = input<string | null | undefined>();
  readonly full = input(false);
  readonly label = input('ID');
  protected readonly display = computed(() => {
    const v = this.value();
    if (!v) return '—';
    return this.full() || v.length <= 12 ? v : v.slice(0, 8);
  });

  copy(ev: Event): void {
    ev.stopPropagation();
    navigator.clipboard?.writeText(this.value() ?? '').then(
      () => toast(`${this.label()} copied`),
      () => toast('Copy failed', 'Your browser blocked clipboard access.', 'error'),
    );
  }
}

@Component({
  selector: 'fd-empty',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <h3>{{ title() }}</h3>
      <p>{{ text() }}</p>
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      display: grid;
      justify-items: start;
      gap: 6px;
      padding: 40px 24px;
      max-width: 52ch;
    }
    p {
      color: var(--muted);
    }
    .empty ::ng-deep .btn {
      margin-top: 10px;
    }
  `,
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly text = input('');
}

@Component({
  selector: 'fd-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > size()) {
      <nav class="pager" aria-label="Pagination">
        <span class="muted">{{ from() }}–{{ to() }} of {{ total() }}</span>
        <div>
          <button class="btn btn-sm" type="button" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
            Previous
          </button>
          <button class="btn btn-sm" type="button" [disabled]="to() >= total()" (click)="pageChange.emit(page() + 1)">
            Next
          </button>
        </div>
      </nav>
    }
  `,
  styles: `
    .pager {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      border-top: 1px solid var(--rule);
      font-size: var(--t-sm);
    }
    .pager div {
      display: flex;
      gap: 6px;
    }
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly pageChange = output<number>();
  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));
}
