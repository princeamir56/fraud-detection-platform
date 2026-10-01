import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Banknote-style guilloché rosette: overlapping hypotrochoid-like curves drawn as SVG paths.
 * Pure geometry, so it scales from a 28px logo mark to a full-bleed background.
 */
@Component({
  selector: 'fd-guilloche',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="-100 -100 200 200" aria-hidden="true" preserveAspectRatio="xMidYMid meet">
      @for (d of paths(); track $index) {
        <path [attr.d]="d" fill="none" stroke="currentColor" [attr.stroke-width]="strokeWidth()" />
      }
    </svg>
  `,
  styles: `
    :host { display: block; }
    svg { width: 100%; height: 100%; display: block; }
  `,
})
export class Guilloche {
  readonly lines = input(18);
  readonly lobes = input(9);
  readonly strokeWidth = input(0.35);

  protected readonly paths = computed(() => {
    const out: string[] = [];
    const n = this.lines();
    const k = this.lobes();
    for (let i = 0; i < n; i++) {
      const phase = (i / n) * ((Math.PI * 2) / k);
      const inner = 44 + (i % 3) * 3;
      let d = '';
      for (let s = 0; s <= 720; s++) {
        const t = (s / 720) * Math.PI * 2;
        const r = inner + 36 * Math.abs(Math.sin((k * t) / 2 + phase)) + 8 * Math.sin(k * 3 * t);
        const x = r * Math.cos(t);
        const y = r * Math.sin(t);
        d += `${s === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`;
      }
      out.push(d + 'Z');
    }
    return out;
  });
}
