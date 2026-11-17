import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';

export interface Money {
  amount: string;
  currency: string;
}

export interface EmployeeSummary {
  id: string;
  employeeId: string;
  firstName: string;
  lastName: string;
  workEmail: string;
  jobTitle: string;
  department: string;
  country: string;
  countryName: string;
  city: string;
  employmentType: string;
  status: string;
  hireDate: string;
  salary: Money | null;
  salaryUsd: Money | null;
}

export interface EmployeePage {
  items: EmployeeSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FilterOptions {
  countries: { code: string; name: string }[];
  departments: string[];
  jobTitles: string[];
  employmentTypes: string[];
  statuses: string[];
}

export interface EmployeeQuery {
  page: number;
  size: number;
  sort: string;
  direction: 'asc' | 'desc';
  q: string;
  country: string;
  department: string;
  jobTitle: string;
  employmentType: string;
  status: string;
}

/** Talks to the employee directory API; all paging, search and sorting happens on the server. */
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  search(query: EmployeeQuery): Observable<EmployeePage> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', `${query.sort},${query.direction}`);
    const optional: Record<string, string> = {
      q: query.q,
      country: query.country,
      department: query.department,
      jobTitle: query.jobTitle,
      employmentType: query.employmentType,
      status: query.status,
    };
    for (const [name, value] of Object.entries(optional)) {
      const trimmed = value.trim();
      if (trimmed) {
        params = params.set(name, trimmed);
      }
    }
    return this.http.get<EmployeePage>(`${this.config.apiBaseUrl}/employees`, { params });
  }

  filters(): Observable<FilterOptions> {
    return this.http.get<FilterOptions>(`${this.config.apiBaseUrl}/employees/filters`);
  }
}
