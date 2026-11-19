import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AppConfigService } from '../../core/config/app-config.service';
import { AuthService } from '../../core/auth/auth.service';
import { SignInPage } from './auth.page';

const LOGIN_URL = 'http://api.test/api/v1/auth/login';

describe('SignInPage', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function render(returnUrl?: string) {
    await TestBed.configureTestingModule({
      imports: [SignInPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) },
          },
        },
        { provide: AppConfigService, useValue: { apiBaseUrl: 'http://api.test/api/v1' } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SignInPage);
    await fixture.whenStable();
    return fixture;
  }

  function fill(root: HTMLElement, email: string, password: string) {
    const emailInput = root.querySelector('input[type="email"]') as HTMLInputElement;
    const passwordInput = root.querySelector('input[type="password"]') as HTMLInputElement;
    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
  }

  it('shows the sign-in heading, both fields and a submit button', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('h2')?.textContent).toContain('Sign in');
    expect(page.querySelector('input[type="email"]')).not.toBeNull();
    expect(page.querySelector('input[type="password"]')).not.toBeNull();
    expect(page.querySelector('button[type="submit"]')?.textContent).toContain('Sign in');
  });

  it('does not call the API while a field is empty', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;

    (page.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await fixture.whenStable();

    http.expectNone(LOGIN_URL);
  });

  it('signs in and goes to the dashboard', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    fill(page, 'priya@acme.example', 'secret');

    (page.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne(LOGIN_URL).flush({
      accessToken: 'tok',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: 'u-1', email: 'priya@acme.example', displayName: 'Priya Nair' },
    });
    await fixture.whenStable();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(true);
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  function submitWith(fixture: { nativeElement: HTMLElement }) {
    const page = fixture.nativeElement;
    fill(page, 'priya@acme.example', 'secret');
    (page.querySelector('button[type="submit"]') as HTMLButtonElement).click();
  }

  const SESSION = {
    accessToken: 'tok',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: { id: 'u-1', email: 'priya@acme.example', displayName: 'Priya Nair' },
  };

  it('returns the user to the page they were heading for', async () => {
    const fixture = await render('/employees');

    submitWith(fixture);
    http.expectOne(LOGIN_URL).flush(SESSION);
    await fixture.whenStable();

    expect(navigateByUrl).toHaveBeenCalledWith('/employees');
  });

  it('ignores a return address that could lead off the site or back to sign-in', async () => {
    for (const unsafe of [
      'https://evil.example',
      '//evil.example',
      '/\\evil.example',
      '/auth?x=1',
      'employees',
    ]) {
      TestBed.resetTestingModule();
      const fixture = await render(unsafe);

      submitWith(fixture);
      http.expectOne(LOGIN_URL).flush(SESSION);
      await fixture.whenStable();

      expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
    }
  });

  it('says the service is unavailable, not that the password is wrong, when sign-in cannot be completed', async () => {
    for (const failure of [
      { status: 500, statusText: 'Server Error' },
      { status: 0, statusText: 'Unknown Error' },
    ]) {
      TestBed.resetTestingModule();
      const fixture = await render();

      submitWith(fixture);
      http.expectOne(LOGIN_URL).flush({}, failure);
      await fixture.whenStable();
      fixture.detectChanges();

      const message = (fixture.nativeElement as HTMLElement).querySelector(
        '[role="alert"]',
      )?.textContent;
      expect(message).toContain('unavailable');
      expect(message).not.toContain('Invalid email or password');
    }
  });

  it('says what the product is for, with three facts it really offers', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('.brand-headline')?.textContent).toContain('ACME pays');
    const points = Array.from(page.querySelectorAll('.brand .point')).map((p) =>
      p.textContent?.trim(),
    );
    expect(points.length).toBe(3);
    expect(points.join(' ')).toContain('audit');
  });

  it('can show and hide the password, and says which it will do', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    const toggle = () => page.querySelector('button.toggle-password') as HTMLButtonElement;
    const input = () => page.querySelector('#password') as HTMLInputElement;

    expect(input().type).toBe('password');
    expect(toggle().getAttribute('aria-label')).toBe('Show password');

    toggle().click();
    fixture.detectChanges();

    expect(input().type).toBe('text');
    expect(toggle().getAttribute('aria-label')).toBe('Hide password');
    expect(toggle().getAttribute('aria-pressed')).toBe('true');
  });

  it('shows that it is working and cannot be pressed twice while signing in', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    fill(page, 'priya@acme.example', 'secret');

    (page.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const submit = page.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    expect(submit.textContent).toContain('Signing in');
    http.expectOne(LOGIN_URL).flush(SESSION);
  });

  it('marks the fields invalid and ties the message to them when sign-in fails', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    fill(page, 'priya@acme.example', 'wrong');
    (page.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http.expectOne(LOGIN_URL).flush({}, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();

    const message = page.querySelector('[role="alert"]') as HTMLElement;
    expect(message.id).toBe('sign-in-error');
    expect(page.querySelector('#password')?.getAttribute('aria-describedby')).toBe('sign-in-error');
    expect(page.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows the generic failure message and keeps the user on the page', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    fill(page, 'priya@acme.example', 'wrong');

    (page.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    http
      .expectOne(LOGIN_URL)
      .flush(
        { code: 'INVALID_CREDENTIALS', detail: 'Invalid email or password' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'Invalid email or password',
    );
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
