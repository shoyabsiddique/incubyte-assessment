import {
  WidgetConfig,
  autoTitle,
  formatValue,
  niceTicks,
  normalize,
  prepare,
  validAggregations,
} from './chart-data';
import { PivotResponse } from '../analytics/analytics.service';

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
    { key: 'UK', label: 'United Kingdom' },
    { key: 'USA', label: 'United States' },
  ],
  series: [
    {
      key: 'all',
      label: 'All',
      points: [
        { value: '300', count: 300 },
        { value: '900', count: 900 },
        { value: '600', count: 600 },
      ],
    },
  ],
};

describe('chart data', () => {
  it('titles a chart from what it shows', () => {
    expect(autoTitle(CONFIG)).toBe('Headcount by country');
    expect(
      autoTitle({ ...CONFIG, measure: 'salaryUsd', aggregation: 'average', x: 'jobTitle' }),
    ).toBe('Average salary (USD) by job title');
    expect(autoTitle({ ...CONFIG, series: 'employmentType' })).toBe(
      'Headcount by country and employment type',
    );
  });

  it('only offers aggregations that make sense for the measure', () => {
    expect(validAggregations('headcount')).toEqual(['count']);
    expect(validAggregations('salaryUsd')).toEqual([
      'count',
      'sum',
      'average',
      'median',
      'minimum',
      'maximum',
    ]);
  });

  it('repairs a configuration whose aggregation does not fit its measure', () => {
    expect(normalize({ ...CONFIG, measure: 'headcount', aggregation: 'sum' }).aggregation).toBe(
      'count',
    );
    expect(normalize({ ...CONFIG, measure: 'salaryUsd', aggregation: 'count' }).aggregation).toBe(
      'count',
    );
    expect(normalize({ ...CONFIG, measure: 'salaryUsd', aggregation: 'count' }).measure).toBe(
      'salaryUsd',
    );
  });

  it('drops a series that repeats the x field', () => {
    expect(normalize({ ...CONFIG, series: 'country' }).series).toBeNull();
  });

  it('turns a response into numbers, in category order by default', () => {
    const model = prepare(RESPONSE, CONFIG);

    expect(model.categories.map((c) => c.label)).toEqual([
      'India',
      'United Kingdom',
      'United States',
    ]);
    expect(model.series[0].values).toEqual([300, 900, 600]);
    expect(model.max).toBe(900);
  });

  it('can sort categories by value and keep only the top few', () => {
    const model = prepare(RESPONSE, { ...CONFIG, sort: 'value', top: 2 });

    expect(model.categories.map((c) => c.label)).toEqual(['United Kingdom', 'United States']);
    expect(model.series[0].values).toEqual([900, 600]);
  });

  it('sorts by the total across series when there are several', () => {
    const response: PivotResponse = {
      categories: [
        { key: 'A', label: 'A' },
        { key: 'B', label: 'B' },
      ],
      series: [
        {
          key: 'x',
          label: 'X',
          points: [
            { value: '1', count: 1 },
            { value: '5', count: 1 },
          ],
        },
        {
          key: 'y',
          label: 'Y',
          points: [
            { value: '2', count: 1 },
            { value: '1', count: 1 },
          ],
        },
      ],
    };

    const model = prepare(response, { ...CONFIG, series: 'employmentType', sort: 'value' });

    expect(model.categories.map((c) => c.key)).toEqual(['B', 'A']);
    expect(model.series.map((s) => s.values)).toEqual([
      [5, 1],
      [1, 2],
    ]);
  });

  it('has an empty model when nothing matched', () => {
    const model = prepare({ categories: [], series: [] }, CONFIG);

    expect(model.categories).toEqual([]);
    expect(model.max).toBe(0);
  });

  it('formats headcount as a whole number and money with two decimals', () => {
    expect(formatValue(12345, 'headcount', 'count', false)).toBe('12,345');
    expect(formatValue(94705.5, 'salaryUsd', 'average', false)).toBe('94,705.50');
    expect(formatValue(5.25, 'tenureYears', 'average', false)).toBe('5.25');
  });

  it('formats axis labels compactly', () => {
    expect(formatValue(1500, 'salaryUsd', 'sum', true)).toBe('1.5k');
    expect(formatValue(2_400_000, 'salaryUsd', 'sum', true)).toBe('2.4M');
    expect(formatValue(80, 'headcount', 'count', true)).toBe('80');
  });

  it('chooses tidy axis ticks', () => {
    expect(niceTicks(900)).toEqual([0, 250, 500, 750, 1000]);
    expect(niceTicks(0)).toEqual([0, 1]);
    expect(niceTicks(7)).toEqual([0, 2, 4, 6, 8]);
  });
});
