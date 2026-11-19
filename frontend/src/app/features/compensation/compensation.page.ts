import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { AnalyticsService, DepartmentExtremes, RankedPerson } from '../analytics/analytics.service';
import { Money } from '../employees/employee.service';
import { EmployeeService } from '../employees/employee.service';
import { MoneyPipe } from '../../core/format/money.pipe';

/** The highest- and lowest-paid active employees, per department, ranked by USD equivalent. */
@Component({
  selector: 'app-compensation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, RouterLink, MatButtonModule, MatProgressBarModule],
  templateUrl: './compensation.page.html',
  styleUrl: './compensation.page.scss',
})
export class CompensationPage {
  private readonly analytics = inject(AnalyticsService);
  private readonly employees = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<void>();

  protected readonly departments = signal<string[]>([]);
  protected readonly department = signal('');
  protected readonly counts = [3, 5, 10, 25];
  protected readonly count = signal(5);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly results = signal<DepartmentExtremes[]>([]);

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.analytics.payExtremes(this.department(), this.count()).pipe(
            tap((result) => {
              this.results.set(result.departments);
              this.state.set('ready');
            }),
            catchError(() => {
              this.state.set('error');
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
    this.employees
      .filters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (o) => this.departments.set(o.departments), error: () => undefined });
    this.load();
  }

  protected load(): void {
    this.requests.next();
  }

  protected setCount(value: string): void {
    this.count.set(Number(value));
    this.load();
  }

  /** How many people the department's two lists show together. */
  protected shown(result: DepartmentExtremes): number {
    return result.highest.length + result.lowest.length;
  }

  /** The best-paid person's USD pay: the figure every bar in the department is measured against. */
  protected top(result: DepartmentExtremes): Money {
    return result.highest[0].salaryUsd;
  }

  /** A person's pay as a percentage of the department's best-paid person, for the bar. */
  protected share(person: RankedPerson, result: DepartmentExtremes): number {
    const best = Number(result.highest[0].salaryUsd.amount);
    return best > 0 ? Math.round((Number(person.salaryUsd.amount) / best) * 1000) / 10 : 0;
  }

  protected choose(department: string): void {
    this.department.set(department);
    this.load();
  }
}
