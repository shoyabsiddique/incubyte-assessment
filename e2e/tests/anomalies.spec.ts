import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

test('an HR Manager opens the anomalies page, filters it and follows a flagged employee', async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Anomalies' }).click();
  await expect(
    page.getByRole('heading', { level: 2, name: 'Potential anomalies for HR review' }),
  ).toBeVisible();
  await expect(page.locator('.rule')).toContainText('more than 20%');
  const rows = page.locator('table.anomalies tbody tr');
  await expect(rows.first()).toBeVisible();
  await expect(rows.first().locator('.deviation')).toHaveText(/^\s*[+-]\d+\.\d{2}%\s*$/);

  await page.locator('select[name="direction"]').selectOption('above');
  await expect(rows.first().locator('.deviation')).toHaveText(/^\s*\+/);

  const name = (await rows.first().locator('a').innerText()).trim();
  await rows.first().locator('a').click();
  await expect(page).toHaveURL(/\/employees\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 2, name })).toBeVisible();
  await expect(page.getByText('Salary history')).toBeVisible();
});
