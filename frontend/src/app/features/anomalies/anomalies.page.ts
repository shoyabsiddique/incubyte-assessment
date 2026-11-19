import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { Anomaly, AnalyticsService } from '../analytics/analytics.service';
import { EmployeeService, FilterOptions } from '../employees/employee.service';
import { MoneyPipe } from '../../core/format/money.pipe';

/** People whose pay is far from their peer group's median, offered for review and never as a verdict. */
@Component({
  selector: 'app-anomalies-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, DecimalPipe, RouterLink, MatButtonModule, MatProgressBarModule],
  templateUrl: './anomalies.page.html',
  styleUrl: './anomalies.page.scss',
})
export class AnomaliesPage {
  private readonly analytics = inject(AnalyticsService);
  private readonly employees = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<void>();

  protected readonly options = signal<FilterOptions | null>(null);
  protected readonly country = signal('');
  protected readonly department = signal('');
  protected readonly direction = signal('');
  protected readonly pageNumber = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly items = signal<Anomaly[]>([]);

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set('loading')),
        switchMap(() =>
          this.analytics
            .anomalies(
              {
                country: this.country(),
                department: this.department(),
                direction: this.direction(),
              },
              this.pageNumber(),
              20,
            )
            .pipe(
              tap((result) => {
                this.items.set(result.items);
                this.totalPages.set(result.totalPages);
                this.totalElements.set(result.totalElements);
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
      .subscribe({ next: (o) => this.options.set(o), error: () => undefined });
    this.load();
  }

  protected load(): void {
    this.requests.next();
  }

  protected setFilter(name: 'country' | 'department' | 'direction', value: string): void {
    this[name].set(value);
    this.pageNumber.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.pageNumber.set(page);
    this.load();
  }

  /** "+45.00%" or "-35.00%": the sign says which side of the median the salary is on. */
  protected signed(item: Anomaly): string {
    const value = item.deviationPercent;
    return `${value.startsWith('-') ? '' : '+'}${value}%`;
  }
}
