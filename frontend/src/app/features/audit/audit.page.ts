import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-audit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Audit history</h2>
    <p>Who changed what, and when, will be shown here.</p>
  `,
})
export class AuditPage {}
