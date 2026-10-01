import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { humanize } from '../core/format';
import { Role } from '../core/models';
import { ThemeService } from '../core/theme.service';
import { Guilloche } from '../shared/guilloche';
import { Icon } from '../shared/icon';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles: Role[];
  badge?: 'openAlerts';
}
interface NavGroup {
  label: string;
  items: NavItem[];
}

const STAFF: Role[] = ['ADMIN', 'ANALYST', 'INVESTIGATOR'];

const NAV: NavGroup[] = [
  {
    label: 'Desk',
    items: [
      { path: '/overview', label: 'Overview', icon: 'overview', roles: STAFF },
      { path: '/alerts', label: 'Alerts', icon: 'alerts', roles: STAFF, badge: 'openAlerts' },
      { path: '/transactions', label: 'Transactions', icon: 'transactions', roles: STAFF },
    ],
  },
  {
    label: 'Controls',
    items: [
      { path: '/rules', label: 'Scoring rules', icon: 'rules', roles: STAFF },
      { path: '/customers', label: 'Customers', icon: 'customers', roles: STAFF },
    ],
  },
  {
    label: 'Records',
    items: [
      { path: '/audit', label: 'Audit trail', icon: 'audit', roles: ['ADMIN', 'INVESTIGATOR'] },
      { path: '/notifications', label: 'Notifications', icon: 'notifications', roles: STAFF },
      { path: '/team', label: 'Team access', icon: 'team', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Banking',
    items: [{ path: '/my-money', label: 'My money', icon: 'wallet', roles: ['CUSTOMER'] }],
  },
];

@Component({
  selector: 'fd-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Guilloche],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <div class="layout" [class.nav-open]="navOpen()">
      <aside class="side">
        <a class="brand" [routerLink]="auth.homeRoute()">
          <fd-guilloche class="mark" [lines]="10" [lobes]="7" [strokeWidth]="2.2" />
          <span>Fraud Desk</span>
        </a>

        <nav aria-label="Main">
          @for (group of nav(); track group.label) {
            <div class="group">
              <p class="group-label">{{ group.label }}</p>
              @for (item of group.items; track item.path) {
                <a [routerLink]="item.path" routerLinkActive="active" ariaCurrentWhenActive="page">
                  <fd-icon [name]="item.icon" />
                  <span>{{ item.label }}</span>
                  @if (item.badge === 'openAlerts' && openAlerts() > 0) {
                    <b class="badge" [attr.aria-label]="openAlerts() + ' open alerts'">{{ openAlerts() }}</b>
                  }
                </a>
              }
            </div>
          }
        </nav>

        <div class="me">
          <div class="who">
            <strong>{{ auth.user()?.subject }}</strong>
            <span>{{ roleLabel() }}</span>
          </div>
          <div class="me-actions">
            <button class="btn btn-ghost btn-icon" type="button" (click)="theme.cycle()" [attr.aria-label]="themeLabel()" [title]="themeLabel()">
              <fd-icon [name]="themeIcon()" />
            </button>
            <button class="btn btn-ghost btn-icon" type="button" (click)="auth.logout()" aria-label="Sign out" title="Sign out">
              <fd-icon name="logout" />
            </button>
          </div>
        </div>
      </aside>

      <div class="scrim" (click)="navOpen.set(false)"></div>

      <div class="main-col">
        <header class="topbar">
          <button class="btn btn-ghost btn-icon" type="button" (click)="navOpen.set(true)" aria-label="Open navigation">
            <fd-icon name="menu" />
          </button>
          <span class="top-brand">Fraud Desk</span>
        </header>
        <main id="main" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .skip {
      position: absolute;
      left: -999px;
      top: 8px;
      z-index: 100;
      background: var(--panel);
      padding: 8px 12px;
      border-radius: var(--r-md);
    }
    .skip:focus {
      left: 8px;
    }
    .layout {
      display: grid;
      grid-template-columns: 248px 1fr;
      min-height: 100vh;
    }
    .side {
      position: sticky;
      top: 0;
      height: 100vh;
      display: flex;
      flex-direction: column;
      background: var(--rail);
      color: var(--rail-ink);
      padding: 18px 12px 14px;
      overflow-y: auto;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 4px 10px 18px;
      color: var(--rail-ink);
      text-decoration: none;
      font-weight: 700;
      font-size: var(--t-md);
      letter-spacing: -0.01em;
    }
    .mark {
      width: 30px;
      height: 30px;
      color: #8fc3ad;
    }
    nav {
      display: grid;
      gap: 18px;
      flex: 1;
      align-content: start;
    }
    .group {
      display: grid;
      gap: 2px;
    }
    .group-label {
      padding: 0 10px 6px;
      font-size: var(--t-xs);
      color: var(--rail-muted);
    }
    nav a {
      display: flex;
      align-items: center;
      gap: 10px;
      height: 36px;
      padding: 0 10px;
      border-radius: var(--r-md);
      color: #c9d3ce;
      text-decoration: none;
      font-weight: 500;
    }
    nav a:hover {
      background: rgb(255 255 255 / 0.06);
      color: #fff;
    }
    nav a.active {
      background: rgb(143 195 173 / 0.16);
      color: #fff;
      box-shadow: inset 3px 0 0 #8fc3ad;
    }
    nav a:focus-visible {
      outline-color: #8fc3ad;
    }
    .badge {
      margin-left: auto;
      min-width: 22px;
      height: 20px;
      padding: 0 6px;
      border-radius: 999px;
      background: #e3587a;
      color: #fff;
      font-size: var(--t-xs);
      font-weight: 650;
      display: grid;
      place-items: center;
    }
    .me {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 12px 6px 0 10px;
      border-top: 1px solid rgb(255 255 255 / 0.08);
    }
    .who {
      display: grid;
      min-width: 0;
    }
    .who strong {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .who span {
      font-size: var(--t-xs);
      color: var(--rail-muted);
    }
    .me-actions {
      display: flex;
    }
    .me .btn-ghost {
      --btn-ink: #c9d3ce;
    }
    .me .btn-ghost:hover {
      background: rgb(255 255 255 / 0.08);
      border-color: transparent;
    }
    .main-col {
      min-width: 0;
    }
    main:focus {
      outline: none;
    }
    .topbar,
    .scrim {
      display: none;
    }

    @media (max-width: 960px) {
      .layout {
        grid-template-columns: 1fr;
      }
      .side {
        position: fixed;
        inset: 0 auto 0 0;
        width: 272px;
        z-index: 60;
        transform: translateX(-100%);
        transition: transform 0.2s ease;
      }
      .nav-open .side {
        transform: none;
      }
      .nav-open .scrim {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 55;
        background: rgb(15 25 21 / 0.45);
      }
      .topbar {
        display: flex;
        align-items: center;
        gap: 8px;
        height: 52px;
        padding: 0 8px;
        border-bottom: 1px solid var(--rule);
        background: var(--panel);
        position: sticky;
        top: 0;
        z-index: 40;
      }
      .top-brand {
        font-weight: 700;
      }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected readonly navOpen = signal(false);
  protected readonly openAlerts = signal(0);

  protected readonly nav = computed(() =>
    NAV.map((g) => ({ ...g, items: g.items.filter((i) => this.auth.hasAnyRole(...i.roles)) })).filter(
      (g) => g.items.length,
    ),
  );
  protected readonly roleLabel = computed(() => this.auth.roles().map(humanize).join(', '));
  protected readonly themeIcon = computed(() =>
    ({ system: 'monitor', light: 'sun', dark: 'moon' })[this.theme.choice()],
  );
  protected readonly themeLabel = computed(
    () => `Theme: ${this.theme.choice()}. Select to switch.`,
  );

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.navOpen.set(false));

    if (this.auth.isStaff()) {
      const refresh = () =>
        this.api.alerts({ status: 'OPEN', size: 1 }).subscribe({
          next: (p) => this.openAlerts.set(p.totalElements),
          error: () => void 0,
        });
      refresh();
      const timer = setInterval(refresh, 20_000);
      inject(DestroyRef).onDestroy(() => clearInterval(timer));
    }
  }
}
