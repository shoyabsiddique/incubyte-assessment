import { Injectable, computed, signal } from '@angular/core';

export type CurrencyStyle = 'code' | 'symbol' | 'both';
export type DigitGrouping = 'international' | 'indian';

export const FORMAT_KEY = 'acme.money-format';

export interface MoneyValue {
  amount: string | number;
  currency: string;
}

const STYLE_LABELS: Record<CurrencyStyle, string> = {
  code: 'Code',
  symbol: 'Symbol',
  both: 'Symbol and code',
};

const GROUPING_LABELS: Record<DigitGrouping, string> = {
  international: 'International grouping',
  indian: 'Indian grouping',
};

/**
 * How money is written everywhere in the app: with the currency code (145,700.00 AUD), the symbol
 * (A$145,700.00) or both, and with international or Indian (lakh and crore) digit grouping. The choice
 * is the viewer's own and is remembered in their browser.
 */
@Injectable({ providedIn: 'root' })
export class MoneyFormatService {
  readonly style = signal<CurrencyStyle>('code');
  readonly grouping = signal<DigitGrouping>('international');

  readonly styleLabels = STYLE_LABELS;
  readonly groupingLabels = GROUPING_LABELS;

  /** A short description of the current choice, for the menu button. */
  readonly summary = computed(
    () =>
      `${STYLE_LABELS[this.style()]}, ${GROUPING_LABELS[this.grouping()].replace(' grouping', ' grouping')}`,
  );

  constructor() {
    const saved = read();
    this.style.set(saved.style);
    this.grouping.set(saved.grouping);
  }

  setStyle(style: CurrencyStyle): void {
    this.style.set(style);
    this.save();
  }

  setGrouping(grouping: DigitGrouping): void {
    this.grouping.set(grouping);
    this.save();
  }

  /** An amount with its currency, in the chosen style. */
  format(money: MoneyValue): string {
    const value = Number(money.amount);
    const code = money.currency;
    const style = this.style();
    if (style === 'code') {
      return `${this.number(value)} ${code}`;
    }
    const symbolic = this.symbolic(value, code);
    return style === 'symbol' ? symbolic : `${symbolic} ${code}`;
  }

  /** A plain number with the chosen grouping. */
  number(value: number | string, decimals = 2): string {
    return new Intl.NumberFormat(this.locale(), {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(Number(value));
  }

  /** What a typed amount will look like once saved, or null while it is not a usable amount. */
  preview(typed: string, currency: string): string | null {
    const plain = this.plain(typed);
    if (!/^\d+(\.\d+)?$/.test(plain)) {
      return null;
    }
    return this.format({ amount: plain, currency });
  }

  /** A typed amount as the plain digits the server expects: thousands separators and spaces removed. */
  plain(typed: string): string {
    return typed.replace(/[,\s]/g, '');
  }

  /** The same sample amount written in a given style and grouping, so the menu can show each choice. */
  sample(style: CurrencyStyle, grouping: DigitGrouping): string {
    const locale = grouping === 'indian' ? 'en-IN' : 'en-US';
    const digits = new Intl.NumberFormat(locale, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    const number = digits.format(1234567.5);
    if (style === 'code') {
      return `${number} INR`;
    }
    const symbol = `₹${number}`;
    return style === 'symbol' ? symbol : `${symbol} INR`;
  }

  private locale(): string {
    return this.grouping() === 'indian' ? 'en-IN' : 'en-US';
  }

  private symbolic(value: number, currency: string): string {
    return new Intl.NumberFormat(this.locale(), {
      style: 'currency',
      currency,
      currencyDisplay: 'symbol',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
      .format(value)
      .replace(/ /g, ' ');
  }

  private save(): void {
    try {
      localStorage.setItem(
        FORMAT_KEY,
        JSON.stringify({ style: this.style(), grouping: this.grouping() }),
      );
    } catch {
      // Not remembering the choice is better than failing to change it.
    }
  }
}

function read(): { style: CurrencyStyle; grouping: DigitGrouping } {
  const fallback = { style: 'code' as CurrencyStyle, grouping: 'international' as DigitGrouping };
  try {
    const parsed = JSON.parse(localStorage.getItem(FORMAT_KEY) ?? 'null') as Record<
      string,
      unknown
    > | null;
    return {
      style:
        parsed?.['style'] === 'symbol' ||
        parsed?.['style'] === 'both' ||
        parsed?.['style'] === 'code'
          ? parsed['style']
          : fallback.style,
      grouping:
        parsed?.['grouping'] === 'indian' || parsed?.['grouping'] === 'international'
          ? parsed['grouping']
          : fallback.grouping,
    };
  } catch {
    return fallback;
  }
}
