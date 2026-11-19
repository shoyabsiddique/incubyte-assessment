import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AppConfigService } from '../../core/config/app-config.service';
import { DashboardPage } from './dashboard.page';
import { DEFAULT_WIDGETS, LAYOUT_KEY } from './dashboard-layout.store';
import { FIELD_MIME } from './widget.component';

const API = 'http://api.test/api/v1';

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

const PIVOT = {
  categories: [{ key: 'INDIA', label: 'India' }],
  series: [{ key: 'all', label: 'All', points: [{ value: '10', count: 10 }] }],
};

describe('DashboardPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DashboardPage>;
  let page: HTMLElement;

  async function open() {
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
    await settle();
  }

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function pivots(): TestRequest[] {
    return http.match((r) => r.url === `${API}/analytics/pivot`);
  }

  async function loadEverything() {
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    for (const request of pivots()) {
      request.flush(PIVOT);
    }
    await settle();
  }

  function tiles(): HTMLElement[] {
    return Array.from(page.querySelectorAll('.tile'));
  }

  beforeEach(() => localStorage.removeItem(LAYOUT_KEY));
  afterEach(() => localStorage.removeItem(LAYOUT_KEY));

  it('starts with a useful set of charts, each asking the server for its data', async () => {
    await open();

    const requests = pivots();
    expect(requests.length).toBe(DEFAULT_WIDGETS.length);
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    requests.forEach((r) => r.flush(PIVOT));
    await settle();

    expect(page.querySelector('h2')?.textContent).toContain('Dashboard');
    expect(tiles().length).toBe(DEFAULT_WIDGETS.length);
    const titles = tiles().map((t) => t.querySelector('.widget-title')?.textContent?.trim());
    expect(titles).toContain('Headcount by country');
  });

  it('lists every field that can be dragged onto a chart', async () => {
    await open();
    await loadEverything();

    const dimensions = Array.from(page.querySelectorAll('.field-chip[data-kind="dimension"]'));
    const measures = Array.from(page.querySelectorAll('.field-chip[data-kind="measure"]'));
    expect(dimensions.map((d) => d.textContent?.trim())).toEqual([
      'Country',
      'Department',
      'Job title',
      'City',
      'Employment type',
      'Hire year',
    ]);
    expect(measures.map((m) => m.textContent?.trim())).toEqual([
      'Headcount',
      'Salary (USD)',
      'Tenure (years)',
    ]);
    expect(dimensions[0].getAttribute('draggable')).toBe('true');
  });

  it('puts the dragged field into the drag data', async () => {
    await open();
    await loadEverything();
    const setData = vi.fn();
    const event = new Event('dragstart', { bubbles: true });
    Object.assign(event, { dataTransfer: { setData, effectAllowed: '' } });

    (page.querySelector('.field-chip[data-key="city"]') as HTMLElement).dispatchEvent(event);

    expect(setData).toHaveBeenCalledWith(
      FIELD_MIME,
      JSON.stringify({ kind: 'dimension', key: 'city' }),
    );
  });

  it('asks every chart again when a filter is chosen', async () => {
    await open();
    await loadEverything();

    const select = page.querySelector('select[name="country"]') as HTMLSelectElement;
    select.value = 'INDIA';
    select.dispatchEvent(new Event('change'));
    await settle();

    const requests = pivots();
    expect(requests.length).toBe(DEFAULT_WIDGETS.length);
    expect(requests.every((r) => r.request.params.get('country') === 'INDIA')).toBe(true);
    requests.forEach((r) => r.flush(PIVOT));
  });

  it('adds a chart that loads its own data', async () => {
    await open();
    await loadEverything();

    (page.querySelector('button.add-chart') as HTMLButtonElement).click();
    await settle();

    expect(tiles().length).toBe(DEFAULT_WIDGETS.length + 1);
    const requests = pivots();
    expect(requests.length).toBe(1);
    requests[0].flush(PIVOT);
  });

  it('removes a chart', async () => {
    await open();
    await loadEverything();

    (tiles()[0].querySelector('button.remove') as HTMLButtonElement).click();
    await settle();

    expect(tiles().length).toBe(DEFAULT_WIDGETS.length - 1);
  });

  it('moves a chart later with its button, and the order follows', async () => {
    await open();
    await loadEverything();
    const first = tiles()[0].querySelector('.widget-title')?.textContent;

    (tiles()[0].querySelector('button.move-later') as HTMLButtonElement).click();
    await settle();

    expect(tiles()[1].querySelector('.widget-title')?.textContent).toBe(first);
  });

  it('reorders charts when one is dropped in a new place', async () => {
    await open();
    await loadEverything();
    const first = tiles()[0].querySelector('.widget-title')?.textContent;

    fixture.componentInstance['reorder']({ previousIndex: 0, currentIndex: 2 } as never);
    await settle();

    expect(tiles()[2].querySelector('.widget-title')?.textContent).toBe(first);
  });

  it('remembers the layout between visits', async () => {
    await open();
    await loadEverything();
    (page.querySelector('button.add-chart') as HTMLButtonElement).click();
    await settle();
    pivots().forEach((r) => r.flush(PIVOT));
    fixture.destroy();
    TestBed.resetTestingModule();

    await open();
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    pivots().forEach((r) => r.flush(PIVOT));
    await settle();

    expect(tiles().length).toBe(DEFAULT_WIDGETS.length + 1);
  });

  it('falls back to the default charts when the saved layout is unusable', async () => {
    localStorage.setItem(LAYOUT_KEY, '{"not":"a layout"}');

    await open();
    http.expectOne(`${API}/employees/filters`).flush(FILTERS);
    pivots().forEach((r) => r.flush(PIVOT));
    await settle();

    expect(tiles().length).toBe(DEFAULT_WIDGETS.length);
  });

  it('can put the default layout back', async () => {
    await open();
    await loadEverything();
    (tiles()[0].querySelector('button.remove') as HTMLButtonElement).click();
    await settle();

    (page.querySelector('button.reset-layout') as HTMLButtonElement).click();
    await settle();
    pivots().forEach((r) => r.flush(PIVOT));
    await settle();

    expect(tiles().length).toBe(DEFAULT_WIDGETS.length);
  });

  it('says how to build a chart', async () => {
    await open();
    await loadEverything();

    expect(page.querySelector('.fields-help')?.textContent).toContain('Drag a field');
  });
});
