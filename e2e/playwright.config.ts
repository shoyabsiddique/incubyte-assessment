import { defineConfig } from '@playwright/test';

// Locally the installed Chrome is used so nothing has to be downloaded; CI installs Chromium.
const useInstalledChrome = !process.env['CI'];

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['github'], ['list']] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    channel: useInstalledChrome ? 'chrome' : undefined,
    trace: 'retain-on-failure',
  },
});
