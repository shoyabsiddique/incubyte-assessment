import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';
import { Money } from '../employees/employee.service';

export type GroupBy = 'country' | 'department' | 'jobTitle';

export interface Stats {
  count: number;
  total: Money;
  average: Money;
  median: Money;
  minimum: Money;
  maximum: Money;
}

export interface GroupStats {
  key: string;
  label: string;
  usd: Stats;
  local: Stats | null;
}

export interface SalaryStatsResponse {
  groupBy: GroupBy;
  groups: GroupStats[];
}

export interface StatsFilters {
  country: string;
  department: string;
  jobTitle: string;
}

export interface RankedPerson {
  id: string;
  employeeId: string;
  name: string;
  jobTitle: string;
  country: string;
  countryName: string;
  salary: Money;
  salaryUsd: Money;
}

export interface DepartmentExtremes {
  department: string;
  highest: RankedPerson[];
  lowest: RankedPerson[];
}

export interface Anomaly {
  id: string;
  employeeId: string;
  name: string;
  country: string;
  countryName: string;
  department: string;
  jobTitle: string;
  salary: Money;
  salaryUsd: Money;
  peerMedianUsd: Money;
  peerGroupSize: number;
  /** Signed percentage as text, such as "45.00" or "-35.00". */
  deviationPercent: string;
  direction: 'ABOVE' | 'BELOW';
}

export interface AnomalyPage {
  label: string;
  items: Anomaly[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PivotCategory {
  key: string;
  label: string;
}

export interface PivotPoint {
  /** The aggregated value as plain decimal text. */
  value: string;
  /** How many people the value covers; zero means nobody fell in this cell. */
  count: number;
}

export interface PivotSeries {
  key: string;
  label: string;
  points: PivotPoint[];
}

export interface PivotResponse {
  categories: PivotCategory[];
  series: PivotSeries[];
}

export interface PivotQuery {
  x: string;
  series: string | null;
  measure: string;
  aggregation: string;
}

/** Talks to the pay analytics API. Every figure is calculated by the server. */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  salaryStats(groupBy: GroupBy, filters: StatsFilters): Observable<SalaryStatsResponse> {
    let params = new HttpParams().set('groupBy', groupBy);
    for (const [name, value] of Object.entries(filters)) {
      if (value.trim()) {
        params = params.set(name, value.trim());
      }
    }
    return this.http.get<SalaryStatsResponse>(`${this.config.apiBaseUrl}/analytics/salary-stats`, {
      params,
    });
  }

  anomalies(
    filters: { country: string; department: string; direction: string },
    page: number,
    size: number,
  ): Observable<AnomalyPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [name, value] of Object.entries(filters)) {
      if (value.trim()) {
        params = params.set(name, value.trim());
      }
    }
    return this.http.get<AnomalyPage>(`${this.config.apiBaseUrl}/analytics/anomalies`, { params });
  }

  pivot(query: PivotQuery, filters: StatsFilters): Observable<PivotResponse> {
    let params = new HttpParams()
      .set('x', query.x)
      .set('measure', query.measure)
      .set('aggregation', query.aggregation);
    if (query.series) {
      params = params.set('series', query.series);
    }
    for (const [name, value] of Object.entries(filters)) {
      if (value.trim()) {
        params = params.set(name, value.trim());
      }
    }
    return this.http.get<PivotResponse>(`${this.config.apiBaseUrl}/analytics/pivot`, { params });
  }

  payExtremes(department: string, n: number): Observable<{ departments: DepartmentExtremes[] }> {
    let params = new HttpParams().set('n', n);
    if (department.trim()) {
      params = params.set('department', department.trim());
    }
    return this.http.get<{ departments: DepartmentExtremes[] }>(
      `${this.config.apiBaseUrl}/analytics/pay-extremes`,
      { params },
    );
  }
}
