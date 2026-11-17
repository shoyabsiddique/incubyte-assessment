import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-auth-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Sign in</h2>
    <p>Sign in with your HR Manager email and password.</p>
  `,
})
export class SignInPage {}
