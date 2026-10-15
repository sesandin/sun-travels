import { HttpErrorResponse } from '@angular/common/http';
import { apiError } from './api-error';
describe('API error messages', () => {
  it('preserves validation and missing-record messages', () => {
    expect(apiError(new HttpErrorResponse({ status: 400, error: { errors: { name: 'Hotel name is required' } } }))).toContain('Hotel name is required');
    expect(apiError(new HttpErrorResponse({ status: 404, error: { message: 'Hotel 7 not found' } }))).toBe('Hotel 7 not found');
  });
  it('provides useful fallbacks without exposing server internals', () => {
    expect(apiError(new HttpErrorResponse({ status: 400 }))).toContain('rejected');
    expect(apiError(new HttpErrorResponse({ status: 409 }))).toContain('conflict');
    expect(apiError(new HttpErrorResponse({ status: 401 }))).toContain('session');
    expect(apiError(new HttpErrorResponse({ status: 403 }))).toContain('permission');
    expect(apiError(new HttpErrorResponse({ status: 500, error: { message: 'SQL secret' } }))).not.toContain('SQL');
    expect(apiError(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach');
  });
});
