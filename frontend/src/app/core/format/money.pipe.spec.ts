import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MoneyFormatService, FORMAT_KEY } from './money-format.service';
import { MoneyPipe } from './money.pipe';

@Component({
  imports: [MoneyPipe],
  template: `<span class="v">{{ value | money }}</span>`,
})
class Host {
  value: { amount: string; currency: string } | null = { amount: '1000', currency: 'EUR' };
}

describe('MoneyPipe', () => {
  beforeEach(() => localStorage.removeItem(FORMAT_KEY));
  afterEach(() => localStorage.removeItem(FORMAT_KEY));

  it('formats an amount and currency the way the format service says', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.v').textContent).toBe('1,000.00 EUR');
  });

  it('follows the viewer changing the format while the page is open', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();

    TestBed.inject(MoneyFormatService).setStyle('symbol');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.v').textContent).toBe('€1,000.00');
  });

  it('shows nothing for a missing value', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.value = null;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.v').textContent).toBe('');
  });
});
