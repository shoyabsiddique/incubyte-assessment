import { DecimalPipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AppConfigService } from '../../core/config/app-config.service';
import { EmployeeService, FilterOptions, Money } from '../employees/employee.service';
import { MoneyPipe } from '../../core/format/money.pipe';

type Scope = 'ORGANIZATION' | 'COUNTRY' | 'DEPARTMENT';

interface SimulationResult {
  percent: string;
  scope: Scope;
  value: string | null;
  employees: number;
  currentPayroll: Money;
  additionalExpenditure: Money;
  projectedPayroll: Money;
}

/** What-if raise planning. Nothing here changes any salary; the server only reads. */
@Component({
  selector: 'app-simulator-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, DecimalPipe, MatButtonModule, MatProgressBarModule],
  templateUrl: './simulator.page.html',
  styleUrl: './simulator.page.scss',
})
export class SimulatorPage {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);
  private readonly employees = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly options = signal<FilterOptions | null>(null);
  protected readonly percent = signal('');
  protected readonly scope = signal<Scope>('ORGANIZATION');
  protected readonly value = signal('');
  protected readonly busy = signal(false);
  protected readonly result = signal<SimulationResult | null>(null);
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly generalError = signal<string | null>(null);

  constructor() {
    this.employees
      .filters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (o) => this.options.set(o), error: () => undefined });
  }

  /** Any change to an input makes the shown result stale, so it is removed rather than left looking current. */
  private changed(): void {
    this.result.set(null);
    this.errors.set({});
    this.generalError.set(null);
  }

  protected setPercent(text: string): void {
    this.percent.set(text);
    this.changed();
  }

  protected setScope(scope: string): void {
    this.scope.set(scope as Scope);
    this.value.set('');
    this.changed();
  }

  protected setValue(text: string): void {
    this.value.set(text);
    this.changed();
  }

  protected run(): void {
    this.changed();
    const problems: Record<string, string> = {};
    if (this.percent().trim() === '') {
      problems['percent'] = 'Enter a percentage from 0 to 100';
    }
    if (this.scope() !== 'ORGANIZATION' && this.value() === '') {
      problems['value'] = `Choose a ${this.scope() === 'COUNTRY' ? 'country' : 'department'}`;
    }
    if (Object.keys(problems).length > 0) {
      this.errors.set(problems);
      return;
    }
    this.busy.set(true);
    this.http
      .post<SimulationResult>(`${this.config.apiBaseUrl}/simulations`, {
        percent: this.percent().trim(),
        scope: this.scope(),
        value: this.scope() === 'ORGANIZATION' ? null : this.value(),
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.result.set(result);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.showError(error);
        },
      });
  }

  private showError(error: unknown): void {
    const body =
      error instanceof HttpErrorResponse
        ? (error.error as { errors?: { field: string; message: string }[] } | null)
        : null;
    if (error instanceof HttpErrorResponse && error.status === 400 && body?.errors) {
      const labelled: Record<string, string> = {};
      for (const problem of body.errors) {
        labelled[problem.field] =
          `${problem.field === 'percent' ? 'Percentage' : 'Value'} ${problem.message}`;
      }
      this.errors.set(labelled);
      return;
    }
    this.generalError.set('The simulation could not be run. Please try again.');
  }
}
