import { expect, test } from '@playwright/test';

// The documented local demo account; override the password with E2E_PASSWORD.
const EMAIL = process.env['E2E_EMAIL'] ?? 'priya.nair@acme.example';
const PASSWORD = process.env['E2E_PASSWORD'] ?? 'LocalDemo#2026';

test('an HR Manager signs in, finds an employee, reads their pay and signs out', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/auth/);

  await page.getByLabel('Email').fill(EMAIL);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();

  await page.getByRole('link', { name: 'Employees' }).click();
  await page.getByLabel('Search by name, email or Employee ID').fill('EMP-000001');
  await page.getByRole('link', { name: /Charlotte Smith|EMP-000001/ }).first().click();

  await expect(page.getByRole('heading', { level: 2, name: 'Charlotte Smith' })).toBeVisible();
  const current = page.locator('.current-salary');
  await expect(current).toContainText('AUD');
  await expect(current).toContainText('USD');
  await expect(page.locator('table.history tr[mat-row]').first()).toContainText('Present');
  await expect(page.locator('table.history')).toContainText('Current');

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/auth/);
});
