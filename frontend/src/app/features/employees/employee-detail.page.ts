import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
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
                  error instanceof HttpErrorResponse && error.status === 404
                    ? of<SalaryRecord[]>([])
                    : throwFailure(error),
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

  protected retry(): void {
    this.requests.next(this.currentId);
  }
}

function throwFailure(error: unknown): never {
  throw error;
}
