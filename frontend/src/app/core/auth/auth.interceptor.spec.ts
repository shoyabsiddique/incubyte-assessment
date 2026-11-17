import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AppConfigService } from '../config/app-config.service';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

const API = 'http://api.test/api/v1';
const USER = { id: 'u-1', email: 'priya@acme.example', displayName: 'Priya Nair' };

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: API, apiBaseUrlOrNull: API } },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('adds the bearer token to API calls when signed in', () => {
    auth.establishSession('tok', USER);

    client.get(`${API}/employees`).subscribe();

    expect(http.expectOne(`${API}/employees`).request.headers.get('Authorization')).toBe(
      'Bearer tok',
    );
  });

  it('sends no Authorization header when signed out', () => {
    client.get(`${API}/employees`).subscribe();

    expect(http.expectOne(`${API}/employees`).request.headers.has('Authorization')).toBe(false);
  });

  it('never sends the token to another origin', () => {
    auth.establishSession('tok', USER);

    client.get('https://elsewhere.example/data').subscribe();

    expect(
      http.expectOne('https://elsewhere.example/data').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('signs out and goes to the sign-in page when the API answers 401', () => {
    auth.establishSession('tok', USER);

    client.get(`${API}/employees`).subscribe({ error: () => undefined });
    http.expectOne(`${API}/employees`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/auth']);
  });

  it('does not redirect when the sign-in call itself fails with 401', () => {
    client.post(`${API}/auth/login`, {}).subscribe({ error: () => undefined });
    http.expectOne(`${API}/auth/login`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });

  it('leaves the session alone for other errors', () => {
    auth.establishSession('tok', USER);

    client.get(`${API}/employees`).subscribe({ error: () => undefined });
    http.expectOne(`${API}/employees`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(auth.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('lets requests through before the configuration is loaded, such as loading the config itself', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    const realClient = TestBed.inject(HttpClient);
    const realHttp = TestBed.inject(HttpTestingController);

    let body: unknown;
    realClient.get('/config.json').subscribe((value) => (body = value));
    realHttp.expectOne('/config.json').flush({ apiBaseUrl: 'http://api.test/api/v1' });

    expect(body).toEqual({ apiBaseUrl: 'http://api.test/api/v1' });
  });
});
