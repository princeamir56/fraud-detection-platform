import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { ThemeService } from '../core/theme.service';

Chart.register(...registerables);

export function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/** Thin Chart.js host: rebuilds when the config or the theme changes. */
@Component({
  selector: 'fd-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas [attr.aria-label]="label()" role="img"></canvas>`,
  styles: `
    :host {
      display: block;
      position: relative;
      height: var(--chart-h, 220px);
    }
  `,
})
export class ChartHost implements OnDestroy {
  private readonly theme = inject(ThemeService);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly config = input.required<() => ChartConfiguration>();
  readonly label = input('');
  private chart?: Chart;
  private readonly media = window.matchMedia('(prefers-color-scheme: dark)');
  private readonly onMedia = () => this.render();

  constructor() {
    effect(() => {
      this.theme.choice();
      this.config();
      // Wait a frame so theme attribute changes have applied before reading CSS variables.
      requestAnimationFrame(() => this.render());
    });
    this.media.addEventListener('change', this.onMedia);
  }

  private render(): void {
    this.chart?.destroy();
    Chart.defaults.font.family = cssVar('--font');
    Chart.defaults.color = cssVar('--muted');
    Chart.defaults.borderColor = cssVar('--rule');
    this.chart = new Chart(this.canvas().nativeElement, this.config()());
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
    this.media.removeEventListener('change', this.onMedia);
  }
}
