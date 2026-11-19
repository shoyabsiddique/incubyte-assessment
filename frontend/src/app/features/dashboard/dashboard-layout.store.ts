import { Injectable } from '@angular/core';
import {
  AGGREGATIONS,
  CHART_TYPES,
  DIMENSIONS,
  MEASURES,
  WidgetConfig,
  normalize,
} from './chart-data';

export const LAYOUT_KEY = 'acme.dashboard.layout.v1';

export const DEFAULT_WIDGETS: readonly WidgetConfig[] = [
  {
    id: 'default-headcount-country',
    chart: 'column',
    x: 'country',
    series: null,
    measure: 'headcount',
    aggregation: 'count',
    size: 'medium',
    sort: 'value',
    top: 0,
  },
  {
    id: 'default-salary-department',
    chart: 'bar',
    x: 'department',
    series: null,
    measure: 'salaryUsd',
    aggregation: 'average',
    size: 'medium',
    sort: 'value',
    top: 0,
  },
  {
    id: 'default-pay-country',
    chart: 'donut',
    x: 'country',
    series: null,
    measure: 'salaryUsd',
    aggregation: 'sum',
    size: 'medium',
    sort: 'value',
    top: 0,
  },
  {
    id: 'default-salary-title',
    chart: 'table',
    x: 'jobTitle',
    series: null,
    measure: 'salaryUsd',
    aggregation: 'median',
    size: 'medium',
    sort: 'value',
    top: 10,
  },
];

/** Remembers each viewer's chart layout in their own browser; storage that is missing or broken is ignored. */
@Injectable({ providedIn: 'root' })
export class DashboardLayoutStore {
  load(): WidgetConfig[] | null {
    try {
      const raw = localStorage.getItem(LAYOUT_KEY);
      if (!raw) {
        return null;
      }
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed) || parsed.length === 0 || !parsed.every(isWidget)) {
        return null;
      }
      return parsed.map((w) => normalize(w));
    } catch {
      return null;
    }
  }

  save(widgets: readonly WidgetConfig[]): void {
    try {
      localStorage.setItem(LAYOUT_KEY, JSON.stringify(widgets));
    } catch {
      // The layout is a convenience; losing it is not worth an error.
    }
  }

  clear(): void {
    try {
      localStorage.removeItem(LAYOUT_KEY);
    } catch {
      // Nothing to clear.
    }
  }
}

function isWidget(value: unknown): value is WidgetConfig {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const w = value as Record<string, unknown>;
  return (
    typeof w['id'] === 'string' &&
    CHART_TYPES.some((c) => c.key === w['chart']) &&
    DIMENSIONS.some((d) => d.key === w['x']) &&
    (w['series'] === null || DIMENSIONS.some((d) => d.key === w['series'])) &&
    MEASURES.some((m) => m.key === w['measure']) &&
    AGGREGATIONS.some((a) => a.key === w['aggregation']) &&
    ['small', 'medium', 'large'].includes(w['size'] as string) &&
    ['label', 'value'].includes(w['sort'] as string) &&
    typeof w['top'] === 'number'
  );
}
