import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { PivotResponse } from '../analytics/analytics.service';
import { WidgetConfig } from './chart-data';
import { WidgetComponent } from './widget.component';

const API = 'http://api.test/api/v1';

const CONFIG: WidgetConfig = {
  id: 'w1',
  chart: 'column',
  x: 'country',
  series: null,
  measure: 'headcount',
  aggregation: 'count',
  size: 'medium',
  sort: 'label',
  top: 0,
};

const RESPONSE: PivotResponse = {
  categories: [
    { key: 'INDIA', label: 'India' },
    { key: 'USA', label: 'United States' },
  ],
  series: [
    {
      key: 'all',
      label: 'All',
      points: [
        { value: '300', count: 300 },
        { value: '900', count: 900 },
      ],
    },
  ],
};

const NO_FILTERS = { country: '', department: '', jobTitle: '' };

describe('WidgetComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<WidgetComponent>;
  let view: HTMLElement;
  let changes: WidgetConfig[];
  let removed: number;
  let moves: number[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WidgetComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WidgetComponent);
    changes = [];
    removed = 0;
    moves = [];
    fixture.componentRef.setInput('config', CONFIG);
    fixture.componentRef.setInput('filters', NO_FILTERS);
    fixture.componentRef.setInput('first', false);
    fixture.componentRef.setInput('last', false);
    // The page owns the configuration: a change comes back down as the new input.
    fixture.componentInstance.configChange.subscribe((c) => {
      changes.push(c);
      fixture.componentRef.setInput('config', c);
    });
    fixture.componentInstance.removed.subscribe(() => removed++);
    fixture.componentInstance.moved.subscribe((d) => moves.push(d));
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function request(): TestRequest {
    return http.expectOne((r) => r.url === `${API}/analytics/pivot`);
  }

  async function load(response: PivotResponse = RESPONSE) {
    request().flush(response);
    await settle();
  }

  async function openPanel() {
    (view.querySelector('button.configure') as HTMLButtonElement).click();
    await settle();
  }

  async function choose(name: string, value: string) {
    const select = view.querySelector(`select[name="${name}"]`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await settle();
  }

  function dropOn(well: string, payload: object) {
    const event = new Event('drop', { bubbles: true, cancelable: true });
    Object.assign(event, {
      dataTransfer: {
        getData: () => JSON.stringify(payload),
        types: ['application/x-dashboard-field'],
      },
    });
    (view.querySelector(`.well-${well}`) as HTMLElement).dispatchEvent(event);
  }

  it('asks for the pivot its configuration describes and titles itself from it', async () => {
    const req = request();
    expect(req.request.params.get('x')).toBe('country');
    expect(req.request.params.get('measure')).toBe('headcount');
    expect(req.request.params.get('aggregation')).toBe('count');
    expect(req.request.params.has('series')).toBe(false);
    req.flush(RESPONSE);
    await settle();

    expect(view.querySelector('.widget-title')?.textContent).toContain('Headcount by country');
    expect(view.querySelectorAll('rect.bar').length).toBe(2);
  });

  it('passes the dashboard filters to the pivot', async () => {
    request().flush(RESPONSE);
    fixture.componentRef.setInput('filters', { country: 'INDIA', department: '', jobTitle: 'Dev' });
    await settle();

    const req = request();
    expect(req.request.params.get('country')).toBe('INDIA');
    expect(req.request.params.get('jobTitle')).toBe('Dev');
    req.flush(RESPONSE);
  });

  it('shows a loading indicator until the data arrives', async () => {
    expect(view.querySelector('[role="progressbar"]')).not.toBeNull();

    await load();

    expect(view.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('shows an error with a retry when the data can not be loaded', async () => {
    request().flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(view.querySelector('.banner')?.textContent).toContain('could not be loaded');
    (view.querySelector('button.retry') as HTMLButtonElement).click();
    await settle();
    await load();
    expect(view.querySelector('.banner')).toBeNull();
    expect(view.querySelectorAll('rect.bar').length).toBe(2);
  });

  it('keeps the settings hidden until the chart is configured', async () => {
    await load();
    expect(view.querySelector('.panel')).toBeNull();

    await openPanel();

    expect(view.querySelector('.panel')).not.toBeNull();
  });

  it('lets any field be chosen for the x axis and asks for the new data', async () => {
    await load();
    await openPanel();

    await choose('x', 'department');

    expect(changes.at(-1)?.x).toBe('department');
    expect(request().request.params.get('x')).toBe('department');
  });

  it('lets a second field split the chart into series', async () => {
    await load();
    await openPanel();

    await choose('series', 'employmentType');

    expect(changes.at(-1)?.series).toBe('employmentType');
    expect(request().request.params.get('series')).toBe('employmentType');
  });

  it('changes the measure and keeps the aggregation valid for it', async () => {
    await load();
    await openPanel();

    await choose('measure', 'salaryUsd');
    expect(changes.at(-1)?.measure).toBe('salaryUsd');
    expect(changes.at(-1)?.aggregation).toBe('average');
    request().flush(RESPONSE);
    await settle();

    await choose('aggregation', 'median');
    expect(request().request.params.get('aggregation')).toBe('median');
  });

  it('offers only counting for headcount', async () => {
    await load();
    await openPanel();

    const options = view.querySelectorAll('select[name="aggregation"] option');
    expect(options.length).toBe(1);
    expect(options[0].textContent).toContain('Count');
  });

  it('does not ask for data again when only the look of the chart changes', async () => {
    await load();
    await openPanel();

    (view.querySelector('button.chart-type[data-chart="line"]') as HTMLButtonElement).click();
    await choose('sort', 'value');
    await choose('top', '5');

    http.expectNone((r) => r.url === `${API}/analytics/pivot`);
    expect(changes.at(-1)).toMatchObject({ chart: 'line', sort: 'value', top: 5 });
    expect(view.querySelectorAll('polyline.line').length).toBe(1);
  });

  it('offers every chart type', async () => {
    await load();
    await openPanel();

    const types = Array.from(view.querySelectorAll('button.chart-type')).map((b) =>
      b.getAttribute('data-chart'),
    );
    expect(types).toEqual([
      'column',
      'stackedColumn',
      'bar',
      'line',
      'area',
      'donut',
      'pie',
      'heatmap',
      'kpi',
      'table',
    ]);
  });

  it('accepts a dimension dropped on the x axis well', async () => {
    await load();
    await openPanel();

    dropOn('x', { kind: 'dimension', key: 'city' });
    await settle();

    expect(changes.at(-1)?.x).toBe('city');
    expect(request().request.params.get('x')).toBe('city');
  });

  it('accepts a dimension dropped on the series well and a measure dropped on the value well', async () => {
    await load();
    await openPanel();

    dropOn('series', { kind: 'dimension', key: 'hireYear' });
    await settle();
    expect(changes.at(-1)?.series).toBe('hireYear');
    request().flush(RESPONSE);
    await settle();

    dropOn('value', { kind: 'measure', key: 'tenureYears' });
    await settle();
    expect(changes.at(-1)?.measure).toBe('tenureYears');
  });

  it('refuses a field dropped on a well that cannot take it', async () => {
    await load();
    await openPanel();

    dropOn('x', { kind: 'measure', key: 'salaryUsd' });
    dropOn('value', { kind: 'dimension', key: 'city' });
    dropOn('x', { kind: 'dimension', key: 'not-a-field' });
    await settle();

    expect(changes).toEqual([]);
  });

  it('reports a move earlier or later and a removal', async () => {
    await load();

    (view.querySelector('button.move-earlier') as HTMLButtonElement).click();
    (view.querySelector('button.move-later') as HTMLButtonElement).click();
    (view.querySelector('button.remove') as HTMLButtonElement).click();

    expect(moves).toEqual([-1, 1]);
    expect(removed).toBe(1);
  });

  it('cannot move the first chart earlier or the last one later', async () => {
    fixture.componentRef.setInput('first', true);
    fixture.componentRef.setInput('last', true);
    await load();

    expect((view.querySelector('button.move-earlier') as HTMLButtonElement).disabled).toBe(true);
    expect((view.querySelector('button.move-later') as HTMLButtonElement).disabled).toBe(true);
  });
});
