import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Angular supplies compiled modules in memory. Disk caching can replay an older bundle.
    fsModuleCache: false,
  },
});
