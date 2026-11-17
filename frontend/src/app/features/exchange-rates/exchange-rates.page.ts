import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-exchange-rates-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Exchange rates</h2>
    <p>Exchange rates to USD will be viewed and edited here.</p>
  `,
})
export class ExchangeRatesPage {}
