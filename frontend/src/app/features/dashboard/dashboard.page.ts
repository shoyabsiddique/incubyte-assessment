import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import {
  AnalyticsService,
  GroupBy,
  GroupStats,
  StatsFilters,
} from '../analytics/analytics.service';
import { EmployeeService, FilterOptions } from '../employees/employee.service';

/** Pay statistics in USD by country, department or job title, with filters and local figures for one country. */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatButtonModule, MatProgressBarModule, MatTableModule],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage {
  private readonly analytics = inject(AnalyticsService);
  private readonly employees = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<void>();

  protected readonly groupings: { value: GroupBy; label: string }[] = [
    { value: 'country', label: 'Country' },
    { value: 'department', label: 'Department' },
    { value: 'jobTitle', label: 'Job title' },
  ];
  protected readonly groupBy = signal<GroupBy>('country');
  protected readonly filters = signal<StatsFilters>({ country: '', department: '', jobTitle: '' });
  protected readonly options = signal<FilterOptions | null>(null);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly groups = signal<GroupStats[]>([]);
  protected readonly showLocal = signal(false);

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.analytics.salaryStats(this.groupBy(), this.filters()).pipe(
            tap((result) => {
              this.groups.set(result.groups);
              this.showLocal.set(
                this.filters().country !== '' && result.groups.some((g) => g.local !== null),
              );
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
      .subscribe({ next: (options) => this.options.set(options), error: () => undefined });
    this.load();
  }

  protected columns(): string[] {
    const base = ['group', 'count', 'total', 'average', 'median', 'minimum', 'maximum'];
    return this.showLocal()
      ? [...base, 'localAverage', 'localMedian', 'localMinimum', 'localMaximum']
      : base;
  }

  protected load(): void {
    this.requests.next();
  }

  protected setGroupBy(value: string): void {
    this.groupBy.set(value as GroupBy);
    this.load();
  }

  protected setFilter(name: keyof StatsFilters, value: string): void {
    this.filters.update((current) => ({ ...current, [name]: value }));
    this.load();
  }
}
