import { Routes } from '@angular/router';
import { APP_NAME } from './app-name';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: `Dashboard | ${APP_NAME}`,
    loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'employees',
    title: `Employees | ${APP_NAME}`,
    loadComponent: () => import('./features/employees/employees.page').then((m) => m.EmployeesPage),
  },
  {
    path: 'compensation',
    title: `Compensation | ${APP_NAME}`,
    loadComponent: () =>
      import('./features/compensation/compensation.page').then((m) => m.CompensationPage),
  },
  {
    path: 'anomalies',
    title: `Anomalies | ${APP_NAME}`,
    loadComponent: () => import('./features/anomalies/anomalies.page').then((m) => m.AnomaliesPage),
  },
  {
    path: 'simulator',
    title: `Salary simulator | ${APP_NAME}`,
    loadComponent: () => import('./features/simulator/simulator.page').then((m) => m.SimulatorPage),
  },
  {
    path: 'audit',
    title: `Audit history | ${APP_NAME}`,
    loadComponent: () => import('./features/audit/audit.page').then((m) => m.AuditPage),
  },
  {
    path: 'exchange-rates',
    title: `Exchange rates | ${APP_NAME}`,
    loadComponent: () =>
      import('./features/exchange-rates/exchange-rates.page').then((m) => m.ExchangeRatesPage),
  },
  {
    path: 'auth',
    title: `Sign in | ${APP_NAME}`,
    loadComponent: () => import('./features/auth/auth.page').then((m) => m.SignInPage),
  },
  {
    path: '**',
    title: `Page not found | ${APP_NAME}`,
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
