import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { AnalyticsService, DepartmentExtremes } from '../analytics/analytics.service';
import { EmployeeService } from '../employees/employee.service';

/** The highest- and lowest-paid active employees, per department, ranked by USD equivalent. */
@Component({
  selector: 'app-compensation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatProgressBarModule],
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
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly results = signal<DepartmentExtremes[]>([]);

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.analytics.payExtremes(this.department(), 5).pipe(
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

  protected choose(department: string): void {
    this.department.set(department);
    this.load();
  }
}
