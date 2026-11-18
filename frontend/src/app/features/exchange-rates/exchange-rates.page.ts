import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ExchangeRate, ExchangeRateService } from './exchange-rate.service';

/** Shows the rate of every currency to USD and lets the HR Manager edit one at a time. */
@Component({
  selector: 'app-exchange-rates-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  templateUrl: './exchange-rates.page.html',
  styleUrl: './exchange-rates.page.scss',
})
export class ExchangeRatesPage {
  private readonly service = inject(ExchangeRateService);

  protected readonly columns = ['currency', 'rate', 'actions'];
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly rates = signal<ExchangeRate[]>([]);
  protected readonly editing = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly conflict = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly saved = signal<string | null>(null);
  protected readonly rateControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  constructor() {
    this.load();
    this.rateControl.valueChanges.subscribe(() => {
      if (this.rateControl.hasError('server')) {
        this.rateControl.updateValueAndValidity({ emitEvent: false });
      }
    });
  }

  protected load(): void {
    this.state.set('loading');
    this.service.list().subscribe({
      next: (rates) => {
        this.rates.set(rates);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected edit(rate: ExchangeRate): void {
    this.saved.set(null);
    this.conflict.set(false);
    this.generalError.set(null);
    this.rateControl.reset(rate.rate);
    this.editing.set(rate.currency);
  }

  protected cancel(): void {
    this.editing.set(null);
    this.conflict.set(false);
    this.generalError.set(null);
  }

  protected reload(): void {
    this.cancel();
    this.load();
  }

  protected save(rate: ExchangeRate): void {
    this.generalError.set(null);
    this.conflict.set(false);
    this.rateControl.markAsTouched();
    if (this.rateControl.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.service.update(rate.currency, this.rateControl.value.trim(), rate.version).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.rates.update((all) => all.map((r) => (r.currency === updated.currency ? updated : r)));
        this.editing.set(null);
        this.saved.set(`${updated.currency} rate saved.`);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.showError(error);
      },
    });
  }

  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const body =
      error instanceof HttpErrorResponse
        ? (error.error as { code?: string; errors?: { field: string; message: string }[] } | null)
        : null;
    if (status === 409) {
      this.conflict.set(true);
      return;
    }
    const problem = body?.errors?.find((e) => e.field === 'rate');
    if (status === 400 && problem) {
      this.rateControl.setErrors({ server: `Rate ${problem.message}` });
      this.rateControl.markAsTouched();
      return;
    }
    if (status === 400 && body?.code) {
      this.generalError.set('That rate can not be saved.');
      return;
    }
    this.generalError.set('The rate could not be saved. Please try again.');
  }
}
