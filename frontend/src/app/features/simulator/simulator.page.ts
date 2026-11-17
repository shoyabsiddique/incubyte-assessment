import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-simulator-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Salary simulator</h2>
    <p>Read-only increment projections will run here.</p>
  `,
})
export class SimulatorPage {}
