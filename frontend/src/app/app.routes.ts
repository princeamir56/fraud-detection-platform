import { Routes } from '@angular/router';
import { requireRole, signedIn, signedOut } from './core/guards';
import { Shell } from './layout/shell';

const STAFF = ['ADMIN', 'ANALYST', 'INVESTIGATOR'] as const;

export const routes: Routes = [
  {
    path: 'sign-in',
    canActivate: [signedOut],
    title: 'Sign in | Fraud Desk',
    loadComponent: () => import('./pages/sign-in').then((m) => m.SignIn),
  },
  {
    path: '',
    component: Shell,
    canActivate: [signedIn],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      {
        path: 'overview',
        title: 'Overview | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/overview').then((m) => m.Overview),
      },
      {
        path: 'alerts',
        title: 'Alerts | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/alerts').then((m) => m.Alerts),
      },
      {
        path: 'transactions',
        title: 'Transactions | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/transactions').then((m) => m.Transactions),
      },
      {
        path: 'rules',
        title: 'Scoring rules | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/rules').then((m) => m.Rules),
      },
      {
        path: 'customers',
        title: 'Customers | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/customers').then((m) => m.Customers),
      },
      {
        path: 'customers/:id',
        title: 'Customer | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/customer-detail').then((m) => m.CustomerDetail),
      },
      {
        path: 'audit',
        title: 'Audit trail | Fraud Desk',
        canActivate: [requireRole('ADMIN', 'INVESTIGATOR')],
        loadComponent: () => import('./pages/audit').then((m) => m.Audit),
      },
      {
        path: 'notifications',
        title: 'Notifications | Fraud Desk',
        canActivate: [requireRole(...STAFF)],
        loadComponent: () => import('./pages/notifications').then((m) => m.Notifications),
      },
      {
        path: 'team',
        title: 'Team access | Fraud Desk',
        canActivate: [requireRole('ADMIN')],
        loadComponent: () => import('./pages/team').then((m) => m.Team),
      },
      {
        path: 'my-money',
        title: 'My money | Fraud Desk',
        canActivate: [requireRole('CUSTOMER')],
        loadComponent: () => import('./pages/my-money').then((m) => m.MyMoney),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
