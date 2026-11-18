import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { ExchangeRateService } from './exchange-rate.service';

const API = 'http://api.test/api/v1';

describe('ExchangeRateService', () => {
  let http: HttpTestingController;
  let service: ExchangeRateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ExchangeRateService);
  });

  it('lists the rates with a GET', () => {
    let result: unknown;
    service.list().subscribe((rates) => (result = rates));

    const request = http.expectOne(`${API}/exchange-rates`);
    expect(request.request.method).toBe('GET');
    request.flush([{ currency: 'USD', rate: '1', version: 0, editable: false }]);
    expect(result).toEqual([{ currency: 'USD', rate: '1', version: 0, editable: false }]);
  });

  it('saves a rate with a PUT that carries the version', () => {
    service.update('INR', '0.0125', 4).subscribe();

    const request = http.expectOne(`${API}/exchange-rates/INR`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rate: '0.0125', version: 4 });
    request.flush({});
  });
});
