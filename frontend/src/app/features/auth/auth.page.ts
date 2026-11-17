import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-auth-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <mat-card class="sign-in-card">
      <mat-card-header>
        <h2>Sign in</h2>
      </mat-card-header>
      <mat-card-content>
        <p>Sign in with your HR Manager email and password.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="username" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input
              matInput
              type="password"
              formControlName="password"
              autocomplete="current-password"
            />
          </mat-form-field>
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
          <button mat-flat-button type="submit" [disabled]="busy()">Sign in</button>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .sign-in-card {
      max-width: 420px;
      margin: 48px auto;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .error {
      color: var(--mat-sys-error);
      margin: 0;
    }
  `,
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

  protected submit(): void {
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
