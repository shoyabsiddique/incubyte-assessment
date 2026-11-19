import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { AuditHistoryComponent } from '../audit/audit-history.component';
import { ChartComponent } from '../dashboard/chart.component';
import { ChartModel } from '../dashboard/chart-data';
import { EmployeeFormComponent } from './employee-form.component';
import { SalaryRevisionFormComponent } from './salary-revision-form.component';
import { BehaviorSubject, catchError, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { EmployeeService, EmployeeSummary, SalaryRecord } from './employee.service';

type Loaded = { employee: EmployeeSummary; history: SalaryRecord[] };
type Outcome = Loaded | 'not-found' | 'error';

@Component({
  selector: 'app-employee-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatTableModule,
    MatTabsModule,
    AuditHistoryComponent,
    EmployeeFormComponent,
    SalaryRevisionFormComponent,
    ChartComponent,
  ],
  templateUrl: './employee-detail.page.html',
  styleUrl: './employee-detail.page.scss',
})
export class EmployeeDetailPage {
  private readonly service = inject(EmployeeService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new BehaviorSubject<string | null>(null);
  private currentId = '';
  /** Set for a refresh that should not blank the page, such as after a salary revision. */
  private quietRefresh = false;

  protected readonly columns = ['period', 'amount', 'amountUsd', 'marker'];
  protected readonly state = signal<'loading' | 'ready' | 'not-found' | 'error'>('loading');
  protected readonly employee = signal<EmployeeSummary | null>(null);
  protected readonly history = signal<SalaryRecord[]>([]);
  protected readonly editing = signal(false);
  protected readonly tab = signal(0);
  protected readonly revising = signal(false);
  protected readonly notice = signal<string | null>(null);

  protected startRevising(): void {
    this.tab.set(0);
    this.notice.set(null);
    this.revising.set(true);
  }

  protected onRevised(record: SalaryRecord): void {
    this.revising.set(false);
    this.notice.set(`Salary revised: ${record.effectiveStart} onwards.`);
    this.quietRefresh = true;
    this.requests.next(this.currentId);
  }

  /** The first letters of their first and last name, for the avatar. */
  protected initials(person: EmployeeSummary): string {
    return `${person.firstName.charAt(0)}${person.lastName.charAt(0)}`.toUpperCase();
  }

  /** How long they have worked here, as "7 years, 7 months" (or just months for under a year). */
  protected tenure(hireDate: string): string {
    const hired = new Date(`${hireDate}T00:00:00`);
    const now = new Date();
    let months =
      (now.getFullYear() - hired.getFullYear()) * 12 + (now.getMonth() - hired.getMonth());
    if (now.getDate() < hired.getDate()) {
      months -= 1;
    }
    months = Math.max(0, months);
    const years = Math.floor(months / 12);
    const rest = months % 12;
    const unit = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;
    if (years === 0) {
      return unit(rest, 'month');
    }
    return rest === 0 ? unit(years, 'year') : `${unit(years, 'year')}, ${unit(rest, 'month')}`;
  }

  /** When the salary they are paid today began. */
  protected readonly currentSince = computed(
    () => this.history().find((record) => record.current)?.effectiveStart ?? null,
  );

  /** How many times the salary has changed since the first record. */
  protected readonly changes = computed(() => Math.max(0, this.history().length - 1));

  /** Their salary in USD over time, oldest first; only worth drawing once there is a change to see. */
  protected readonly chart = computed<ChartModel | null>(() => {
    const records = [...this.history()].reverse();
    if (records.length < 2) {
      return null;
    }
    const values = records.map((r) => Number(r.amountUsd.amount));
    return {
      categories: records.map((r) => ({ key: r.id, label: formatDay(r.effectiveStart) })),
      series: [{ key: 'usd', label: 'Salary (USD)', values, counts: values.map(() => 1) }],
      max: Math.max(...values),
    };
  });

  protected startEditing(): void {
    this.tab.set(0);
    this.editing.set(true);
  }

  constructor() {
    this.requests
      .pipe(
        switchMap((id) => {
          if (id === null) {
            return of<Outcome | null>(null);
          }
          if (this.quietRefresh) {
            this.quietRefresh = false;
          } else {
            this.state.set('loading');
          }
          return forkJoin({
            employee: this.service.get(id),
            // A person with no salary records is still a person: show them with an empty history.
            history: this.service
              .salaryHistory(id)
              .pipe(
                catchError((error: unknown) =>
                  isNoHistory(error) ? of<SalaryRecord[]>([]) : throwFailure(),
                ),
              ),
          }).pipe(
            map((loaded): Outcome => loaded),
            catchError((error: unknown): [Outcome] =>
              error instanceof HttpErrorResponse && error.status === 404
                ? ['not-found']
                : ['error'],
            ),
          );
        }),
        tap((outcome) => {
          if (outcome === null) {
            return;
          }
          if (outcome === 'not-found' || outcome === 'error') {
            this.employee.set(null);
            this.history.set([]);
            this.state.set(outcome);
            return;
          }
          this.employee.set(outcome.employee);
          this.history.set(outcome.history);
          this.state.set('ready');
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();

    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.currentId = params.get('id') ?? '';
      this.requests.next(this.currentId);
    });
  }

  /** Writes an enum value such as ON_LEAVE as plain words: "On leave". */
  protected label(value: string): string {
    const words = value.replace(/_/g, ' ').toLowerCase();
    return words.charAt(0).toUpperCase() + words.slice(1);
  }

  protected onSaved(updated: EmployeeSummary): void {
    this.employee.set(updated);
    this.editing.set(false);
  }

  protected reloadLatest(): void {
    this.editing.set(false);
    this.revising.set(false);
    this.notice.set(null);
    this.retry();
  }

  protected retry(): void {
    this.requests.next(this.currentId);
  }
}

/** Only the API's own "no salary records" answer is an empty history; any other 404 is a fault. */
function isNoHistory(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === 404 &&
    (error.error as { code?: string } | null)?.code === 'SALARY_HISTORY_NOT_FOUND'
  );
}

/** A failed history is a fault of its own, so it must not be mistaken for a missing employee (also a 404). */
function throwFailure(): never {
  throw new Error('The salary history could not be loaded');
}

function formatDay(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-US', {
    month: 'short',
    year: 'numeric',
  });
}
