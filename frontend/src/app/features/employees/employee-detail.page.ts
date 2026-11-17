import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { AuditHistoryComponent } from '../audit/audit-history.component';
import { EmployeeFormComponent } from './employee-form.component';
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

  protected readonly columns = ['period', 'amount', 'amountUsd', 'marker'];
  protected readonly state = signal<'loading' | 'ready' | 'not-found' | 'error'>('loading');
  protected readonly employee = signal<EmployeeSummary | null>(null);
  protected readonly history = signal<SalaryRecord[]>([]);
  protected readonly editing = signal(false);
  protected readonly tab = signal(0);

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
          this.state.set('loading');
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
