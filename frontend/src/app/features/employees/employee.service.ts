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
  version: number;
}

export interface SalaryRecord {
  id: string;
  amount: Money;
  amountUsd: Money;
  effectiveStart: string;
  effectiveEnd: string | null;
  current: boolean;
}

export interface EmployeeProfileInput {
  firstName: string;
  lastName: string;
  workEmail: string;
  jobTitle: string;
  department: string;
  country: string;
  city: string;
  employmentType: string;
  hireDate: string;
  status: string;
}

export interface NewEmployeeInput extends EmployeeProfileInput {
  salaryAmount: string;
  salaryCurrency: string;
  salaryEffectiveStart: string;
}

export interface SalaryRevisionInput {
  amount: string;
  effectiveStart: string;
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

  get(id: string): Observable<EmployeeSummary> {
    return this.http.get<EmployeeSummary>(`${this.config.apiBaseUrl}/employees/${id}`);
  }

  salaryHistory(id: string): Observable<SalaryRecord[]> {
    return this.http.get<SalaryRecord[]>(
      `${this.config.apiBaseUrl}/employees/${id}/salary-history`,
    );
  }

  create(input: NewEmployeeInput): Observable<EmployeeSummary> {
    return this.http.post<EmployeeSummary>(`${this.config.apiBaseUrl}/employees`, input);
  }

  update(id: string, input: EmployeeProfileInput, version: number): Observable<EmployeeSummary> {
    return this.http.put<EmployeeSummary>(`${this.config.apiBaseUrl}/employees/${id}`, {
      ...input,
      version,
    });
  }

  /** Gives the employee a new salary from {@code effectiveStart}; the old record is closed the day before. */
  revise(id: string, input: SalaryRevisionInput): Observable<SalaryRecord> {
    return this.http.post<SalaryRecord>(
      `${this.config.apiBaseUrl}/employees/${id}/salary-revisions`,
      input,
    );
  }

  filters(): Observable<FilterOptions> {
    return this.http.get<FilterOptions>(`${this.config.apiBaseUrl}/employees/filters`);
  }
}
