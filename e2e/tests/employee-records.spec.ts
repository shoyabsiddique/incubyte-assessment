import { expect, test, type Page } from '@playwright/test';

const EMAIL = process.env['E2E_EMAIL'] ?? 'priya.nair@acme.example';
const PASSWORD = process.env['E2E_PASSWORD'] ?? 'LocalDemo#2026';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Email').fill(EMAIL);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
}

async function choose(page: Page, label: string, option: string) {
  await page.getByLabel(label).click({ force: true });
  await expect(page.getByRole('listbox')).toBeVisible();
  await page.getByRole('option', { name: option, exact: true }).click();
  await expect(page.getByRole('listbox')).toBeHidden();
}

test('an HR Manager adds an employee, edits them and finds both changes in the audit history', async ({
  page,
}) => {
  const unique = Date.now().toString(36);
  const email = `e2e.${unique}@acme.example`;
  await signIn(page);

  await page.getByRole('link', { name: 'Employees' }).click();
  await page.getByRole('link', { name: 'Add employee' }).click();
  await page.getByLabel('First name').fill('Grace');
  await page.getByLabel('Last name').fill(`Hopper${unique}`);
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Job title').fill('Software Engineer');
  await page.getByLabel('Department').fill('Engineering');
  await page.getByLabel('City').fill('Sydney');
  await choose(page, 'Country', 'Australia');
  await choose(page, 'Employment type', 'Full time');
  await choose(page, 'Status', 'Active');
  await page.getByLabel('Hire date').fill('2024-03-01');
  await page.getByLabel('Annual gross base salary').fill('100000');
  await page.getByLabel('Salary start date').fill('2024-03-01');
  await expect(page.locator('.currency')).toHaveText('AUD');
  await page.getByRole('button', { name: 'Add employee' }).click();

  await expect(page.getByRole('heading', { level: 2, name: `Grace Hopper${unique}` })).toBeVisible();
  await expect(page.locator('.current-salary .local')).toContainText('100,000.00 AUD');

  await page.getByRole('button', { name: 'Edit profile' }).click();
  await page.getByLabel('Job title').fill('Staff Engineer');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.locator('.profile')).toContainText('Staff Engineer');

  await page.getByRole('tab', { name: 'Audit history' }).click();
  const trail = page.locator('app-audit-history table');
  await expect(trail.locator('tr[mat-row]')).toHaveCount(2);
  await expect(trail).toContainText('Update');
  await expect(trail).toContainText('Staff Engineer');
  await expect(trail).toContainText('Create');

  await page.getByRole('link', { name: 'Audit history' }).click();
  await expect(page.getByRole('heading', { level: 2, name: 'Audit history' })).toBeVisible();
  await expect(page.locator('app-audit-history table').first()).toContainText(EMAIL);
});

test('adding an employee with an email that is already used shows the problem next to the field', async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Employees' }).click();
  await page.getByRole('link', { name: 'Add employee' }).click();
  await page.getByLabel('First name').fill('Dup');
  await page.getByLabel('Last name').fill('Licate');
  await page.getByLabel('Work email').fill('charlotte.smith@acme.example');
  await page.getByLabel('Job title').fill('Engineer');
  await page.getByLabel('Department').fill('Engineering');
  await page.getByLabel('City').fill('Sydney');
  await choose(page, 'Country', 'Australia');
  await choose(page, 'Employment type', 'Full time');
  await choose(page, 'Status', 'Active');
  await page.getByLabel('Hire date').fill('2024-03-01');
  await page.getByLabel('Annual gross base salary').fill('90000');
  await page.getByLabel('Salary start date').fill('2024-03-01');
  await page.getByRole('button', { name: 'Add employee' }).click();

  await expect(page.getByText('already uses this work email')).toBeVisible();
  await expect(page).toHaveURL(/\/employees\/new$/);
});
