import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

test('an HR Manager reads pay by country and filters it to one country', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Dashboard' }).click();
  await expect(page.getByRole('heading', { level: 2, name: 'Dashboard' })).toBeVisible();
  const rows = page.locator('table.stats tbody tr');
  await expect(rows).toHaveCount(8);
  await expect(page.getByText('Average (local)')).toHaveCount(0);

  await page.locator('select[name="country"]').selectOption('INDIA');
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('India');
  await expect(rows.first()).toContainText('INR');
  await expect(page.getByText('Average (local)')).toBeVisible();
});

test('an HR Manager sees the highest and lowest paid in a department', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Compensation' }).click();
  await page.locator('select[name="department"]').selectOption('Engineering');
  await expect(page.locator('article.department')).toHaveCount(1);
  await expect(page.locator('section.highest li')).toHaveCount(5);
  await expect(page.locator('section.lowest li')).toHaveCount(5);
  await page.locator('section.highest li a').first().click();
  await expect(page.getByRole('heading', { level: 2 }).first()).toBeVisible();
  await expect(page).toHaveURL(/\/employees\/[0-9a-f-]{36}$/);
});
