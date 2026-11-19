import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';
import { EmployeeDetailPage } from './employee-detail.page';

const API = 'http://api.test/api/v1';
const ID = 'e1b2c3d4-0000-4000-8000-000000000001';

const EMPLOYEE = {
  id: ID,
  employeeId: 'EMP-000001',
  firstName: 'Charlotte',
  lastName: 'Smith',
  workEmail: 'charlotte.smith@acme.example',
  jobTitle: 'Software Engineer',
  department: 'Engineering',
  country: 'AUSTRALIA',
  countryName: 'Australia',
  city: 'Sydney',
  employmentType: 'FULL_TIME',
  status: 'ON_LEAVE',
  hireDate: '2018-04-23',
  salary: { amount: '145700.00', currency: 'AUD' },
  salaryUsd: { amount: '94705.00', currency: 'USD' },
};

const HISTORY = [
  {
    id: 'r3',
    amount: { amount: '145700.00', currency: 'AUD' },
    amountUsd: { amount: '94705.00', currency: 'USD' },
    effectiveStart: '2023-06-01',
    effectiveEnd: null,
    current: true,
  },
  {
    id: 'r2',
    amount: { amount: '131000.00', currency: 'AUD' },
    amountUsd: { amount: '85150.00', currency: 'USD' },
    effectiveStart: '2021-02-10',
    effectiveEnd: '2023-05-31',
    current: false,
  },
  {
    id: 'r1',
    amount: { amount: '120000.00', currency: 'AUD' },
    amountUsd: { amount: '78000.00', currency: 'USD' },
    effectiveStart: '2018-04-23',
    effectiveEnd: '2021-02-09',
    current: false,
  },
];

