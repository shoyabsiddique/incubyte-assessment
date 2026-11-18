import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { AppConfigService } from './core/config/app-config.service';
import { routes } from './app.routes';

const USER = { id: 'u-1', email: 'priya@acme.example', displayName: 'Priya Nair' };

// The first test to load a lazy route compiles it, which can take longer than the 5 second default.
vi.setConfig({ testTimeout: 20_000 });

describe('App shell', () => {
  async function setUp(signedIn: boolean, withRoutes = false) {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(withRoutes ? routes : []),
        { provide: AppConfigService, useValue: { apiBaseUrl: 'http://api.test/api/v1' } },
      ],
    }).compileComponents();
    const auth = TestBed.inject(AuthService);
    if (signedIn) {
      auth.establishSession('tok', USER);
    }
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, shell: fixture.nativeElement as HTMLElement, auth };
  }

  it('shows the product name as the page heading', async () => {
    const { shell } = await setUp(false);

    expect(shell.querySelector('mat-toolbar h1')?.textContent).toContain('ACME Salary Management');
  });

  it('shows no navigation and no sign-out while signed out', async () => {
    const { shell } = await setUp(false);

    expect(shell.querySelectorAll('nav a')).toHaveLength(0);
    expect(shell.textContent).not.toContain('Sign out');
  });

  it('offers navigation to every working area once signed in', async () => {
    const { shell } = await setUp(true);

    const links = Array.from(shell.querySelectorAll('nav a')).map((a) => ({
      label: a.textContent?.trim(),
      href: a.getAttribute('href'),
    }));

    expect(links).toEqual([
      { label: 'Dashboard', href: '/dashboard' },
      { label: 'Employees', href: '/employees' },
      { label: 'Compensation', href: '/compensation' },
      { label: 'Anomalies', href: '/anomalies' },
      { label: 'Salary simulator', href: '/simulator' },
      { label: 'Audit history', href: '/audit' },
      { label: 'Exchange rates', href: '/exchange-rates' },
    ]);
  });

  it('shows who is signed in and a sign-out button', async () => {
    const { shell } = await setUp(true);

    expect(shell.querySelector('mat-toolbar')?.textContent).toContain('priya@acme.example');
    expect(shell.querySelector('button.sign-out')?.textContent).toContain('Sign out');
  });

  it('signs out when the sign-out button is pressed', async () => {
    const { shell, auth } = await setUp(true);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (shell.querySelector('button.sign-out') as HTMLButtonElement).click();

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('marks only the current page in the navigation', async () => {
    const { fixture, shell } = await setUp(true, true);
    await TestBed.inject(Router).navigateByUrl('/employees');
    await fixture.whenStable();

    const current = Array.from(shell.querySelectorAll('nav a[aria-current="page"]')).map((a) =>
      a.textContent?.trim(),
    );

    expect(current).toEqual(['Employees']);
  });

  it('keeps Employees highlighted while an individual employee is open', async () => {
    const { fixture, shell } = await setUp(true, true);
    await TestBed.inject(Router).navigateByUrl('/employees/e1b2c3d4-0000-4000-8000-000000000001');
    await fixture.whenStable();

    const current = Array.from(shell.querySelectorAll('nav a[aria-current="page"]')).map((a) =>
      a.textContent?.trim(),
    );

    expect(current).toEqual(['Employees']);
  });

  it('has a place where the routed page is rendered', async () => {
    const { shell } = await setUp(false);

    expect(shell.querySelector('router-outlet')).not.toBeNull();
  });
});
