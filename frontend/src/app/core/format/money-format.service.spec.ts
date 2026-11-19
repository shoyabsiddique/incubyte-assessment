import { TestBed } from '@angular/core/testing';
import { FORMAT_KEY, MoneyFormatService } from './money-format.service';

const AUD = { amount: '145700.5', currency: 'AUD' };
const INR = { amount: '1234567.8', currency: 'INR' };

describe('MoneyFormatService', () => {
  beforeEach(() => {
    localStorage.removeItem(FORMAT_KEY);
    TestBed.resetTestingModule();
  });
  afterEach(() => localStorage.removeItem(FORMAT_KEY));

  it('writes the amount with its currency code by default, as the product always has', () => {
    const f = TestBed.inject(MoneyFormatService);

    expect(f.format(AUD)).toBe('145,700.50 AUD');
    expect(f.format({ amount: '0', currency: 'USD' })).toBe('0.00 USD');
  });

  it('can lead with the currency symbol instead', () => {
    const f = TestBed.inject(MoneyFormatService);
    f.setStyle('symbol');

    expect(f.format(AUD)).toBe('A$145,700.50');
    expect(f.format({ amount: '94705', currency: 'USD' })).toBe('$94,705.00');
    expect(f.format({ amount: '1500', currency: 'GBP' })).toBe('£1,500.00');
  });

  it('can show the symbol and the code together', () => {
    const f = TestBed.inject(MoneyFormatService);
    f.setStyle('both');

    expect(f.format(AUD)).toBe('A$145,700.50 AUD');
  });

  it('can group digits the Indian way, in lakhs and crores', () => {
    const f = TestBed.inject(MoneyFormatService);
    f.setGrouping('indian');

    expect(f.format(INR)).toBe('12,34,567.80 INR');
    expect(f.number(1234567.8)).toBe('12,34,567.80');
    f.setGrouping('international');
    expect(f.format(INR)).toBe('1,234,567.80 INR');
  });

  it('formats plain numbers with the chosen grouping and a number of decimals', () => {
    const f = TestBed.inject(MoneyFormatService);

    expect(f.number(1234.5, 0)).toBe('1,235');
    expect(f.number('99.1')).toBe('99.10');
  });

  it('remembers the choice for the next visit', () => {
    const first = TestBed.inject(MoneyFormatService);
    first.setStyle('symbol');
    first.setGrouping('indian');
    TestBed.resetTestingModule();

    const next = TestBed.inject(MoneyFormatService);

    expect(next.style()).toBe('symbol');
    expect(next.grouping()).toBe('indian');
  });

  it('ignores a saved value it does not understand', () => {
    localStorage.setItem(FORMAT_KEY, '{"style":"words","grouping":"nope"}');

    const f = TestBed.inject(MoneyFormatService);

    expect(f.style()).toBe('code');
    expect(f.grouping()).toBe('international');
  });

  it('previews what a typed amount will look like, forgiving thousands separators', () => {
    const f = TestBed.inject(MoneyFormatService);

    expect(f.preview('120,000', 'AUD')).toBe('120,000.00 AUD');
    expect(f.preview(' 95 000.5 ', 'AUD')).toBe('95,000.50 AUD');
    expect(f.preview('', 'AUD')).toBeNull();
    expect(f.preview('abc', 'AUD')).toBeNull();
    expect(f.preview('-5', 'AUD')).toBeNull();
  });

  it('turns a typed amount into the plain digits the server expects', () => {
    const f = TestBed.inject(MoneyFormatService);

    expect(f.plain('120,000')).toBe('120000');
    expect(f.plain(' 95 000.50 ')).toBe('95000.50');
  });

  it('describes the current choice for the menu', () => {
    const f = TestBed.inject(MoneyFormatService);
    f.setStyle('symbol');
    f.setGrouping('indian');

    expect(f.summary()).toBe('Symbol, Indian grouping');
  });

  it('shows a sample of every choice for the menu, whatever is currently selected', () => {
    const f = TestBed.inject(MoneyFormatService);

    expect(f.sample('code', 'international')).toBe('1,234,567.50 INR');
    expect(f.sample('symbol', 'international')).toBe('₹1,234,567.50');
    expect(f.sample('both', 'indian')).toBe('₹12,34,567.50 INR');
    expect(f.sample('code', 'indian')).toBe('12,34,567.50 INR');
  });
});
