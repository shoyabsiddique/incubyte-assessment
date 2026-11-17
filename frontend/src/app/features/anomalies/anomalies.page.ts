import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-anomalies-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Anomalies</h2>
    <p>Potential pay anomalies for HR review will be listed here.</p>
  `,
})
export class AnomaliesPage {}
