import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

test('an HR Manager builds a dashboard: filters it, adds a chart and drags fields onto it', async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Dashboard' }).click();
  await expect(page.getByRole('heading', { level: 2, name: 'Dashboard' })).toBeVisible();
  const tiles = page.locator('.tile');
  await expect(tiles).toHaveCount(4);
  await expect(tiles.first().locator('rect.bar').first()).toBeVisible();
  await expect(tiles.first().locator('.widget-title')).toHaveText('Headcount by country');

  await page.locator('select[name="country"]').selectOption('INDIA');
  await expect(tiles.first().locator('rect.bar')).toHaveCount(1);

  await page.locator('select[name="country"]').selectOption('');
  await page.getByRole('button', { name: 'Add chart' }).click();
  await expect(tiles).toHaveCount(5);
  const added = tiles.nth(4);
  await added.getByRole('button', { name: 'Configure this chart' }).click();
  // A drop needs both ends on screen: the fields panel is sticky, so bring the chart's boxes to it.
  await added.locator('.well-x').scrollIntoViewIfNeeded();
  await page.locator('.field-chip[data-key="jobTitle"]').dragTo(added.locator('.well-x'));
  await expect(added.locator('.widget-title')).toHaveText('Headcount by job title');
  await added.locator('.well-value').scrollIntoViewIfNeeded();
  await page.locator('.field-chip[data-key="salaryUsd"]').dragTo(added.locator('.well-value'));
  await expect(added.locator('.widget-title')).toContainText('salary (USD) by job title');
  await added.locator('select[name="series"]').selectOption('country');
  await expect(added.locator('.legend li').first()).toBeVisible();

  for (const type of ['stackedColumn', 'area', 'pie', 'heatmap', 'line', 'bar', 'kpi', 'table']) {
    await added.locator(`button.chart-type[data-chart="${type}"]`).click();
    await expect(added.locator('svg.chart, .kpi, table.chart-table').first()).toBeVisible();
  }
  await added.locator('button.chart-type[data-chart="column"]').click();

  await added.getByRole('button', { name: 'Move this chart earlier' }).click();
  await expect(tiles.nth(3).locator('.widget-title')).toContainText('by job title');
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
