import { Pipe, PipeTransform } from '@angular/core';
import { Decision, Severity, TransactionStatus } from './models';

/** The interface copy is English, so numbers, money and dates follow it rather than the OS locale. */
export const LOCALE = 'en-GB';

export function severityFromScore(score: number): Severity {
  if (score >= 80) return 'CRITICAL';
  if (score >= 60) return 'HIGH';
  if (score >= 30) return 'MEDIUM';
  return 'LOW';
}

export function humanize(value: string | null | undefined): string {
  if (!value) return '—';
  const s = value.replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function money(amount: number | null | undefined, currency = 'EUR'): string {
  if (amount === null || amount === undefined) return '—';
  try {
    return new Intl.NumberFormat(LOCALE, { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

export function relativeTime(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const diff = (new Date(iso).getTime() - now) / 1000;
  const rtf = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' });
  const abs = Math.abs(diff);
  if (abs < 45) return 'just now';
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
  if (abs < 86400 * 30) return rtf.format(Math.round(diff / 86400), 'day');
  return new Date(iso).toLocaleDateString(LOCALE, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function shortId(id: string | null | undefined): string {
  if (!id) return '—';
  return id.length > 12 ? `${id.slice(0, 8)}` : id;
}

export function statusTone(status: string | null | undefined): 'good' | 'warn' | 'bad' | 'pending' | '' {
  switch (status as TransactionStatus | Decision | string) {
    case 'COMPLETED':
    case 'ALLOW':
    case 'ACTIVE':
    case 'SENT':
    case 'RESOLVED':
      return 'good';
    case 'FLAGGED':
    case 'UNDER_REVIEW':
    case 'REVIEW':
    case 'ACKNOWLEDGED':
    case 'FROZEN':
      return 'warn';
    case 'BLOCKED':
    case 'REJECTED':
    case 'BLOCK':
    case 'FAILED':
    case 'CLOSED':
    case 'OPEN':
      return 'bad';
    case 'PENDING':
    case 'VALIDATED':
      return 'pending';
    default:
      return '';
  }
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency?: string | null): string {
    return money(amount, currency ?? 'EUR');
  }
}

@Pipe({ name: 'ago' })
export class AgoPipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return relativeTime(iso);
  }
}

@Pipe({ name: 'humanize' })
export class HumanizePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return humanize(value);
  }
}

@Pipe({ name: 'shortId' })
export class ShortIdPipe implements PipeTransform {
  transform(id: string | null | undefined): string {
    return shortId(id);
  }
}

@Pipe({ name: 'tone' })
export class TonePipe implements PipeTransform {
  transform(status: string | null | undefined): string {
    const t = statusTone(status);
    return t ? `status status-${t}` : 'status';
  }
}
