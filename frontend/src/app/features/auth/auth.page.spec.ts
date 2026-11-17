import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AppConfigService } from '../../core/config/app-config.service';
import { AuthService } from '../../core/auth/auth.service';
import { SignInPage } from './auth.page';

const LOGIN_URL = 'http://api.test/api/v1/auth/login';

describe('SignInPage', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function render() {
    await TestBed.configureTestingModule({
      imports: [SignInPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
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
