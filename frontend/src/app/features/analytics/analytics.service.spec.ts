import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { AnalyticsService } from './analytics.service';

const API = 'http://api.test/api/v1';

describe('AnalyticsService', () => {
  let http: HttpTestingController;
  let service: AnalyticsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AnalyticsService);
  });

  it('asks for salary statistics with the grouping and only the filters that are set', () => {
    service
      .salaryStats('jobTitle', { country: '', department: '', jobTitle: 'Software Engineer' })
      .subscribe();

    const request = http.expectOne((r) => r.url === `${API}/analytics/salary-stats`);
    expect(request.request.params.get('groupBy')).toBe('jobTitle');
    expect(request.request.params.get('jobTitle')).toBe('Software Engineer');
    expect(request.request.params.has('country')).toBe(false);
    expect(request.request.params.has('department')).toBe(false);
  });

  it('asks for pay extremes, with a department only when one is chosen', () => {
    service.payExtremes('Engineering', 5).subscribe();
    service.payExtremes('', 5).subscribe();

    const requests = http.match((r) => r.url === `${API}/analytics/pay-extremes`);
    expect(requests[0].request.params.get('department')).toBe('Engineering');
    expect(requests[0].request.params.get('n')).toBe('5');
    expect(requests[1].request.params.has('department')).toBe(false);
  });
});
