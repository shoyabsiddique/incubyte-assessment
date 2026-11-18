import { expect, test } from '@playwright/test';
import { addEmployee, signIn } from './helpers';

test('an HR Manager revises a salary and sees the history and the audit trail change', async ({
  page,
}) => {
  const unique = `${Date.now().toString(36)}r`;
  await signIn(page);
  await addEmployee(page, unique);
  await expect(page.locator('.current-salary .local')).toContainText('100,000.00 AUD');

  await page.getByRole('button', { name: 'Revise salary' }).click();
  await expect(page.locator('.reference')).toContainText('100,000.00 AUD');
  await page.getByLabel('New annual gross base salary').fill('120000');
  await page.getByLabel('Effective date').fill('2025-01-01');
  await page.getByRole('button', { name: 'Save revision' }).click();

  await expect(page.locator('.current-salary .local')).toContainText('120,000.00 AUD');
  await expect(page.locator('.notice')).toContainText('Salary revised');
  const history = page.locator('table.history tr[mat-row]');
  await expect(history).toHaveCount(2);
  await expect(history.first()).toContainText('Present');
  await expect(history.first()).toContainText('Current');
  await expect(history.nth(1)).toContainText('Dec 31, 2024');

  await page.getByRole('tab', { name: 'Audit history' }).click();
  const trail = page.locator('app-audit-history table');
  await expect(trail.locator('tr[mat-row]')).toHaveCount(2);
  await expect(trail).toContainText('Revise');
  await expect(trail).toContainText('120,000.00 AUD');
});

test('a revision dated on or before the current salary start is refused next to the date', async ({
  page,
}) => {
  const unique = `${Date.now().toString(36)}d`;
  await signIn(page);
  await addEmployee(page, unique);

  await page.getByRole('button', { name: 'Revise salary' }).click();
  await page.getByLabel('New annual gross base salary').fill('120000');
  await page.getByLabel('Effective date').fill('2024-03-01');
  await page.getByRole('button', { name: 'Save revision' }).click();

  await expect(page.getByText('must be after the current salary')).toBeVisible();
  await expect(page.getByLabel('New annual gross base salary')).toHaveValue('120000');
  await expect(page.locator('.current-salary .local')).toContainText('100,000.00 AUD');
});

test('an HR Manager edits an exchange rate and finds the change in the audit history', async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Exchange rates' }).click();
  const row = page.locator('tr[mat-row]', { hasText: 'AED' });
  await expect(page.locator('tr[mat-row]', { hasText: 'USD' })).toContainText('Fixed');
  const original = (await row.locator('td').nth(1).innerText()).trim();
  const changed = original === '0.2725' ? '0.2726' : '0.2725';

  async function setRate(value: string) {
    await page.getByRole('button', { name: 'Edit AED rate' }).click();
    await page.locator('input.rate-input').fill(value);
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('AED rate saved');
    await expect(row).toContainText(value);
  }

  try {
    await setRate(changed);
    await page.getByRole('link', { name: 'Audit history' }).click();
    await expect(page.locator('app-audit-history table').first()).toContainText('AED rate');
    await expect(page.locator('app-audit-history table').first()).toContainText(changed);
  } finally {
    await page.getByRole('link', { name: 'Exchange rates' }).click();
    await setRate(original);
  }
});
