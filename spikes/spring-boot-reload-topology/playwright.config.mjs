import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [['list']],
  outputDir: 'build/playwright-results',
  use: {
    browserName: 'chromium',
    headless: true,
  },
});
