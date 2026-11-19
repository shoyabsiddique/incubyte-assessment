import { PivotResponse } from '../analytics/analytics.service';

export type DimensionKey =
  'country' | 'department' | 'jobTitle' | 'city' | 'employmentType' | 'hireYear';
export type MeasureKey = 'headcount' | 'salaryUsd' | 'tenureYears';
export type AggregationKey = 'count' | 'sum' | 'average' | 'median' | 'minimum' | 'maximum';
export type ChartType =
  | 'column'
  | 'stackedColumn'
  | 'bar'
  | 'line'
  | 'area'
  | 'donut'
  | 'pie'
  | 'heatmap'
  | 'kpi'
  | 'table';
export type WidgetSize = 'small' | 'medium' | 'large';

export interface WidgetConfig {
  id: string;
  chart: ChartType;
  x: DimensionKey;
  /** A second field that splits each category into series, or null for one series. */
  series: DimensionKey | null;
  measure: MeasureKey;
  aggregation: AggregationKey;
  size: WidgetSize;
  sort: 'label' | 'value';
  /** Keep only this many categories (after sorting); 0 keeps them all. */
  top: number;
}

export const DIMENSIONS: { key: DimensionKey; label: string }[] = [
  { key: 'country', label: 'Country' },
  { key: 'department', label: 'Department' },
  { key: 'jobTitle', label: 'Job title' },
  { key: 'city', label: 'City' },
  { key: 'employmentType', label: 'Employment type' },
  { key: 'hireYear', label: 'Hire year' },
];

export const MEASURES: { key: MeasureKey; label: string }[] = [
  { key: 'headcount', label: 'Headcount' },
  { key: 'salaryUsd', label: 'Salary (USD)' },
  { key: 'tenureYears', label: 'Tenure (years)' },
];

export const AGGREGATIONS: { key: AggregationKey; label: string }[] = [
  { key: 'count', label: 'Count' },
  { key: 'sum', label: 'Total' },
  { key: 'average', label: 'Average' },
  { key: 'median', label: 'Median' },
  { key: 'minimum', label: 'Minimum' },
  { key: 'maximum', label: 'Maximum' },
];

export const CHART_TYPES: { key: ChartType; label: string }[] = [
  { key: 'column', label: 'Column' },
  { key: 'stackedColumn', label: 'Stacked column' },
  { key: 'bar', label: 'Bar' },
  { key: 'line', label: 'Line' },
  { key: 'area', label: 'Area' },
  { key: 'donut', label: 'Donut' },
  { key: 'pie', label: 'Pie' },
  { key: 'heatmap', label: 'Heatmap' },
  { key: 'kpi', label: 'Single number' },
  { key: 'table', label: 'Table' },
];

const dimensionLabel = (key: DimensionKey) => DIMENSIONS.find((d) => d.key === key)!.label;

/** Headcount can only be counted; any other measure can be combined any way. */
export function validAggregations(measure: MeasureKey): AggregationKey[] {
  return measure === 'headcount' ? ['count'] : AGGREGATIONS.map((a) => a.key);
}

/** Repairs a configuration so every part of it is allowed with the others. */
export function normalize(config: WidgetConfig): WidgetConfig {
  const allowed = validAggregations(config.measure);
  return {
    ...config,
    aggregation: allowed.includes(config.aggregation) ? config.aggregation : allowed[0],
    series: config.series === config.x ? null : config.series,
  };
}

/** "Average salary (USD) by job title": the chart's name, written from what it shows. */
export function autoTitle(config: WidgetConfig): string {
  const measure = MEASURES.find((m) => m.key === config.measure)!.label;
  const aggregation = AGGREGATIONS.find((a) => a.key === config.aggregation)!.label;
  const what =
    config.measure === 'headcount'
      ? measure
      : `${aggregation} ${measure.charAt(0).toLowerCase()}${measure.slice(1)}`;
  const by = dimensionLabel(config.x).toLowerCase();
  const and = config.series ? ` and ${dimensionLabel(config.series).toLowerCase()}` : '';
  return `${what} by ${by}${and}`;
}

export interface ChartModel {
  categories: { key: string; label: string }[];
  series: { key: string; label: string; values: number[]; counts: number[] }[];
  /** The largest value shown, for scaling the value axis. */
  max: number;
}

/** Numbers ready to draw: sorted, trimmed to the top few, with the largest value found. */
export function prepare(response: PivotResponse, config: WidgetConfig): ChartModel {
  let order = response.categories.map((_, index) => index);
  if (config.sort === 'value') {
    const total = (index: number) =>
      response.series.reduce((sum, s) => sum + Number(s.points[index].value), 0);
    order = [...order].sort((a, b) => total(b) - total(a));
  }
  if (config.top > 0) {
    order = order.slice(0, config.top);
  }
  const series = response.series.map((s) => ({
    key: s.key,
    label: s.label,
    values: order.map((index) => Number(s.points[index].value)),
    counts: order.map((index) => s.points[index].count),
  }));
  return {
    categories: order.map((index) => response.categories[index]),
    series,
    max: Math.max(0, ...series.flatMap((s) => s.values)),
  };
}

export function formatValue(
  value: number,
  measure: MeasureKey,
  aggregation: AggregationKey,
  compact: boolean,
): string {
  const whole = measure === 'headcount' || aggregation === 'count';
  if (compact) {
    if (Math.abs(value) >= 1_000_000) {
      return `${trim(value / 1_000_000)}M`;
    }
    if (Math.abs(value) >= 1_000) {
      return `${trim(value / 1_000)}k`;
    }
    return whole ? String(Math.round(value)) : trim(value);
  }
  return value.toLocaleString('en-US', {
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  });
}

function trim(value: number): string {
  return String(Math.round(value * 10) / 10);
}

/** Tidy tick values from zero up to at least the largest value. */
export function niceTicks(max: number): number[] {
  if (max <= 0) {
    return [0, 1];
  }
  const raw = max / 4;
  const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
  const normal = raw / magnitude;
  const nice = normal <= 1 ? 1 : normal <= 2 ? 2 : normal <= 2.5 ? 2.5 : normal <= 5 ? 5 : 10;
  const step = nice * magnitude;
  const count = Math.ceil(max / step);
  return Array.from({ length: count + 1 }, (_, i) => Math.round(i * step * 100) / 100);
}

/**
 * One headline figure for the whole chart, or null when the aggregation can not be combined honestly
 * (a median of group medians is not the median of everyone).
 */
export function kpiValue(model: ChartModel, aggregation: AggregationKey): number | null {
  const points = model.series.flatMap((s) =>
    s.values.map((value, index) => ({ value, count: s.counts[index] })),
  );
  const filled = points.filter((p) => p.count > 0);
  if (filled.length === 0 || aggregation === 'median') {
    return null;
  }
  switch (aggregation) {
    case 'count':
    case 'sum':
      return points.reduce((sum, p) => sum + p.value, 0);
    case 'minimum':
      return Math.min(...filled.map((p) => p.value));
    case 'maximum':
      return Math.max(...filled.map((p) => p.value));
    case 'average': {
      const people = filled.reduce((sum, p) => sum + p.count, 0);
      return filled.reduce((sum, p) => sum + p.value * p.count, 0) / people;
    }
  }
}
