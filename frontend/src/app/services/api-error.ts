import { HttpErrorResponse } from '@angular/common/http';

export function apiError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'Something went wrong. Please try again.';
  if (error.status === 0)
    return 'Cannot reach Sun Travels API. Check that the backend and development proxy are running.';
  const body = error.error;
  let messages: string[] = [];
  if (error.status === 400 && body && typeof body === 'object') {
    messages = validationMessages(body.errors ?? body.fieldErrors);
  }
  if (messages.length) return messages.join(' ');
  if (
    [400, 401, 403, 404, 409].includes(error.status) &&
    typeof body?.message === 'string' &&
    body.message.length < 500
  )
    return body.message;
  switch (error.status) {
    case 400:
      return 'The hotel details were rejected. Check the fields and try again.';
    case 401:
      return 'Your session has expired. Sign in again.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'This hotel could not be found. Refresh the list to get the latest records.';
    case 409:
      return 'These details conflict with an existing record. Review the hotel information and try again.';
    default:
      return 'The server could not complete this request. Please try again shortly.';
  }
}

function validationMessages(errors: unknown): string[] {
  const messages: string[] = [];
  if (Array.isArray(errors)) {
    for (const item of errors) {
      const message = validationMessage(item);
      if (message !== null) messages.push(message);
    }
  } else if (errors && typeof errors === 'object') {
    for (const [field, message] of Object.entries(errors)) {
      if (typeof message === 'string') messages.push(`${field}: ${message}`);
    }
  }
  return messages;
}

function validationMessage(item: unknown): string | null {
  if (typeof item === 'string') return item;
  const error = item as { field?: unknown; defaultMessage?: unknown; message?: unknown } | null;
  const rawField: unknown = error?.field;
  const field: string = typeof rawField === 'string' ? rawField : 'Field';
  const defaultMessage: unknown = error?.defaultMessage;
  if (typeof defaultMessage === 'string') return `${field}: ${defaultMessage}`;
  const message: unknown = error?.message;
  if (typeof message === 'string') return `${field}: ${message}`;
  return null;
}
