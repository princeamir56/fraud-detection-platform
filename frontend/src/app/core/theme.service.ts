import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ThemeChoice = 'system' | 'light' | 'dark';
const KEY = 'fd.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  readonly choice = signal<ThemeChoice>(this.read());

  constructor() {
    this.apply(this.choice());
  }

  cycle(): void {
    const next: ThemeChoice = this.choice() === 'system' ? 'light' : this.choice() === 'light' ? 'dark' : 'system';
    this.choice.set(next);
    this.apply(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* per-tab preference only */
    }
  }

  /** Resolved theme, for canvases (charts) that can't read CSS variables reactively. */
  isDark(): boolean {
    const c = this.choice();
    if (c !== 'system') return c === 'dark';
    return this.doc.defaultView?.matchMedia('(prefers-color-scheme: dark)').matches ?? false;
  }

  private apply(choice: ThemeChoice): void {
    const root = this.doc.documentElement;
    if (choice === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', choice);
  }

  private read(): ThemeChoice {
    try {
      const v = localStorage.getItem(KEY);
      return v === 'light' || v === 'dark' ? v : 'system';
    } catch {
      return 'system';
    }
  }
}