describe('EmployeeDetailPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EmployeeDetailPage>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: ID })) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDetailPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function loadEverything(employee: object = EMPLOYEE, history: object[] = HISTORY) {
    http.expectOne(`${API}/employees/${ID}`).flush(employee);
    http.expectOne(`${API}/employees/${ID}/salary-history`).flush(history);
    await settle();
  }

  it('asks for the employee and for their salary history', () => {
    expect(http.expectOne(`${API}/employees/${ID}`).request.method).toBe('GET');
    expect(http.expectOne(`${API}/employees/${ID}/salary-history`).request.method).toBe('GET');
  });

  it('shows a loading indicator until both answers arrive', async () => {
    expect(page.querySelector('[role="progressbar"]')).not.toBeNull();

    await loadEverything();

    expect(page.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('shows the name as the heading and the profile details', async () => {
    await loadEverything();

    expect(page.querySelector('h2')?.textContent).toContain('Charlotte Smith');
    const profile = page.querySelector('.profile')?.textContent ?? '';
    for (const text of [
      'EMP-000001',
      'charlotte.smith@acme.example',
      'Software Engineer',
      'Engineering',
      'Australia',
      'Sydney',
      'Full time',
      'Apr 23, 2018',
      'On leave',
    ]) {
      expect(profile).toContain(text);
    }
  });

  it('shows the current salary in local currency and in USD', async () => {
    await loadEverything();

    const salary = page.querySelector('.current-salary')?.textContent ?? '';
    expect(salary).toContain('145,700.00');
    expect(salary).toContain('AUD');
    expect(salary).toContain('94,705.00');
    expect(salary).toContain('USD');
  });

  it('shows a dash when there is no current salary', async () => {
    await loadEverything({ ...EMPLOYEE, salary: null, salaryUsd: null }, []);

    expect(page.querySelector('.current-salary')?.textContent).toContain('—');
  });

  it('lists the salary history newest first with each period and both amounts', async () => {
    await loadEverything();

    const rows = Array.from(page.querySelectorAll('table.history tr[mat-row]')).map((row) =>
      (row.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );
    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Jun 1, 2023');
    expect(rows[0]).toContain('Present');
    expect(rows[0]).toContain('145,700.00 AUD');
    expect(rows[0]).toContain('94,705.00 USD');
    expect(rows[1]).toContain('Feb 10, 2021');
    expect(rows[1]).toContain('May 31, 2023');
    expect(rows[2]).toContain('Apr 23, 2018');
    expect(rows[2]).toContain('Feb 9, 2021');
  });

  it('marks only the current salary period', async () => {
    await loadEverything();

    const rows = page.querySelectorAll('table.history tr[mat-row]');
    expect(rows[0].textContent).toContain('Current');
    expect(rows[1].textContent).not.toContain('Current');
    expect(rows[2].textContent).not.toContain('Current');
  });

  it('says the USD figures use current exchange rates', async () => {
    await loadEverything();

    expect(page.textContent).toContain('current exchange rates');
  });

  it('links back to the employee directory', async () => {
    await loadEverything();

    const back = page.querySelector('a.back') as HTMLAnchorElement;
    expect(back.getAttribute('href')).toBe('/employees');
  });

  it('says so, with a way back, when there is no such employee', async () => {
    http
      .expectOne(`${API}/employees/${ID}`)
      .flush({ code: 'EMPLOYEE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(page.querySelector('h2')?.textContent).toContain('Employee not found');
    expect((page.querySelector('a.back') as HTMLAnchorElement).getAttribute('href')).toBe(
      '/employees',
    );
  });

  it('shows the error and retries when the employee cannot be loaded', async () => {
    http.expectOne(`${API}/employees/${ID}`).flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');

    (page.querySelector('button.retry') as HTMLButtonElement).click();
    expect(http.expectOne(`${API}/employees/${ID}`)).toBeTruthy();
    http.match(`${API}/employees/${ID}/salary-history`);
  });

  it('shows an empty history when the employee has no salary records', async () => {
    http.expectOne(`${API}/employees/${ID}`).flush({ ...EMPLOYEE, salary: null, salaryUsd: null });
    http
      .expectOne(`${API}/employees/${ID}/salary-history`)
      .flush({ code: 'SALARY_HISTORY_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(page.textContent).toContain('No salary has been recorded');
    expect(page.querySelector('[role="alert"]')).toBeNull();
  });

  it('treats a 404 that is not the missing-history answer as an error', async () => {
    http.expectOne(`${API}/employees/${ID}`).flush(EMPLOYEE);
    http
      .expectOne(`${API}/employees/${ID}/salary-history`)
      .flush('<html>nope</html>', { status: 404, statusText: 'Not Found' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');
    expect(page.textContent).not.toContain('No salary has been recorded');
  });

  it('shows the error state when the history cannot be loaded', async () => {
    http.expectOne(`${API}/employees/${ID}`).flush(EMPLOYEE);
    http
      .expectOne(`${API}/employees/${ID}/salary-history`)
      .flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');
  });

  it('announces the not-found answer to assistive technology', async () => {
    http
      .expectOne(`${API}/employees/${ID}`)
      .flush({ code: 'EMPLOYEE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    http.match(`${API}/employees/${ID}/salary-history`);
    await settle();

    expect(page.querySelector('section.message')?.getAttribute('role')).toBe('status');
  });

  it('offers to edit the profile and shows the form with the current values', async () => {
    await loadEverything();

    (page.querySelector('button.edit') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = page.querySelector('app-employee-form');
    expect(form).not.toBeNull();
    expect((form?.querySelector('[formcontrolname="firstName"]') as HTMLInputElement).value).toBe(
      'Charlotte',
    );
  });

  it('shows the updated profile after a successful save', async () => {
    await loadEverything();
    (page.querySelector('button.edit') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const input = page.querySelector('[formcontrolname="jobTitle"]') as HTMLInputElement;
    input.value = 'Staff Engineer';
    input.dispatchEvent(new Event('input'));

    (page.querySelector('app-employee-form button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    http
      .expectOne(`${API}/employees/${ID}`)
      .flush({ ...EMPLOYEE, jobTitle: 'Staff Engineer', version: 1 });
    await settle();

    expect(page.querySelector('app-employee-form')).toBeNull();
    expect(page.querySelector('.profile')?.textContent).toContain('Staff Engineer');
  });

  it('switches back to the details tab when editing starts from the audit tab', async () => {
    await loadEverything();
    (page.querySelectorAll('[role="tab"]')[1] as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    (page.querySelector('button.edit') as HTMLButtonElement).click();
    await settle();
    await settle();

    expect(page.querySelector('.mat-mdc-tab-body-active app-employee-form')).not.toBeNull();
    expect(page.querySelector('app-employee-form')).not.toBeNull();
  });

  it('has an audit history tab for this employee', async () => {
    await loadEverything();

    const labels = Array.from(page.querySelectorAll('[role="tab"]')).map((t) =>
      t.textContent?.trim(),
    );
    expect(labels).toEqual(['Details', 'Audit history']);
  });

  it('marks a future-dated salary as scheduled instead of present', async () => {
    const scheduled = {
      ...HISTORY[0],
      id: 'r4',
      effectiveStart: '2099-07-01',
      current: false,
    };
    await loadEverything(EMPLOYEE, [
      scheduled,
      { ...HISTORY[0], effectiveEnd: '2099-06-30' },
      HISTORY[1],
      HISTORY[2],
    ]);

    const rows = page.querySelectorAll('table.history tr[mat-row]');
    expect(rows[0].textContent).toContain('Scheduled');
    expect(rows[0].textContent).not.toContain('Present');
    expect(rows[0].textContent).not.toContain('Current');
  });

  describe('at a glance', () => {
    it('shows their initials, role line and status next to the name', async () => {
      await loadEverything();

      expect(page.querySelector('.avatar')?.textContent?.trim()).toBe('CS');
      expect(page.querySelector('.subtitle')?.textContent).toContain(
        'Software Engineer · Engineering · Australia',
      );
      const status = page.querySelector('.status-badge');
      expect(status?.textContent?.trim()).toBe('On leave');
      expect(status?.getAttribute('data-status')).toBe('ON_LEAVE');
    });

    it('summarises tenure, when the current salary started and how often it has changed', async () => {
      await loadEverything();

      const tiles = page.querySelector('.tiles')?.textContent ?? '';
      expect(tiles).toMatch(/Tenure\s*\d+ years?/);
      expect(tiles).toContain('Jun 1, 2023');
      expect(page.querySelector('.tile-changes .tile-value')?.textContent?.trim()).toBe('2');
    });

    it('draws the salary over time in USD once there is more than one record', async () => {
      await loadEverything();

      const chart = page.querySelector('.history-chart');
      expect(chart?.querySelector('svg')).not.toBeNull();
      expect(chart?.querySelectorAll('circle.dot').length).toBe(3);
    });

    it('does not draw a chart for a single salary record', async () => {
      await loadEverything(EMPLOYEE, [HISTORY[0]]);

      expect(page.querySelector('.history-chart')).toBeNull();
      expect(page.querySelector('.tile-changes .tile-value')?.textContent?.trim()).toBe('0');
    });
  });

  describe('revising the salary', () => {
    async function openTheForm() {
      (page.querySelector('button.revise-salary') as HTMLButtonElement).click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('offers to revise the salary and shows the form with the current salary for reference', async () => {
      await loadEverything();

      await openTheForm();

      const form = page.querySelector('app-salary-revision-form');
      expect(form).not.toBeNull();
      expect(form?.querySelector('.reference')?.textContent).toContain('145,700.00 AUD');
      expect(page.querySelector('button.revise-salary')).toBeNull();
    });

    it('does not offer a revision to someone with no salary', async () => {
      await loadEverything({ ...EMPLOYEE, salary: null, salaryUsd: null }, []);

      expect(page.querySelector('button.revise-salary')).toBeNull();
    });

    it('refreshes the current salary and the history after a revision and says so', async () => {
      await loadEverything();
      await openTheForm();
      const amount = page.querySelector('[formcontrolname="amount"]') as HTMLInputElement;
      amount.value = '150000';
      amount.dispatchEvent(new Event('input'));
      const start = page.querySelector('[formcontrolname="effectiveStart"]') as HTMLInputElement;
      start.value = '2025-01-01';
      start.dispatchEvent(new Event('input'));

      (
        page.querySelector('app-salary-revision-form button[type="submit"]') as HTMLButtonElement
      ).click();
      fixture.detectChanges();
      await fixture.whenStable();
      http
        .expectOne(`${API}/employees/${ID}/salary-revisions`)
        .flush(
          { ...HISTORY[0], id: 'r4', effectiveStart: '2025-01-01' },
          { status: 201, statusText: 'Created' },
        );
      await settle();
      http
        .expectOne(`${API}/employees/${ID}`)
        .flush({ ...EMPLOYEE, salary: { amount: '150000.00', currency: 'AUD' } });
      http.expectOne(`${API}/employees/${ID}/salary-history`).flush([
        {
          ...HISTORY[0],
          id: 'r4',
          amount: { amount: '150000.00', currency: 'AUD' },
          effectiveStart: '2025-01-01',
        },
        { ...HISTORY[0], current: false, effectiveEnd: '2024-12-31' },
        HISTORY[1],
        HISTORY[2],
      ]);
      await settle();

      expect(page.querySelector('app-salary-revision-form')).toBeNull();
      expect(page.querySelector('.current-salary .local')?.textContent).toContain('150,000.00');
      expect(page.querySelectorAll('table.history tr[mat-row]').length).toBe(4);
      expect(page.querySelector('.notice')?.textContent).toContain('Salary revised');
      expect(page.querySelector('[role="progressbar"]')).toBeNull();
    });

    it('can be cancelled without changing anything', async () => {
      await loadEverything();
      await openTheForm();

      (page.querySelector('app-salary-revision-form button.cancel') as HTMLButtonElement).click();
      await settle();

      expect(page.querySelector('app-salary-revision-form')).toBeNull();
      expect(page.querySelector('button.revise-salary')).not.toBeNull();
    });

    it('reloads the latest data when a conflicting revision asks for it', async () => {
      await loadEverything();
      await openTheForm();
      fixture.componentInstance['reloadLatest']();
      await settle();

      http.expectOne(`${API}/employees/${ID}`);
      http.expectOne(`${API}/employees/${ID}/salary-history`);
      expect(page.querySelector('app-salary-revision-form')).toBeNull();
    });
  });
});
