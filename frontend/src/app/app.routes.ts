import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'employees',
    loadComponent: () =>
      import('./features/employees/employees.page').then((m) => m.EmployeesPage),
  },
  {
    path: 'compensation',
    loadComponent: () =>
      import('./features/compensation/compensation.page').then((m) => m.CompensationPage),
  },
  {
    path: 'anomalies',
    loadComponent: () =>
      import('./features/anomalies/anomalies.page').then((m) => m.AnomaliesPage),
  },
  {
    path: 'simulator',
    loadComponent: () =>
      import('./features/simulator/simulator.page').then((m) => m.SimulatorPage),
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./features/audit/audit.page').then((m) => m.AuditPage),
  },
  {
    path: 'exchange-rates',
    loadComponent: () =>
      import('./features/exchange-rates/exchange-rates.page').then((m) => m.ExchangeRatesPage),
  },
  {
    path: 'auth',
    loadComponent: () =>
      import('./features/auth/auth.page').then((m) => m.SignInPage),
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
