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
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  EmployeeProfileInput,
  EmployeeService,
  EmployeeSummary,
  NewEmployeeInput,
} from './employee.service';

export const COUNTRIES = [
  { code: 'INDIA', name: 'India', currency: 'INR' },
  { code: 'USA', name: 'United States', currency: 'USD' },
  { code: 'UK', name: 'United Kingdom', currency: 'GBP' },
  { code: 'GERMANY', name: 'Germany', currency: 'EUR' },
  { code: 'SINGAPORE', name: 'Singapore', currency: 'SGD' },
  { code: 'UAE', name: 'United Arab Emirates', currency: 'AED' },
  { code: 'CANADA', name: 'Canada', currency: 'CAD' },
  { code: 'AUSTRALIA', name: 'Australia', currency: 'AUD' },
];

const EMPLOYMENT_TYPES = [
  { value: 'FULL_TIME', label: 'Full time' },
  { value: 'PART_TIME', label: 'Part time' },
  { value: 'CONTRACT', label: 'Contract' },
];

const STATUSES = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_LEAVE', label: 'On leave' },
  { value: 'TERMINATED', label: 'Terminated' },
];

const LABELS: Record<string, string> = {
  firstName: 'First name',
  lastName: 'Last name',
  workEmail: 'Work email',
  jobTitle: 'Job title',
  department: 'Department',
  city: 'City',
  country: 'Country',
  employmentType: 'Employment type',
  status: 'Status',
  hireDate: 'Hire date',
  salaryAmount: 'Salary',
  salaryEffectiveStart: 'Salary start date',
};

/** Adds a new employee (with a starting salary) or edits an existing profile. Talks to the API itself. */
@Component({
  selector: 'app-employee-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './employee-form.component.html',
  styleUrl: './employee-form.component.scss',
})
export class EmployeeFormComponent implements OnInit {
  private readonly service = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly mode = input.required<'create' | 'edit'>();
  readonly employee = input<EmployeeSummary | null>(null);
  readonly saved = output<EmployeeSummary>();
  readonly cancelled = output<void>();
  readonly reload = output<void>();

  protected readonly countries = COUNTRIES;
  protected readonly employmentTypes = EMPLOYMENT_TYPES;
  protected readonly statuses = STATUSES;
  protected readonly busy = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly conflict = signal(false);
  protected readonly currency = signal<string | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    workEmail: ['', [Validators.required, Validators.email]],
    jobTitle: ['', Validators.required],
    department: ['', Validators.required],
    city: ['', Validators.required],
    country: ['', Validators.required],
    employmentType: ['', Validators.required],
    status: ['', Validators.required],
    hireDate: ['', Validators.required],
    salaryAmount: [''],
    salaryEffectiveStart: [''],
  });

  ngOnInit(): void {
    const current = this.employee();
    if (this.mode() === 'create') {
      this.form.controls.salaryAmount.addValidators(Validators.required);
      this.form.controls.salaryEffectiveStart.addValidators(Validators.required);
    } else if (current) {
      this.form.patchValue({
        firstName: current.firstName,
        lastName: current.lastName,
        workEmail: current.workEmail,
        jobTitle: current.jobTitle,
        department: current.department,
        city: current.city,
        country: current.country,
        employmentType: current.employmentType,
        status: current.status,
        hireDate: current.hireDate,
      });
    }
    this.form.controls.country.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((code) => this.showCurrencyOf(code));
    this.showCurrencyOf(this.form.controls.country.value);
  }

  /** Picking a country also decides the salary currency, which is never typed in. */
  setCountry(code: string): void {
    this.form.controls.country.setValue(code);
  }

  private showCurrencyOf(code: string): void {
    this.currency.set(COUNTRIES.find((c) => c.code === code)?.currency ?? null);
  }

  protected label(field: string): string {
    return LABELS[field] ?? field;
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
    const profile: EmployeeProfileInput = {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      workEmail: value.workEmail.trim(),
      jobTitle: value.jobTitle.trim(),
      department: value.department.trim(),
      city: value.city.trim(),
      country: value.country,
      employmentType: value.employmentType,
      status: value.status,
      hireDate: value.hireDate,
    };
    const request =
      this.mode() === 'create'
        ? this.service.create({
            ...profile,
            salaryAmount: value.salaryAmount.trim(),
            salaryCurrency: this.currency() ?? '',
            salaryEffectiveStart: value.salaryEffectiveStart,
          } satisfies NewEmployeeInput)
        : this.service.update(this.employee()!.id, profile, this.employee()!.version);
    request.subscribe({
      next: (employee) => {
        this.busy.set(false);
        this.saved.emit(employee);
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
        ? (error.error as { code?: string; errors?: { field: string; message: string }[] } | null)
        : null;
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409 && body?.code === 'DUPLICATE_EMPLOYEE_EMAIL') {
      this.form.controls.workEmail.setErrors({
        server: 'Another employee already uses this work email',
      });
      return;
    }
    if (status === 409 && body?.code === 'CONCURRENT_MODIFICATION') {
      this.conflict.set(true);
      return;
    }
    if (status === 400 && body?.code === 'VALIDATION_FAILED' && body.errors) {
      const unplaced: string[] = [];
      for (const problem of body.errors) {
        const control = (
          this.form.controls as Record<string, import('@angular/forms').AbstractControl>
        )[problem.field];
        if (control) {
          control.setErrors({ server: `${this.label(problem.field)} ${problem.message}` });
          control.markAsTouched();
        } else {
          unplaced.push(`${problem.field} ${problem.message}`);
        }
      }
      if (unplaced.length > 0) {
        this.generalError.set(`The employee could not be saved: ${unplaced.join('; ')}.`);
      }
      return;
    }
    this.generalError.set('The employee could not be saved. Please try again.');
  }
}
