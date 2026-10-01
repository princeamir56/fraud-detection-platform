import { HttpErrorResponse } from '@angular/common/http';
import Swal, { SweetAlertIcon } from 'sweetalert2';
import { ApiError, Resolution } from './models';

/** One place that styles every SweetAlert2 dialog to match the console. */
const dialog = Swal.mixin({
  customClass: {
    popup: 'fd-swal',
    confirmButton: 'btn btn-primary',
    cancelButton: 'btn',
    denyButton: 'btn btn-danger',
  },
  buttonsStyling: false,
  reverseButtons: true,
  showClass: { popup: 'swal2-show' },
  focusCancel: false,
});

const toaster = Swal.mixin({
  toast: true,
  position: 'bottom-end',
  showConfirmButton: false,
  timer: 3600,
  timerProgressBar: true,
  customClass: { popup: 'fd-toast' },
  didOpen: (el) => {
    el.addEventListener('mouseenter', Swal.stopTimer);
    el.addEventListener('mouseleave', Swal.resumeTimer);
  },
});

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

export function toast(title: string, text?: string, icon: SweetAlertIcon = 'success'): void {
  void toaster.fire({ title, text, icon });
}

/** Turns any HTTP failure into a message that says what happened and what to do. */
export function describeError(err: unknown): { title: string; detail: string } {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiError> | null;
    if (err.status === 0) {
      return { title: 'Can’t reach the platform', detail: 'The API gateway on port 8080 isn’t answering. Check that the stack is running.' };
    }
    if (err.status === 400 && body?.violations?.length) {
      return {
        title: 'Some fields need attention',
        detail: body.violations.map((v) => `${v.field}: ${v.message}`).join('\n'),
      };
    }
    if (err.status === 403) {
      return { title: 'Your role can’t do this', detail: 'Ask an admin for access, or sign in with a different account.' };
    }
    if (err.status === 429) {
      return { title: 'Too many requests', detail: 'The gateway rate limit was hit. Wait a minute, then try again.' };
    }
    const ref = body?.correlationId ? ` Reference: ${body.correlationId}` : '';
    return {
      title: body?.message && err.status < 500 ? body.message : 'The request failed',
      detail: err.status >= 500 ? `The service returned ${err.status}.${ref}` : ref.trim(),
    };
  }
  return { title: 'Something went wrong', detail: String(err) };
}

export function toastError(err: unknown): void {
  const { title, detail } = describeError(err);
  void toaster.fire({ title, text: detail, icon: 'error', timer: 6000 });
}

export async function confirmAction(opts: {
  title: string;
  text: string;
  confirm: string;
  danger?: boolean;
}): Promise<boolean> {
  const res = await dialog.fire({
    title: opts.title,
    text: opts.text,
    showCancelButton: true,
    confirmButtonText: opts.confirm,
    cancelButtonText: 'Cancel',
    customClass: {
      popup: 'fd-swal',
      confirmButton: opts.danger ? 'btn btn-danger' : 'btn btn-primary',
      cancelButton: 'btn',
    },
  });
  return res.isConfirmed;
}

export async function promptResolution(alertTitle: string): Promise<{ resolution: Resolution; notes: string } | null> {
  const res = await dialog.fire<{ resolution: Resolution; notes: string }>({
    title: 'Resolve alert',
    html: `
      <p>${escapeHtml(alertTitle)}</p>
      <div class="fd-swal-form">
        <div class="fd-choice" role="radiogroup" aria-label="Outcome">
          <label><input type="radio" name="fd-res" value="CONFIRMED_FRAUD">
            <span><strong>Confirmed fraud</strong><small>The activity was malicious. Keep the account under watch.</small></span></label>
          <label><input type="radio" name="fd-res" value="FALSE_POSITIVE">
            <span><strong>False positive</strong><small>Legitimate activity the rules flagged by mistake.</small></span></label>
          <label><input type="radio" name="fd-res" value="DISMISSED">
            <span><strong>Dismissed</strong><small>Duplicate, or not enough evidence either way.</small></span></label>
        </div>
        <label class="field"><span>Case notes</span>
          <textarea id="fd-notes" class="textarea" maxlength="2000" placeholder="What did you check, and what happens next?"></textarea>
        </label>
      </div>`,
    showCancelButton: true,
    confirmButtonText: 'Resolve alert',
    cancelButtonText: 'Cancel',
    focusConfirm: false,
    preConfirm: () => {
      const picked = document.querySelector<HTMLInputElement>('input[name="fd-res"]:checked');
      if (!picked) {
        Swal.showValidationMessage('Choose an outcome to resolve the alert.');
        return false;
      }
      const notes = document.querySelector<HTMLTextAreaElement>('#fd-notes')?.value.trim() ?? '';
      return { resolution: picked.value as Resolution, notes };
    },
  });
  return res.isConfirmed && res.value ? res.value : null;
}

export async function promptAmount(title: string, text: string, currency: string): Promise<{ amount: number; reason: string } | null> {
  const res = await dialog.fire<{ amount: number; reason: string }>({
    title,
    html: `
      <p>${escapeHtml(text)}</p>
      <div class="fd-swal-form">
        <label class="field"><span>Amount (${escapeHtml(currency)})</span>
          <input id="fd-amount" class="input" type="number" min="0.01" step="0.01" inputmode="decimal"></label>
        <label class="field"><span>Reason</span>
          <input id="fd-reason" class="input" maxlength="255" placeholder="Shown in the account history"></label>
      </div>`,
    showCancelButton: true,
    confirmButtonText: 'Add funds',
    cancelButtonText: 'Cancel',
    focusConfirm: false,
    didOpen: () => document.querySelector<HTMLInputElement>('#fd-amount')?.focus(),
    preConfirm: () => {
      const amount = Number(document.querySelector<HTMLInputElement>('#fd-amount')?.value);
      if (!Number.isFinite(amount) || amount <= 0) {
        Swal.showValidationMessage('Enter an amount greater than zero.');
        return false;
      }
      const reason = document.querySelector<HTMLInputElement>('#fd-reason')?.value.trim() ?? '';
      return { amount, reason };
    },
  });
  return res.isConfirmed && res.value ? res.value : null;
}

/** Blocking dialog shown while a submitted transaction waits for its verdict. */
export function showScoring(): void {
  void dialog.fire({
    title: 'Scoring transaction',
    html: '<p>Accepted by the gateway. Rules and the risk model are evaluating it now.</p>',
    allowOutsideClick: false,
    allowEscapeKey: false,
    showConfirmButton: false,
    didOpen: () => Swal.showLoading(),
  });
}

export async function showVerdict(opts: {
  title: string;
  html: string;
  icon: SweetAlertIcon;
  viewLabel: string;
}): Promise<boolean> {
  Swal.close();
  const res = await dialog.fire({
    title: opts.title,
    html: opts.html,
    icon: opts.icon,
    showCancelButton: true,
    confirmButtonText: opts.viewLabel,
    cancelButtonText: 'Done',
  });
  return res.isConfirmed;
}

export function closeDialogs(): void {
  Swal.close();
}
