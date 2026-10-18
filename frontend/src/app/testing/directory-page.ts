import { DirectoryPage } from '../shared/directory-state';

// Builds a paginated directory response fixture for frontend tests.
export function directoryPage<T>(
  items: T[],
  page = 0,
  totalElements = items.length,
  summary: Record<string, unknown> = {},
): DirectoryPage<T> {
  return {
    items,
    page,
    size: 10,
    totalElements,
    totalPages: Math.ceil(totalElements / 10),
    summary: {
      totalRecords: totalElements,
      activeRecords: items.filter((item) => (item as { status?: string }).status === 'ACTIVE')
        .length,
      ...summary,
    },
  };
}
