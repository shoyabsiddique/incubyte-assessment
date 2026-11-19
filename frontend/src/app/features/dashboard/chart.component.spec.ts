import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChartComponent } from './chart.component';
import { ChartModel, ChartType } from './chart-data';

const ONE_SERIES: ChartModel = {
  categories: [
    { key: 'INDIA', label: 'India' },
    { key: 'UK', label: 'United Kingdom' },
    { key: 'USA', label: 'United States' },
  ],
  series: [{ key: 'all', label: 'All', values: [300, 900, 600], counts: [300, 900, 600] }],
  max: 900,
};

const TWO_SERIES: ChartModel = {
  categories: [
    { key: 'INDIA', label: 'India' },
    { key: 'USA', label: 'United States' },
  ],
  series: [
    { key: 'FULL_TIME', label: 'Full time', values: [200, 500], counts: [200, 500] },
    { key: 'CONTRACT', label: 'Contract', values: [100, 100], counts: [100, 100] },
  ],
  max: 500,
};

describe('ChartComponent', () => {
  let fixture: ComponentFixture<ChartComponent>;
  let view: HTMLElement;

  function show(model: ChartModel, type: ChartType, measure = 'headcount', aggregation = 'count') {
    fixture = TestBed.createComponent(ChartComponent);
    fixture.componentRef.setInput('model', model);
    fixture.componentRef.setInput('type', type);
    fixture.componentRef.setInput('measure', measure);
    fixture.componentRef.setInput('aggregation', aggregation);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  beforeEach(() => TestBed.configureTestingModule({ imports: [ChartComponent] }));

  it('draws one column per category for a single series', () => {
    show(ONE_SERIES, 'column');

    expect(view.querySelectorAll('rect.bar').length).toBe(3);
    expect(
      Array.from(view.querySelectorAll('text.category')).map((t) => t.textContent?.trim()),
    ).toEqual(['India', 'United Kingdom', 'United States']);
  });

  it('makes the tallest column taller than the shortest', () => {
    show(ONE_SERIES, 'column');
    const heights = Array.from(view.querySelectorAll('rect.bar')).map((r) =>
      Number(r.getAttribute('height')),
    );

    expect(heights[1]).toBeGreaterThan(heights[2]);
    expect(heights[2]).toBeGreaterThan(heights[0]);
  });

  it('draws grouped columns and a legend when there are several series', () => {
    show(TWO_SERIES, 'column');

    expect(view.querySelectorAll('rect.bar').length).toBe(4);
    expect(
      Array.from(view.querySelectorAll('.legend li')).map((l) => l.textContent?.trim()),
    ).toEqual(['Full time', 'Contract']);
  });

  it('shows no legend for a single series', () => {
    show(ONE_SERIES, 'column');

    expect(view.querySelector('.legend')).toBeNull();
  });

  it('gives every bar a readable tooltip with its value', () => {
    show(ONE_SERIES, 'column', 'salaryUsd', 'average');

    const title = view.querySelector('rect.bar title')?.textContent ?? '';
    expect(title).toContain('India');
    expect(title).toContain('300.00');
  });

  it('draws horizontal bars with the category names beside them', () => {
    show(ONE_SERIES, 'bar');

    expect(view.querySelectorAll('rect.bar').length).toBe(3);
    const widths = Array.from(view.querySelectorAll('rect.bar')).map((r) =>
      Number(r.getAttribute('width')),
    );
    expect(widths[1]).toBeGreaterThan(widths[0]);
  });

  it('draws one line per series with a dot for every point', () => {
    show(TWO_SERIES, 'line');

    expect(view.querySelectorAll('polyline.line').length).toBe(2);
    expect(view.querySelectorAll('circle.dot').length).toBe(4);
  });

  it('draws a donut slice per category with its share in the legend', () => {
    show(ONE_SERIES, 'donut');

    expect(view.querySelectorAll('path.slice').length).toBe(3);
    const legend = Array.from(view.querySelectorAll('.legend li')).map((l) => l.textContent ?? '');
    expect(legend[1]).toContain('United Kingdom');
    expect(legend[1]).toContain('50%');
  });

  it('draws a table with a row per category and a column per series', () => {
    show(TWO_SERIES, 'table');

    expect(view.querySelectorAll('table.chart-table tbody tr').length).toBe(2);
    expect(view.querySelectorAll('table.chart-table thead th').length).toBe(3);
    expect(view.querySelector('table.chart-table tbody tr')?.textContent).toContain('200');
  });

  it('says so when there is nothing to draw', () => {
    show({ categories: [], series: [], max: 0 }, 'column');

    expect(view.querySelector('.empty')?.textContent).toContain('No data');
    expect(view.querySelector('svg')).toBeNull();
  });

  it('describes the chart for assistive technology', () => {
    show(ONE_SERIES, 'column');

    expect(view.querySelector('svg')?.getAttribute('role')).toBe('img');
    expect(view.querySelector('svg')?.getAttribute('aria-label')).toContain('3 categories');
  });
});
