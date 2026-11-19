import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { EmployeeService, Money, SalaryRecord } from './employee.service';
import { MoneyFormatService } from '../../core/format/money-format.service';
import { MoneyPipe } from '../../core/format/money.pipe';

const LABELS: Record<string, string> = {
  amount: 'New salary',
  effectiveStart: 'Effective date',
};

/** Asks for a new salary and the date it starts. The currency is the current salary's and is never typed in. */
@Component({
  selector: 'app-salary-revision-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './salary-revision-form.component.html',
  styleUrl: './salary-revision-form.component.scss',
})
export class SalaryRevisionFormComponent implements OnInit {
  private readonly service = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly money = inject(MoneyFormatService);

  readonly employeeId = input.required<string>();
  readonly current = input.required<Money | null>();
  readonly revised = output<SalaryRecord>();
  readonly cancelled = output<void>();
  readonly reload = output<void>();

  protected readonly busy = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly conflict = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group({
    amount: ['', Validators.required],
    effectiveStart: ['', Validators.required],
  });

  ngOnInit(): void {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      for (const control of Object.values(this.form.controls)) {
        if (control.hasError('server')) {
          control.updateValueAndValidity({ emitEvent: false });
        }
      }
    });
  }

  /** What the typed amount will look like once saved, in the viewer's chosen format. */
  protected preview(): string | null {
    return this.money.preview(this.form.controls.amount.value, this.current()?.currency ?? 'USD');
  }

  protected submit(): void {
    this.generalError.set(null);
    this.conflict.set(false);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    const value = this.form.getRawValue();
    this.service
      .revise(this.employeeId(), {
        amount: this.money.plain(value.amount.trim()),
        effectiveStart: value.effectiveStart,
      })
      .subscribe({
        next: (record) => {
          this.busy.set(false);
          this.revised.emit(record);
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
    if (status === 400 && body?.errors) {
      const unplaced: string[] = [];
      for (const problem of body.errors) {
        const control = (this.form.controls as Record<string, AbstractControl>)[problem.field];
        if (control) {
          const label = LABELS[problem.field] ?? problem.field;
          control.setErrors({ server: `${label} ${problem.message}` });
          control.markAsTouched();
        } else {
          unplaced.push(`${problem.field} ${problem.message}`);
        }
      }
      if (unplaced.length > 0) {
        this.generalError.set(`The salary could not be saved: ${unplaced.join('; ')}.`);
      }
      return;
    }
    this.generalError.set('The salary could not be saved. Please try again.');
  }
}
