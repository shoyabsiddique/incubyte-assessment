import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-auth-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './auth.page.html',
  styleUrl: './auth.page.scss',
})
export class SignInPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected submit(): void {
    // A rejected sign-in marks both fields; try again from a clean state.
    this.form.controls.email.updateValueAndValidity();
    this.form.controls.password.updateValueAndValidity();
    if (this.form.invalid) {
      this.error.set('Enter your email and password');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl(this.destination());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.form.controls.email.setErrors({ rejected: true });
        this.form.controls.password.setErrors({ rejected: true });
        this.form.markAllAsTouched();
        this.error.set(
          error instanceof HttpErrorResponse && error.status === 401
            ? 'Invalid email or password'
            : 'Sign-in is unavailable right now. Please try again in a moment.',
        );
      },
    });
  }

  /** Where to go after signing in: the page the visitor wanted, if it is a safe in-app address. */
  private destination(): string {
    const requested = this.route.snapshot.queryParamMap.get('returnUrl');
    const safe =
      requested !== null &&
      requested.startsWith('/') &&
      !requested.startsWith('//') &&
      !requested.startsWith('/\\') &&
      !requested.startsWith('/auth');
    return safe ? requested : '/dashboard';
  }
}
