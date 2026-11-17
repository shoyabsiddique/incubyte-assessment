import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AppConfigService } from '../config/app-config.service';
import { AuthService } from './auth.service';

const LOGIN_URL = 'http://api.test/api/v1/auth/login';
const RESPONSE = {
  accessToken: 'token-123',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 'u-1', email: 'priya@acme.example', displayName: 'Priya Nair' },
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: 'http://api.test/api/v1' } },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('starts signed out', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('signs in by posting the credentials and keeps the session in memory', () => {
    auth.login('priya@acme.example', 'secret').subscribe();

    const request = http.expectOne(LOGIN_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'priya@acme.example', password: 'secret' });
    request.flush(RESPONSE);

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.token()).toBe('token-123');
    expect(auth.user()?.email).toBe('priya@acme.example');
    expect(auth.user()?.displayName).toBe('Priya Nair');
  });

  it('never writes the token to web storage', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');

    auth.login('priya@acme.example', 'secret').subscribe();
    http.expectOne(LOGIN_URL).flush(RESPONSE);

    expect(setItem).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    setItem.mockRestore();
  });

  it('stays signed out when sign-in fails', () => {
    let failed = false;
    auth.login('priya@acme.example', 'wrong').subscribe({ error: () => (failed = true) });

    http
      .expectOne(LOGIN_URL)
      .flush(
        { code: 'INVALID_CREDENTIALS', detail: 'Invalid email or password' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('signing out discards the session and returns to the sign-in page', () => {
    auth.establishSession('token-123', RESPONSE.user);

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/auth']);
  });
});
