import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-compensation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Compensation</h2>
    <p>Salary revisions and history will be managed here.</p>
  `,
})
export class CompensationPage {}
