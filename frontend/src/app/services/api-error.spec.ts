import { HttpErrorResponse } from '@angular/common/http';
import { apiError } from './api-error';

describe('API error messages', () => {
  it('uses a friendly field label for malformed validation field names', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        errors: [
          { field: { name: 'customer' }, defaultMessage: 'Required' },
          { field: ['dates'], message: 'Invalid dates' },
        ],
      },
    });
    expect(apiError(error)).toBe('Field: Required Field: Invalid dates');
    expect(apiError(error)).not.toContain('[object Object]');
  });
  it('preserves mixed validation arrays, field names and message precedence', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        errors: [
          'First message',
          { field: 'name', defaultMessage: 'Required', message: 'Fallback' },
          { message: 'Invalid value' },
          null,
          12,
        ],
      },
    });
    expect(apiError(error)).toBe('First message name: Required Field: Invalid value');
  });
  it('falls back to the business message when validation entries contain no messages', () => {
    expect(
      apiError(
        new HttpErrorResponse({
          status: 400,
          error: {
            fieldErrors: [null, {}, { message: 12 }],
            message: 'Choose valid dates',
          },
        }),
      ),
    ).toBe('Choose valid dates');
  });
  it('preserves validation and missing-record messages', () => {
    expect(
      apiError(
        new HttpErrorResponse({
          status: 400,
          error: { errors: { name: 'Hotel name is required' } },
        }),
      ),
    ).toContain('Hotel name is required');
    expect(
      apiError(new HttpErrorResponse({ status: 404, error: { message: 'Hotel 7 not found' } })),
    ).toBe('Hotel 7 not found');
  });
  it('provides useful fallbacks without exposing server internals', () => {
    expect(apiError(new HttpErrorResponse({ status: 400 }))).toContain('rejected');
    expect(apiError(new HttpErrorResponse({ status: 409 }))).toContain('conflict');
    expect(apiError(new HttpErrorResponse({ status: 401 }))).toContain('session');
    expect(apiError(new HttpErrorResponse({ status: 403 }))).toContain('permission');
    expect(
      apiError(new HttpErrorResponse({ status: 500, error: { message: 'SQL secret' } })),
    ).not.toContain('SQL');
    expect(apiError(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach');
  });
});
