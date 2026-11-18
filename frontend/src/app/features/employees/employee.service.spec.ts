import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { EmployeeQuery, EmployeeService } from './employee.service';

const API = 'http://api.test/api/v1';

const EMPTY: EmployeeQuery = {
  page: 0,
  size: 20,
  sort: 'employeeId',
  direction: 'asc',
  q: '',
  country: '',
  department: '',
  jobTitle: '',
  employmentType: '',
  status: '',
};

describe('EmployeeService', () => {
  let service: EmployeeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    });
    service = TestBed.inject(EmployeeService);
    http = TestBed.inject(HttpTestingController);
  });

  it('asks for the page, size and sort and leaves out empty filters', () => {
    service.search(EMPTY).subscribe();

    const request = http.expectOne((r) => r.url === `${API}/employees`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('sort')).toBe('employeeId,asc');
    for (const name of ['q', 'country', 'department', 'jobTitle', 'employmentType', 'status']) {
      expect(request.request.params.has(name)).toBe(false);
    }
  });

  it('sends the search text and every chosen filter', () => {
    service
      .search({
        ...EMPTY,
        page: 3,
        sort: 'salary',
        direction: 'desc',
        q: ' priya nair ',
        country: 'INDIA',
        department: 'Engineering',
        jobTitle: 'Software Engineer',
        employmentType: 'FULL_TIME',
        status: 'ACTIVE',
      })
      .subscribe();

    const params = http.expectOne((r) => r.url === `${API}/employees`).request.params;
    expect(params.get('page')).toBe('3');
    expect(params.get('sort')).toBe('salary,desc');
    expect(params.get('q')).toBe('priya nair');
    expect(params.get('country')).toBe('INDIA');
    expect(params.get('department')).toBe('Engineering');
    expect(params.get('jobTitle')).toBe('Software Engineer');
    expect(params.get('employmentType')).toBe('FULL_TIME');
    expect(params.get('status')).toBe('ACTIVE');
  });

  it('returns the page the API sends', () => {
    let received: unknown;
    service.search(EMPTY).subscribe((page) => (received = page));

    const body = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    http.expectOne((r) => r.url === `${API}/employees`).flush(body);

    expect(received).toEqual(body);
  });

  it('loads the filter options from their own endpoint', () => {
    service.filters().subscribe();

    expect(http.expectOne(`${API}/employees/filters`).request.method).toBe('GET');
  });

  it('loads one employee by id', () => {
    service.get('abc-123').subscribe();

    expect(http.expectOne(`${API}/employees/abc-123`).request.method).toBe('GET');
  });

  it('loads the salary history of an employee', () => {
    service.salaryHistory('abc-123').subscribe();

    expect(http.expectOne(`${API}/employees/abc-123/salary-history`).request.method).toBe('GET');
  });

  it('creates an employee with a POST', () => {
    service.create({ firstName: 'Ada' } as never).subscribe();

    const request = http.expectOne(`${API}/employees`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ firstName: 'Ada' });
  });

  it('updates an employee with a PUT that carries the version', () => {
    service.update('abc-123', { firstName: 'Ada' } as never, 7).subscribe();

    const request = http.expectOne(`${API}/employees/abc-123`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ firstName: 'Ada', version: 7 });
  });

  it('revises a salary with a POST of the amount and the effective date', () => {
    service.revise('abc-123', { amount: '150000', effectiveStart: '2025-01-01' }).subscribe();

    const request = http.expectOne(`${API}/employees/abc-123/salary-revisions`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ amount: '150000', effectiveStart: '2025-01-01' });
  });
});
