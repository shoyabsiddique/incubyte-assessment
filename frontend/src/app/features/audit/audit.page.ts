import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AuditHistoryComponent } from './audit-history.component';

@Component({
  selector: 'app-audit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AuditHistoryComponent],
  template: `
    <h2>Audit history</h2>
    <p>Who changed what, and when, across the organisation.</p>
    <app-audit-history />
  `,
  styles: `
    :host {
      display: block;
      padding: 16px 24px;
    }
  `,
})
export class AuditPage {}
