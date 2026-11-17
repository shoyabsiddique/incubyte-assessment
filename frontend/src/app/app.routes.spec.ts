import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

const FEATURES: ReadonlyArray<{ path: string; heading: string }> = [
  { path: 'dashboard', heading: 'Dashboard' },
  { path: 'employees', heading: 'Employees' },
  { path: 'compensation', heading: 'Compensation' },
  { path: 'anomalies', heading: 'Anomalies' },
  { path: 'simulator', heading: 'Salary simulator' },
  { path: 'audit', heading: 'Audit history' },
  { path: 'exchange-rates', heading: 'Exchange rates' },
  { path: 'auth', heading: 'Sign in' },
];

describe('app routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
  });

  async function open(url: string): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  for (const feature of FEATURES) {
    it(`lazy-loads the ${feature.heading} page at /${feature.path}`, async () => {
      const page = await open(`/${feature.path}`);

      expect(page.querySelector('h2')?.textContent).toContain(feature.heading);
    });

    it(`declares /${feature.path} as a lazy route`, () => {
      const route = routes.find((r) => r.path === feature.path);

      expect(route?.loadComponent).toBeTypeOf('function');
      expect(route?.component).toBeUndefined();
    });
  }

  it('redirects the root to the dashboard', async () => {
    const page = await open('/');

    expect(page.querySelector('h2')?.textContent).toContain('Dashboard');
  });

  it('shows a not-found view for unknown paths', async () => {
    const page = await open('/no-such-page');

    expect(page.querySelector('h2')?.textContent).toContain('Page not found');
  });
});
