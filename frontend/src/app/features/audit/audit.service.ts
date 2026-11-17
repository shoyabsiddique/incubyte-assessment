import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';

export interface AuditEntry {
  id: string;
  userId: string;
  userEmail: string;
  entityType: string;
  entityId: string;
  subjectEmployeeId: string | null;
  operation: string;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  createdAt: string;
}

export interface AuditPage {
  items: AuditEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditFilters {
  userId: string;
  entityType: string;
  from: string;
  to: string;
  page: number;
  size: number;
}

/** Reads the audit trail; entries can only be looked at, never changed. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  forEmployee(employeeId: string, page: number, size: number): Observable<AuditPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<AuditPage>(`${this.config.apiBaseUrl}/employees/${employeeId}/audit`, {
      params,
    });
  }

  search(filters: AuditFilters): Observable<AuditPage> {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    const optional: Record<string, string> = {
      userId: filters.userId,
      entityType: filters.entityType,
      from: filters.from,
      to: filters.to,
    };
    for (const [name, value] of Object.entries(optional)) {
      if (value) {
        params = params.set(name, value);
      }
    }
    return this.http.get<AuditPage>(`${this.config.apiBaseUrl}/audit`, { params });
  }
}
