import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

function amount(text: string): number {
  return Number(text.replace(/[^0-9.]/g, ''));
}

test('a 10% organisation-wide simulation projects payroll at current payroll times 1.10', async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Salary simulator' }).click();
  await expect(page.locator('.read-only')).toContainText('changes no salaries');

  await page.locator('input[name="percent"]').fill('10');
  await page.getByRole('button', { name: 'Run simulation' }).click();

  const results = page.locator('.results');
  await expect(results).toBeVisible();
  const current = amount(await results.locator('.current').innerText());
  const projected = amount(await results.locator('.projected').innerText());
  const additional = amount(await results.locator('.additional').innerText());
  expect(current).toBeGreaterThan(0);
  expect(Math.abs(projected - current * 1.1)).toBeLessThan(0.02);
  expect(Math.abs(additional - current * 0.1)).toBeLessThan(0.02);

  await page.locator('input[name="percent"]').fill('11');
  await expect(results).toHaveCount(0);
});
