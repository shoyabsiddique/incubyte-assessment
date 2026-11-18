import { expect, type Page } from '@playwright/test';

export const EMAIL = process.env['E2E_EMAIL'] ?? 'priya.nair@acme.example';
const PASSWORD = process.env['E2E_PASSWORD'] ?? 'LocalDemo#2026';

export async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Email').fill(EMAIL);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
}

export async function choose(page: Page, label: string, option: string) {
  await page.getByLabel(label).click({ force: true });
  await expect(page.getByRole('listbox')).toBeVisible();
  await page.getByRole('option', { name: option, exact: true }).click();
  await expect(page.getByRole('listbox')).toBeHidden();
}

/** Adds an Australian employee on 100,000 AUD through the UI and lands on their page. */
export async function addEmployee(page: Page, unique: string) {
  await page.getByRole('link', { name: 'Employees' }).click();
  await page.getByRole('link', { name: 'Add employee' }).click();
  await page.getByLabel('First name').fill('Grace');
  await page.getByLabel('Last name').fill(`Hopper${unique}`);
  await page.getByLabel('Work email').fill(`e2e.${unique}@acme.example`);
  await page.getByLabel('Job title').fill('Software Engineer');
  await page.getByLabel('Department').fill('Engineering');
  await page.getByLabel('City').fill('Sydney');
  await choose(page, 'Country', 'Australia');
  await choose(page, 'Employment type', 'Full time');
  await choose(page, 'Status', 'Active');
  await page.getByLabel('Hire date').fill('2024-03-01');
  await page.getByLabel('Annual gross base salary').fill('100000');
  await page.getByLabel('Salary start date').fill('2024-03-01');
  await page.getByRole('button', { name: 'Add employee' }).click();
  await expect(page.getByRole('heading', { level: 2, name: `Grace Hopper${unique}` })).toBeVisible();
}
