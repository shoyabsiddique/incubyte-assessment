import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { AuditService } from './audit.service';

const API = 'http://api.test/api/v1';

describe('AuditService', () => {
  let service: AuditService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    });
    service = TestBed.inject(AuditService);
    http = TestBed.inject(HttpTestingController);
  });

  it('asks for one employee trail a page at a time', () => {
    service.forEmployee('e-1', 2, 10).subscribe();

    const request = http.expectOne((r) => r.url === `${API}/employees/e-1/audit`);
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');
  });

  it('sends only the filters that have a value', () => {
    service
      .search({ userId: '', entityType: 'EMPLOYEE', from: '2026-03-01', to: '', page: 0, size: 20 })
      .subscribe();

    const request = http.expectOne((r) => r.url === `${API}/audit`);
    expect(request.request.params.get('entityType')).toBe('EMPLOYEE');
    expect(request.request.params.get('from')).toBe('2026-03-01');
    expect(request.request.params.has('userId')).toBe(false);
    expect(request.request.params.has('to')).toBe(false);
  });
});
