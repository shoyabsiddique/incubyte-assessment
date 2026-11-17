import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App shell', () => {
  async function render() {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the product name in the toolbar', async () => {
    const shell = await render();

    expect(shell.querySelector('mat-toolbar')?.textContent).toContain('ACME Salary Management');
  });

  it('offers navigation to every working area except sign-in', async () => {
    const shell = await render();

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

  it('has a place where the routed page is rendered', async () => {
    const shell = await render();

    expect(shell.querySelector('router-outlet')).not.toBeNull();
  });
});
