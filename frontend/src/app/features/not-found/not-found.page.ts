import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Page not found</h2>
    <p>The page you asked for does not exist.</p>
  `,
})
export class NotFoundPage {}
