import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AppConfigService } from '../../core/config/app-config.service';
import { EmployeesPage } from './employees.page';

const API = 'http://api.test/api/v1';

const FILTERS = {
  countries: [
    { code: 'INDIA', name: 'India' },
    { code: 'USA', name: 'United States' },
  ],
  departments: ['Engineering', 'Finance'],
  jobTitles: ['Software Engineer', 'Accountant'],
  employmentTypes: ['FULL_TIME', 'PART_TIME', 'CONTRACT'],
  statuses: ['ACTIVE', 'ON_LEAVE', 'TERMINATED'],
};

function employee(n: number, overrides: Record<string, unknown> = {}) {
  return {
    id: `id-${n}`,
    employeeId: `EMP-00000${n}`,
    firstName: 'Priya',
    lastName: `Nair${n}`,
    workEmail: `priya${n}@acme.example`,
    jobTitle: 'Software Engineer',
    department: 'Engineering',
    country: 'INDIA',
    countryName: 'India',
    city: 'Pune',
    employmentType: 'FULL_TIME',
    status: 'ACTIVE',
    hireDate: '2020-01-15',
    salary: { amount: '2400000.00', currency: 'INR' },
    salaryUsd: { amount: '28800.00', currency: 'USD' },
    ...overrides,
  };
}

function pageOf(items: unknown[], extra: Record<string, unknown> = {}) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1, ...extra };
}

describe('EmployeesPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EmployeesPage>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeesPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  function listRequests() {
    return http.match((r) => r.url === `${API}/employees`);
  }

  function lastListRequest() {
    const all = listRequests();
    return all[all.length - 1];
  }

  async function loadFirstPage(items: unknown[] = [employee(1), employee(2)], extra = {}) {
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    const [first] = listRequests();
    first.flush(pageOf(items, extra));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('asks for the first twenty people sorted by Employee ID and loads the filter options', async () => {
    const first = listRequests()[0];
    expect(first.request.params.get('page')).toBe('0');
    expect(first.request.params.get('size')).toBe('20');
    expect(first.request.params.get('sort')).toBe('employeeId,asc');
    expect(http.expectOne(`${API}/employees/filters`)).toBeTruthy();
    first.flush(pageOf([]));
  });

  it('shows a loading indicator until the first answer arrives', async () => {
    expect(page.querySelector('[role="progressbar"]')).not.toBeNull();

    await loadFirstPage();

    expect(page.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('lists each person with their ID, name, job, department, country and status', async () => {
    await loadFirstPage();

    const rows = page.querySelectorAll('tr[mat-row]');
    expect(rows).toHaveLength(2);
    const first = rows[0].textContent ?? '';
    for (const text of [
      'EMP-000001',
      'Priya',
      'Nair1',
      'Software Engineer',
      'Engineering',
      'India',
      'ACTIVE',
    ]) {
      expect(first).toContain(text);
    }
  });

  it('shows the salary in local currency and in USD', async () => {
    await loadFirstPage();

    const first = page.querySelector('tr[mat-row]')?.textContent ?? '';
    expect(first).toContain('2,400,000.00');
    expect(first).toContain('INR');
    expect(first).toContain('28,800.00');
    expect(first).toContain('USD');
  });

  it('shows a dash for someone without a current salary', async () => {
    await loadFirstPage([employee(1, { salary: null, salaryUsd: null })]);

    expect(page.querySelector('tr[mat-row]')?.textContent).toContain('—');
  });

  it('links every row to that employee', async () => {
    await loadFirstPage();

    const link = page.querySelector('tr[mat-row] a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/employees/id-1');
  });

  it('shows a friendly message when nobody matches', async () => {
    await loadFirstPage([], { totalElements: 0, totalPages: 0 });

    expect(page.textContent).toContain('No employees match');
    expect(page.querySelectorAll('tr[mat-row]')).toHaveLength(0);
  });

  it('shows the error and retries when the list cannot be loaded', async () => {
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    listRequests()[0].flush({}, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');

    (page.querySelector('button.retry') as HTMLButtonElement).click();
    expect(listRequests()).toHaveLength(1);
  });

  it('waits for typing to pause before searching, then starts again from the first page', async () => {
    vi.useFakeTimers();
    await loadFirstPage();
    const input = page.querySelector('input.search') as HTMLInputElement;

    input.value = 'pri';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(200);
    input.value = 'priya';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(299);
    expect(listRequests()).toHaveLength(0);

    vi.advanceTimersByTime(1);

    const requests = listRequests();
    expect(requests).toHaveLength(1);
    expect(requests[0].request.params.get('q')).toBe('priya');
    expect(requests[0].request.params.get('page')).toBe('0');
  });

  it('filters by country and returns to the first page', async () => {
    await loadFirstPage();

    fixture.componentInstance['setFilter']('country', 'INDIA');
    await fixture.whenStable();

    const request = lastListRequest();
    expect(request.request.params.get('country')).toBe('INDIA');
    expect(request.request.params.get('page')).toBe('0');
  });

  it('offers the filter options the API sent', async () => {
    await loadFirstPage();

    const options = Array.from(page.querySelectorAll('mat-select')).length;
    expect(options).toBeGreaterThanOrEqual(5);
  });

  it('sorts by the clicked column and flips the direction on a second click', async () => {
    await loadFirstPage();
    const nameHeader = page.querySelector('th.mat-column-name') as HTMLElement;

    nameHeader.click();
    await fixture.whenStable();
    expect(lastListRequest().request.params.get('sort')).toBe('name,asc');

    lastListRequest().flush(pageOf([employee(1)]));
    await fixture.whenStable();
    fixture.detectChanges();
    nameHeader.click();
    await fixture.whenStable();
    expect(lastListRequest().request.params.get('sort')).toBe('name,desc');
  });

  it('lets the USD salary column be sorted as salary', async () => {
    await loadFirstPage();

    (page.querySelector('th.mat-column-salaryUsd') as HTMLElement).click();
    await fixture.whenStable();

    expect(lastListRequest().request.params.get('sort')).toBe('salary,asc');
  });

  it('moves to the next page when asked', async () => {
    await loadFirstPage([employee(1)], { totalElements: 100, totalPages: 5, size: 20 });

    (page.querySelector('button.mat-mdc-paginator-navigation-next') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(lastListRequest().request.params.get('page')).toBe('1');
  });
});
