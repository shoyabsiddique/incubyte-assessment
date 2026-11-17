import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from './app-config.service';

describe('AppConfigService', () => {
  let service: AppConfigService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AppConfigService);
    http = TestBed.inject(HttpTestingController);
  });

  it('loads the API base URL from config.json at startup', async () => {
    const loading = service.load();

    http.expectOne('/config.json').flush({ apiBaseUrl: 'https://api.example.com/api/v1' });
    await loading;

    expect(service.apiBaseUrl).toBe('https://api.example.com/api/v1');
  });

  it('drops a trailing slash so URLs join cleanly', async () => {
    const loading = service.load();

    http.expectOne('/config.json').flush({ apiBaseUrl: 'https://api.example.com/api/v1/' });
    await loading;

    expect(service.apiBaseUrl).toBe('https://api.example.com/api/v1');
  });

  it('fails clearly when config.json has no API base URL', async () => {
    const loading = service.load();

    http.expectOne('/config.json').flush({});

    await expect(loading).rejects.toThrow(/apiBaseUrl/);
  });

  it('refuses to answer before the configuration has been loaded', () => {
    expect(() => service.apiBaseUrl).toThrow(/not loaded/i);
  });
});
