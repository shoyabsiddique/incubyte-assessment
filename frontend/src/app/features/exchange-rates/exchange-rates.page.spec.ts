import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { ExchangeRatesPage } from './exchange-rates.page';

const API = 'http://api.test/api/v1';

const RATES = [
  { currency: 'USD', rate: '1', version: 0, editable: false },
  { currency: 'AUD', rate: '0.65', version: 2, editable: true },
  { currency: 'INR', rate: '0.012', version: 0, editable: true },
];

describe('ExchangeRatesPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ExchangeRatesPage>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExchangeRatesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ExchangeRatesPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function load(rates: object[] = RATES) {
    http.expectOne(`${API}/exchange-rates`).flush(rates);
    await settle();
  }

  function row(currency: string): HTMLElement {
    return Array.from(page.querySelectorAll('tr[mat-row]')).find((r) =>
      r.textContent?.includes(currency),
    ) as HTMLElement;
  }

  async function startEditing(currency: string, value?: string) {
    (row(currency).querySelector('button.edit') as HTMLButtonElement).click();
    await settle();
    if (value !== undefined) {
      const input = page.querySelector('input.rate-input') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
  }

  async function save() {
    (page.querySelector('button.save') as HTMLButtonElement).click();
    await settle();
  }

  it('shows a loading indicator until the rates arrive', async () => {
    expect(page.querySelector('[role="progressbar"]')).not.toBeNull();

    await load();

    expect(page.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('lists one row per currency with its rate to USD', async () => {
    await load();

    expect(page.querySelectorAll('tr[mat-row]').length).toBe(3);
    expect(row('AUD').textContent).toContain('0.65');
    expect(row('INR').textContent).toContain('0.012');
  });

  it('shows USD as fixed at 1 with no way to edit it', async () => {
    await load();

    expect(row('USD').textContent).toContain('1');
    expect(row('USD').textContent).toContain('Fixed');
    expect(row('USD').querySelector('button.edit')).toBeNull();
  });

  it('shows the error with a way to try again when the rates can not be loaded', async () => {
    http.expectOne(`${API}/exchange-rates`).flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');
    (page.querySelector('button.retry') as HTMLButtonElement).click();
    await settle();
    await load();
    expect(page.querySelectorAll('tr[mat-row]').length).toBe(3);
  });

  it('opens an input holding the current rate when a row is edited', async () => {
    await load();

    await startEditing('INR');

    expect((page.querySelector('input.rate-input') as HTMLInputElement).value).toBe('0.012');
  });

  it('saves the edited rate with its version, shows the new value and confirms', async () => {
    await load();
    await startEditing('INR', '0.0125');

    await save();

    const request = http.expectOne(`${API}/exchange-rates/INR`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rate: '0.0125', version: 0 });
    request.flush({ currency: 'INR', rate: '0.0125', version: 1, editable: true });
    await settle();

    expect(row('INR').textContent).toContain('0.0125');
    expect(page.querySelector('input.rate-input')).toBeNull();
    expect(page.querySelector('[role="status"]')?.textContent).toContain('INR rate saved');
  });

  it('uses the new version for the next edit of the same currency', async () => {
    await load();
    await startEditing('INR', '0.0125');
    await save();
    http
      .expectOne(`${API}/exchange-rates/INR`)
      .flush({ currency: 'INR', rate: '0.0125', version: 1, editable: true });
    await settle();

    await startEditing('INR', '0.013');
    await save();

    expect(http.expectOne(`${API}/exchange-rates/INR`).request.body).toEqual({
      rate: '0.013',
      version: 1,
    });
  });

  it('shows a rejected rate next to the input and keeps what was typed', async () => {
    await load();
    await startEditing('INR', '-1');
    await save();

    http.expectOne(`${API}/exchange-rates/INR`).flush(
      {
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'rate', message: 'must be a number greater than 0 with at most 8 decimals' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(page.querySelector('mat-error')?.textContent).toContain('greater than 0');
    expect((page.querySelector('input.rate-input') as HTMLInputElement).value).toBe('-1');
  });

  it('does not call the server for an empty rate', async () => {
    await load();
    await startEditing('INR', '');

    await save();

    http.expectNone(`${API}/exchange-rates/INR`);
    expect(page.querySelector('mat-error')?.textContent).toContain('Rate is required');
  });

  it('shows a conflict banner with a reload when the rate was changed by someone else', async () => {
    await load();
    await startEditing('INR', '0.02');
    await save();

    http
      .expectOne(`${API}/exchange-rates/INR`)
      .flush({ code: 'CONCURRENT_MODIFICATION' }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(page.querySelector('.banner')?.textContent).toContain('changed by someone else');
    (page.querySelector('button.reload') as HTMLButtonElement).click();
    await settle();
    http.expectOne(`${API}/exchange-rates`).flush(RATES);
    await settle();
    expect(page.querySelector('.banner')).toBeNull();
    expect(page.querySelector('input.rate-input')).toBeNull();
  });

  it('can stop editing without saving', async () => {
    await load();
    await startEditing('INR', '0.5');

    (page.querySelector('button.cancel') as HTMLButtonElement).click();
    await settle();

    http.expectNone(`${API}/exchange-rates/INR`);
    expect(page.querySelector('input.rate-input')).toBeNull();
    expect(row('INR').textContent).toContain('0.012');
  });
});
