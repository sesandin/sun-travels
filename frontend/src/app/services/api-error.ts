import { HttpErrorResponse } from '@angular/common/http';

export function apiError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'Something went wrong. Please try again.';
  if (error.status === 0) return 'Cannot reach Sun Travels API. Check that the backend is running on localhost:8080 and allows this frontend connection.';
  const body = error.error;
  const messages: string[] = [];
  if (error.status === 400 && body && typeof body === 'object') {
    const errors = body.errors ?? body.fieldErrors;
    if (Array.isArray(errors)) {
      for (const item of errors) {
        if (typeof item === 'string') messages.push(item);
        else if (typeof item?.defaultMessage === 'string') messages.push(`${item.field ?? 'Field'}: ${item.defaultMessage}`);
        else if (typeof item?.message === 'string') messages.push(`${item.field ?? 'Field'}: ${item.message}`);
      }
    } else if (errors && typeof errors === 'object') {
      for (const [field, message] of Object.entries(errors)) if (typeof message === 'string') messages.push(`${field}: ${message}`);
    }
  }
  if (messages.length) return messages.join(' ');
  if ([400, 404, 409].includes(error.status) && typeof body?.message === 'string' && body.message.length < 500) return body.message;
  switch (error.status) {
    case 400: return 'The hotel details were rejected. Check the fields and try again.';
    case 404: return 'This hotel could not be found. Refresh the list to get the latest records.';
    case 409: return 'These details conflict with an existing record. Review the hotel information and try again.';
    default: return 'The server could not complete this request. Please try again shortly.';
  }
}
