# Fraud Desk (frontend)

Angular 22 console for the fraud-detection platform. Analysts and investigators triage alerts,
see why each transaction scored the way it did, and tune scoring rules. Customers get a small
"My money" view with their accounts and payments.

## Run it

The backend stack must be running (`scripts/up.sh` from the repo root); the gateway is on `:8080`.

```bash
npm install
npm start            # http://localhost:4200, proxies /api to the gateway (proxy.conf.json)
npm run build        # production bundle in dist/frontend
```

Sign in with the seeded admin (`admin` / `admin-change-me`), or register a customer from the
sign-in page.

## What's where

| Path | Who | What |
|------|-----|------|
| `/overview` | staff | Verdict rail (recent transactions on the 0–100 score scale), alert queue, top rules, hourly verdicts |
| `/alerts` | staff | Case queue; investigators and admins take and resolve cases, freeze accounts |
| `/transactions` | staff | Searchable scored transactions; "Test a transaction" sends real traffic through the gateway |
| `/rules` | staff (edit: admin) | Rule weights and on/off, applied to the next transaction |
| `/customers/:id` | staff | Profile, alerts, accounts (open, add funds, freeze), history |
| `/audit` | investigator, admin | Event trail grouped by correlation ID |
| `/notifications` | staff | Messages sent to customers for alerts |
| `/team` | admin | Add staff logins with roles |
| `/my-money` | customer | Own accounts, payments, make a payment |

## Structure

- `src/styles.scss` — design tokens (light and dark), controls, tables, SweetAlert2 theme
- `src/app/core` — API client, auth (JWT in localStorage, auto sign-out on expiry), guards, formatting, SweetAlert2 dialogs
- `src/app/shared` — score scale, verdict rail, transaction drawer and composer, charts (Chart.js), guilloché mark
- `src/app/pages` — one standalone component per route, all lazy-loaded

## Notes on the backend it talks to

- The global transaction list comes from `/api/v1/search/transactions` (Elasticsearch), because
  `GET /api/v1/transactions` without `customerId` or `accountId` returns 500.
- Rule-by-rule detail (`fraud-events`) exists only for HIGH and CRITICAL verdicts; the drawer says so
  for lower scores.
- There is no endpoint to list staff users, so Team access only shows people added in the session.
- Completed payments don't change account balances; the transaction service doesn't debit accounts.
