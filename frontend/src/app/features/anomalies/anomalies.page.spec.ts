import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppConfigService } from '../../core/config/app-config.service';
import { AnomaliesPage } from './anomalies.page';

const API = 'http://api.test/api/v1';

function item(n: number, deviation: string, direction: 'ABOVE' | 'BELOW') {
  return {
    id: `id-${n}`,
    employeeId: `EMP-00000${n}`,
    name: `Person ${n}`,
    country: 'INDIA',
    countryName: 'India',
    department: 'Engineering',
    jobTitle: 'Software Engineer',
    salary: { amount: '2000000.00', currency: 'INR' },
    salaryUsd: { amount: '24000.00', currency: 'USD' },
    peerMedianUsd: { amount: '16500.00', currency: 'USD' },
    peerGroupSize: 12,
    deviationPercent: deviation,
    direction,
  };
}

function page(items: object[], totalPages = 1, pageNumber = 0) {
  return {
    label: 'Potential anomalies for HR review',
    items,
    page: pageNumber,
    size: 20,
    totalElements: items.length,
    totalPages,
  };
}

describe('AnomaliesPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AnomaliesPage>;
  let view: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnomaliesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnomaliesPage);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function anomalyRequest(): TestRequest {
    return http.expectOne((r) => r.url === `${API}/analytics/anomalies`);
  }

  async function load(
    result: object = page([item(1, '45.00', 'ABOVE'), item(2, '-35.00', 'BELOW')]),
  ) {
    http.expectOne(`${API}/employees/filters`).flush({
      countries: [{ code: 'INDIA', name: 'India' }],
      departments: ['Engineering'],
      jobTitles: [],
    });
    anomalyRequest().flush(result);
    await settle();
  }

  async function choose(name: string, value: string) {
    const select = view.querySelector(`select[name="${name}"]`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await settle();
  }

  it('is titled as items for review and explains the rule', async () => {
    await load();

    expect(view.querySelector('h2')?.textContent).toContain('Potential anomalies for HR review');
    const explanation = view.querySelector('.rule')?.textContent ?? '';
    expect(explanation).toContain('more than 20%');
    expect(explanation).toContain('median');
    expect(explanation).toContain('at least 5');
  });

  it('shows a loading indicator first', () => {
    expect(view.querySelector('[role="progressbar"]')).not.toBeNull();
  });

  it('lists each person with the reasoning and a signed percentage', async () => {
    await load();

    const rows = view.querySelectorAll('table.anomalies tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Person 1');
    expect(rows[0].textContent).toContain('+45.00%');
    expect(rows[0].textContent).toContain('16,500.00 USD');
    expect(rows[0].textContent).toContain('12');
    expect(rows[1].textContent).toContain('-35.00%');
  });

  it('links each person to their employee page', async () => {
    await load();

    const link = view.querySelector('table.anomalies tbody a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/employees/id-1');
  });

  it('asks again when a filter is applied', async () => {
    await load();

    await choose('direction', 'above');

    const request = anomalyRequest();
    expect(request.request.params.get('direction')).toBe('above');
    request.flush(page([item(1, '45.00', 'ABOVE')]));
    await settle();
    expect(view.querySelectorAll('table.anomalies tbody tr').length).toBe(1);
  });

  it('pages forward, and a changed filter starts again from the first page', async () => {
    await load(page([item(1, '45.00', 'ABOVE')], 3));

    (view.querySelector('button.next') as HTMLButtonElement).click();
    await settle();
    const second = anomalyRequest();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([item(2, '-35.00', 'BELOW')], 3, 1));
    await settle();
    expect(view.querySelector('.pager')?.textContent).toContain('Page 2 of 3');

    await choose('country', 'INDIA');
    const filtered = anomalyRequest();
    expect(filtered.request.params.get('page')).toBe('0');
    expect(filtered.request.params.get('country')).toBe('INDIA');
  });

  it('words an empty result as nothing found, not as a verdict of fairness', async () => {
    await load(page([]));

    const empty = view.querySelector('.empty')?.textContent ?? '';
    expect(empty).toContain('No potential anomalies found');
    expect(empty).not.toMatch(/fair/i);
    expect(view.querySelector('table.anomalies')).toBeNull();
  });

  it('shows the error with a retry', async () => {
    http
      .expectOne(`${API}/employees/filters`)
      .flush({ countries: [], departments: [], jobTitles: [] });
    anomalyRequest().flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(view.querySelector('[role="alert"]')).not.toBeNull();
    (view.querySelector('button.retry') as HTMLButtonElement).click();
    await settle();
    anomalyRequest().flush(page([item(1, '45.00', 'ABOVE')]));
    await settle();
    expect(view.querySelectorAll('table.anomalies tbody tr').length).toBe(1);
  });
});
