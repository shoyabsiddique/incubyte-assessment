import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppConfigService } from '../../core/config/app-config.service';
import { CompensationPage } from './compensation.page';

const API = 'http://api.test/api/v1';

function person(n: number, usd: string) {
  return {
    id: `id-${n}`,
    employeeId: `EMP-00000${n}`,
    name: `Person ${n}`,
    jobTitle: 'Software Engineer',
    country: 'INDIA',
    countryName: 'India',
    salary: { amount: '1000000.00', currency: 'INR' },
    salaryUsd: { amount: usd, currency: 'USD' },
  };
}

const RESULT = {
  departments: [
    {
      department: 'Engineering',
      highest: [person(1, '90000.00'), person(2, '80000.00')],
      lowest: [person(3, '10000.00')],
    },
  ],
};

describe('CompensationPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CompensationPage>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompensationPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CompensationPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function extremesRequest(): TestRequest {
    return http.expectOne((r) => r.url === `${API}/analytics/pay-extremes`);
  }

  async function load(result: object = RESULT) {
    http
      .expectOne(`${API}/employees/filters`)
      .flush({ countries: [], departments: ['Engineering', 'Sales'], jobTitles: [] });
    extremesRequest().flush(result);
    await settle();
  }

  it('keeps the Compensation heading and asks for every department at first', async () => {
    expect(page.querySelector('h2')?.textContent).toContain('Compensation');
    expect(extremesRequest().request.params.has('department')).toBe(false);
  });

  it('shows the highest and lowest lists side by side with links to each employee', async () => {
    await load();

    expect(page.querySelector('section.highest')?.textContent).toContain('Person 1');
    expect(page.querySelector('section.lowest')?.textContent).toContain('Person 3');
    expect(page.querySelector('section.highest')?.textContent).toContain('90,000.00 USD');
    expect(page.querySelector('section.highest')?.textContent).toContain('1,000,000.00 INR');
    const link = page.querySelector('section.highest a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/employees/id-1');
  });

  it('asks for one department when it is chosen', async () => {
    await load();

    const select = page.querySelector('select[name="department"]') as HTMLSelectElement;
    select.value = 'Sales';
    select.dispatchEvent(new Event('change'));
    await settle();

    expect(extremesRequest().request.params.get('department')).toBe('Sales');
  });

  it('says so when there is nobody to show', async () => {
    await load({ departments: [] });

    expect(page.querySelector('.empty')).not.toBeNull();
  });

  it('shows the error with a retry', async () => {
    http
      .expectOne(`${API}/employees/filters`)
      .flush({ countries: [], departments: [], jobTitles: [] });
    extremesRequest().flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(page.querySelector('[role="alert"]')).not.toBeNull();
    (page.querySelector('button.retry') as HTMLButtonElement).click();
    await settle();
    extremesRequest().flush(RESULT);
    await settle();
    expect(page.querySelector('section.highest')).not.toBeNull();
  });
  it('numbers each list from the top', async () => {
    await load();

    const ranks = Array.from(page.querySelectorAll('section.highest .rank')).map((r) =>
      r.textContent?.trim(),
    );
    expect(ranks).toEqual(['1', '2']);
  });

  it('draws a bar for each person relative to the best paid in the department', async () => {
    await load();

    const widths = Array.from(page.querySelectorAll('section.highest .bar-fill')).map((b) =>
      Math.round(parseFloat((b as HTMLElement).style.width)),
    );
    expect(widths).toEqual([100, 89]);
    const low = page.querySelector('section.lowest .bar-fill') as HTMLElement;
    expect(Math.round(parseFloat(low.style.width))).toBe(11);
  });

  it('summarises the department in its heading', async () => {
    await load();

    const summary = page.querySelector('article.department .summary')?.textContent ?? '';
    expect(summary).toContain('3 people shown');
    expect(summary).toContain('90,000.00');
  });

  it('lets the number of people shown be changed', async () => {
    await load();

    const select = page.querySelector('select[name="n"]') as HTMLSelectElement;
    select.value = '10';
    select.dispatchEvent(new Event('change'));
    await settle();

    expect(extremesRequest().request.params.get('n')).toBe('10');
  });
});
