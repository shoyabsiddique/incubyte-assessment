import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { AppConfigService } from './core/config/app-config.service';

const FEATURES: ReadonlyArray<{ path: string; heading: string; protectedPage: boolean }> = [
  { path: 'dashboard', heading: 'Dashboard', protectedPage: true },
  { path: 'employees', heading: 'Employees', protectedPage: true },
  { path: 'compensation', heading: 'Compensation', protectedPage: true },
  { path: 'anomalies', heading: 'Potential anomalies for HR review', protectedPage: true },
  { path: 'simulator', heading: 'Salary simulator', protectedPage: true },
  { path: 'audit', heading: 'Audit history', protectedPage: true },
  { path: 'exchange-rates', heading: 'Exchange rates', protectedPage: true },
  { path: 'auth', heading: 'Sign in', protectedPage: false },
];

describe('app routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: 'http://api.test/api/v1' } },
      ],
    });
  });

  function signIn() {
    TestBed.inject(AuthService).establishSession('tok', {
      id: 'u-1',
      email: 'priya@acme.example',
      displayName: 'Priya Nair',
    });
  }

  async function open(url: string): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  for (const feature of FEATURES) {
    it(`lazy-loads the ${feature.heading} page at /${feature.path}`, async () => {
      signIn();

      const page = await open(`/${feature.path}`);

      expect(page.querySelector('h2')?.textContent).toContain(feature.heading);
    });

    it(`declares /${feature.path} as a lazy route`, () => {
      const route = routes.find((r) => r.path === feature.path);

      expect(route?.loadComponent).toBeTypeOf('function');
      expect(route?.component).toBeUndefined();
    });

    it(`sets the browser tab title for /${feature.path}`, async () => {
      signIn();
      await open(`/${feature.path}`);

      expect(document.title).toBe(`${feature.heading} | ACME Salary Management`);
    });
  }

  for (const feature of FEATURES.filter((f) => f.protectedPage)) {
    it(`sends a signed-out visitor from /${feature.path} to the sign-in page`, async () => {
      const page = await open(`/${feature.path}`);

      expect(TestBed.inject(Router).url).toBe(`/auth?returnUrl=%2F${feature.path}`);
      expect(page.querySelector('h2')?.textContent).toContain('Sign in');
    });
  }

  it('lets a signed-out visitor open the sign-in page', async () => {
    const page = await open('/auth');

    expect(TestBed.inject(Router).url).toBe('/auth');
    expect(page.querySelector('h2')?.textContent).toContain('Sign in');
  });

  it('shows the not-found view for unknown paths below a known page', async () => {
    signIn();

    const page = await open('/employees/123/unknown');

    expect(page.querySelector('h2')?.textContent).toContain('Page not found');
  });

  it('redirects the root to the dashboard when signed in', async () => {
    signIn();

    const page = await open('/');

    expect(page.querySelector('h2')?.textContent).toContain('Dashboard');
  });

  it('redirects the root to the sign-in page when signed out', async () => {
    await open('/');

    expect(TestBed.inject(Router).url).toBe('/auth?returnUrl=%2Fdashboard');
  });

  it('shows a not-found view for unknown paths', async () => {
    const page = await open('/no-such-page');

    expect(page.querySelector('h2')?.textContent).toContain('Page not found');
  });

  describe('the employee detail route', () => {
    it('is a lazy route', () => {
      const route = routes.find((r) => r.path === 'employees/:id');

      expect(route?.loadComponent).toBeTypeOf('function');
      expect(route?.canActivate).toBeDefined();
    });

    it('opens the detail page for a signed-in user and sets the tab title', async () => {
      signIn();

      const page = await open('/employees/e1b2c3d4-0000-4000-8000-000000000001');

      expect(page.querySelector('a.back')).not.toBeNull();
      expect(document.title).toBe('Employee | ACME Salary Management');
    });

    it('sends a signed-out visitor to sign in and remembers the page', async () => {
      await open('/employees/e1b2c3d4-0000-4000-8000-000000000001');

      expect(TestBed.inject(Router).url).toBe(
        '/auth?returnUrl=%2Femployees%2Fe1b2c3d4-0000-4000-8000-000000000001',
      );
    });
  });

  describe('the add employee route', () => {
    it('is a guarded lazy route that comes before the employee id route', () => {
      const index = routes.findIndex((r) => r.path === 'employees/new');

      expect(index).toBeGreaterThan(-1);
      expect(index).toBeLessThan(routes.findIndex((r) => r.path === 'employees/:id'));
      expect(routes[index].canActivate).toBeDefined();
      expect(routes[index].loadComponent).toBeTypeOf('function');
    });

    it('opens the form with a tab title for a signed-in user', async () => {
      signIn();

      const page = await open('/employees/new');

      expect(page.querySelector('app-employee-form')).not.toBeNull();
      expect(document.title).toBe('Add employee | ACME Salary Management');
    });
  });
});
