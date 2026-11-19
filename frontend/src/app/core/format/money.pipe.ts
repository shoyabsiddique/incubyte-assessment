import { Pipe, PipeTransform, inject } from '@angular/core';
import { MoneyFormatService, MoneyValue } from './money-format.service';

/**
 * Writes an amount and its currency in the viewer's chosen style. It is impure on purpose: it reads the
 * format signals, so a change of format updates every amount on screen without a reload.
 */
@Pipe({ name: 'money', pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly format = inject(MoneyFormatService);

  transform(value: MoneyValue | null | undefined): string {
    return value ? this.format.format(value) : '';
  }
}
