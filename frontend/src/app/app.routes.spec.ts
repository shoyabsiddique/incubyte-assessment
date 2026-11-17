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
  { path: 'anomalies', heading: 'Anomalies', protectedPage: true },
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
});
