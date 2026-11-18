import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { DashboardPage } from './dashboard.page';

const API = 'http://api.test/api/v1';

function money(amount: string, currency = 'USD') {
  return { amount, currency };
}

function stats(count: number, base: number, currency = 'USD') {
  return {
    count,
    total: money(String(base * count), currency),
    average: money(String(base), currency),
    median: money(String(base + 1), currency),
    minimum: money(String(base - 10), currency),
    maximum: money(String(base + 10), currency),
  };
}

const FILTERS = {
  countries: [
    { code: 'INDIA', name: 'India' },
    { code: 'USA', name: 'United States' },
  ],
  departments: ['Engineering', 'Sales'],
  jobTitles: ['Software Engineer'],
  employmentTypes: [],
  statuses: [],
};

const BY_COUNTRY = {
  groupBy: 'country',
  groups: [
    { key: 'INDIA', label: 'India', usd: stats(40, 1000), local: null },
    { key: 'USA', label: 'United States', usd: stats(60, 5000), local: null },
  ],
};

describe('DashboardPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DashboardPage>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function statsRequest(): TestRequest {
    return http.expectOne((r) => r.url === `${API}/analytics/salary-stats`);
  }

  async function load(result: object = BY_COUNTRY) {
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    statsRequest().flush(result);
    await settle();
  }

  async function choose(name: string, value: string) {
    const select = page.querySelector(`select[name="${name}"]`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await settle();
  }

  it('starts by asking for pay per country and shows a loading indicator', () => {
    expect(page.querySelector('[role="progressbar"]')).not.toBeNull();
    expect(statsRequest().request.params.get('groupBy')).toBe('country');
  });

  it('shows one row per group with every statistic in USD', async () => {
    await load();

    const rows = page.querySelectorAll('table.stats tr[mat-row]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('India');
    expect(rows[0].textContent).toContain('40');
    expect(rows[1].textContent).toContain('5,000.00');
    expect(page.textContent).toContain('USD');
    expect(page.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('asks again when the grouping or a filter changes', async () => {
    await load();

    await choose('groupBy', 'jobTitle');
    const grouped = statsRequest();
    expect(grouped.request.params.get('groupBy')).toBe('jobTitle');
    grouped.flush({ groupBy: 'jobTitle', groups: [] });
    await settle();

    await choose('jobTitle', 'Software Engineer');
    const filtered = statsRequest();
    expect(filtered.request.params.get('jobTitle')).toBe('Software Engineer');
    expect(filtered.request.params.get('groupBy')).toBe('jobTitle');
  });

  it('shows local-currency figures next to USD once a country is chosen', async () => {
    await load();
    expect(page.textContent).not.toContain('Average (local)');

    await choose('country', 'INDIA');
    statsRequest().flush({
      groupBy: 'country',
      groups: [
        { key: 'INDIA', label: 'India', usd: stats(40, 1000), local: stats(40, 83000, 'INR') },
      ],
    });
    await settle();

    expect(page.textContent).toContain('Average (local)');
    expect(page.querySelector('table.stats tr[mat-row]')?.textContent).toContain('83,000.00 INR');
  });

  it('says so when nothing matches, and shows no table', async () => {
    await load({ groupBy: 'country', groups: [] });

    expect(page.querySelector('.empty')?.textContent).toContain('No active employees match');
    expect(page.querySelector('table.stats')).toBeNull();
  });

  it('shows the error with a retry instead of partial numbers', async () => {
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    statsRequest().flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    expect(page.querySelector('table.stats')).toBeNull();
    (page.querySelector('button.retry') as HTMLButtonElement).click();
    await settle();
    statsRequest().flush(BY_COUNTRY);
    await settle();
    expect(page.querySelectorAll('table.stats tr[mat-row]').length).toBe(2);
  });
});
