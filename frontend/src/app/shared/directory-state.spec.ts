import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { DirectoryPage, DirectoryState } from './directory-state';
import { directoryPage } from '../testing/directory-page';

describe('Server directory state', () => {
  it('preserves partial pages and total counts supplied by the backend', () => {
    const response = new Subject<DirectoryPage<number>>();
    const fetch = vi.fn(() => response);
    const state = new DirectoryState(fetch, TestBed.inject(DestroyRef));
    state.load(2);
    response.next(directoryPage([21, 22, 23, 24, 25], 2, 25));
    expect(fetch).toHaveBeenCalledWith(2);
    expect(state.items()).toHaveLength(5);
    expect(state.totalElements()).toBe(25);
    expect(state.totalPages()).toBe(3);
    expect(state.page()).toBe(2);
  });

  it('cancels stale requests and resets filters to the first page', () => {
    const old = new Subject<DirectoryPage<number>>();
    const current = new Subject<DirectoryPage<number>>();
    let query = '';
    const fetch = vi.fn().mockReturnValueOnce(old).mockReturnValueOnce(current);
    const state = new DirectoryState<number>(fetch, TestBed.inject(DestroyRef), () => query);
    state.load(2);
    query = 'Colombo';
    state.load(0);
    expect(state.page()).toBe(0);
    expect(old.observers.length).toBe(0);
    current.next(directoryPage([3], 0, 1));
    old.next(directoryPage([99], 2, 25));
    expect(state.items()).toEqual([3]);
    expect(state.totalElements()).toBe(1);
  });

  it('deduplicates in-flight requests and allows retry after failure', () => {
    const failed = new Subject<DirectoryPage<number>>();
    const retry = new Subject<DirectoryPage<number>>();
    const fetch = vi.fn().mockReturnValueOnce(failed).mockReturnValueOnce(retry);
    const state = new DirectoryState<number>(fetch, TestBed.inject(DestroyRef));
    state.load();
    state.load();
    expect(fetch).toHaveBeenCalledTimes(1);
    failed.error(
      new HttpErrorResponse({
        status: 500,
        error: { message: 'Database details must stay private' },
      }),
    );
    expect(state.loading()).toBe(false);
    expect(state.error()).toBe(
      'The server could not complete this request. Please try again shortly.',
    );
    state.load();
    retry.next(directoryPage([]));
    expect(state.error()).toBe('');
    expect(state.totalElements()).toBe(0);
    expect(state.totalPages()).toBe(0);
  });
});
