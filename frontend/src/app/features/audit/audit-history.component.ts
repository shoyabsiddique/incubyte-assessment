import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, of, switchMap, tap } from 'rxjs';
import { describeChanges, FieldChange } from './audit-diff';
import { AuditEntry, AuditFilters, AuditPage, AuditService } from './audit.service';

type FilterName = 'userId' | 'entityType' | 'from' | 'to';

const FIRST: AuditFilters = { userId: '', entityType: '', from: '', to: '', page: 0, size: 20 };

const ENTITY_LABELS: Record<string, string> = {
  EMPLOYEE: 'Employee',
  COMPENSATION_RECORD: 'Compensation record',
  EXCHANGE_RATE: 'Exchange rate',
};

/**
 * The audit trail as a table with changed-fields detail. Given an employee it shows that person's
 * trail; without one it shows the whole organisation's, with filters.
 */
@Component({
  selector: 'app-audit-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './audit-history.component.html',
  styleUrl: './audit-history.component.scss',
})
export class AuditHistoryComponent implements OnInit {
  private readonly service = inject(AuditService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queries = new BehaviorSubject<AuditFilters>(FIRST);

  readonly employeeId = input<string | null>(null);

  protected readonly entityTypes = Object.entries(ENTITY_LABELS).map(([value, label]) => ({
    value,
    label,
  }));
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly filters = signal<AuditFilters>(FIRST);
  protected readonly entries = signal<AuditEntry[]>([]);
  protected readonly total = signal(0);
  /** People seen in any answer so far, so "Changed by" can offer them. */
  readonly knownUsers = signal<{ id: string; email: string }[]>([]);

  protected columns: string[] = [];

  ngOnInit(): void {
    this.columns = this.employeeId()
      ? ['when', 'user', 'action', 'what', 'changes']
      : ['when', 'user', 'action', 'what', 'employee', 'changes'];
    this.queries
      .pipe(
        tap((filters) => {
          this.filters.set(filters);
          this.state.set('loading');
        }),
        switchMap((filters) =>
          this.load(filters).pipe(catchError((error: unknown) => of(error as Error))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (!('items' in result)) {
          this.entries.set([]);
          this.total.set(0);
          this.state.set('error');
          return;
        }
        this.entries.set(result.items);
        this.total.set(result.totalElements);
        this.remember(result.items);
        this.state.set('ready');
      });
  }

  protected entityLabel(type: string): string {
    return ENTITY_LABELS[type] ?? type;
  }

  protected actionLabel(operation: string): string {
    return operation.charAt(0) + operation.slice(1).toLowerCase();
  }

  protected changes(entry: AuditEntry): FieldChange[] {
    return describeChanges(entry);
  }

  protected setFilter(name: FilterName, value: string): void {
    this.queries.next({ ...this.filters(), [name]: value, page: 0 });
  }

  protected clearFilters(): void {
    this.queries.next({ ...FIRST, size: this.filters().size });
  }

  protected onPage(event: PageEvent): void {
    this.queries.next({ ...this.filters(), page: event.pageIndex, size: event.pageSize });
  }

  protected retry(): void {
    this.queries.next({ ...this.filters() });
  }

  private load(filters: AuditFilters) {
    const employee = this.employeeId();
    return employee
      ? this.service.forEmployee(employee, filters.page, filters.size)
      : this.service.search(filters);
  }

  private remember(items: AuditEntry[]): void {
    const known = new Map(this.knownUsers().map((u) => [u.id, u.email]));
    for (const entry of items) {
      known.set(entry.userId, entry.userEmail);
    }
    if (known.size !== this.knownUsers().length) {
      this.knownUsers.set([...known].map(([id, email]) => ({ id, email })));
    }
  }
}

export type { AuditPage };
