import { TestBed } from '@angular/core/testing';
import { ApplicationInitStatus } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { appConfig } from './app.config';
import { AuthService } from './core/auth/auth.service';
import { AppConfigService } from './core/config/app-config.service';

/** Boots the app with its real providers, as main.ts does, to prove the wiring works end to end. */
describe('appConfig', () => {
  const API = 'https://api.example.com/api/v1';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
  });

  async function start(): Promise<HttpTestingController> {
    const http = TestBed.inject(HttpTestingController);
    const initialised = TestBed.inject(ApplicationInitStatus).donePromise;
    http.expectOne('/config.json').flush({ apiBaseUrl: API });
    await initialised;
    return http;
  }

  it('loads the runtime configuration before the app starts', async () => {
    await start();

    expect(TestBed.inject(AppConfigService).apiBaseUrl).toBe(API);
  });

  it('registers the auth interceptor so API calls carry the token', async () => {
    const http = await start();
    TestBed.inject(AuthService).establishSession('tok', {
      id: 'u-1',
      email: 'a@b.c',
      displayName: 'A',
    });

    TestBed.inject(HttpClient).get(`${API}/employees`).subscribe();

    expect(http.expectOne(`${API}/employees`).request.headers.get('Authorization')).toBe(
      'Bearer tok',
    );
  });
});
