import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { SalaryRecord } from './employee.service';
import { FORMAT_KEY, MoneyFormatService } from '../../core/format/money-format.service';
import { SalaryRevisionFormComponent } from './salary-revision-form.component';

const API = 'http://api.test/api/v1';
const ID = 'e1b2c3d4-0000-4000-8000-000000000001';

const NEW_RECORD: SalaryRecord = {
  id: 'r4',
  amount: { amount: '150000.00', currency: 'AUD' },
  amountUsd: { amount: '97500.00', currency: 'USD' },
  effectiveStart: '2025-01-01',
  effectiveEnd: null,
  current: true,
};

describe('SalaryRevisionFormComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<SalaryRevisionFormComponent>;
  let view: HTMLElement;
  let revised: SalaryRecord[];
  let reloads: number;
  let cancels: number;

  afterEach(() => localStorage.removeItem(FORMAT_KEY));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalaryRevisionFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SalaryRevisionFormComponent);
    fixture.componentRef.setInput('employeeId', ID);
    fixture.componentRef.setInput('current', { amount: '145700.00', currency: 'AUD' });
    revised = [];
    reloads = 0;
    cancels = 0;
    fixture.componentInstance.revised.subscribe((r) => revised.push(r));
    fixture.componentInstance.reload.subscribe(() => reloads++);
    fixture.componentInstance.cancelled.subscribe(() => cancels++);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function type(name: string, value: string) {
    const control = view.querySelector(`[formcontrolname="${name}"]`) as HTMLInputElement;
    control.value = value;
    control.dispatchEvent(new Event('input'));
  }

  async function submit() {
    (view.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function submitValid() {
    type('amount', '150000');
    type('effectiveStart', '2025-01-01');
    await submit();
  }

  it('shows the current salary for reference and the currency, which can not be edited', () => {
    expect(view.querySelector('.reference')?.textContent).toContain('145,700.00 AUD');
    expect(view.querySelector('.currency')?.textContent).toContain('AUD');
    expect(view.querySelector('input.currency')).toBeNull();
    expect(view.querySelector('[formcontrolname="currency"]')).toBeNull();
  });

  it('does not call the server while the amount or the date is missing, and says which', async () => {
    await submit();

    http.expectNone(`${API}/employees/${ID}/salary-revisions`);
    expect(view.textContent).toContain('New salary is required');
    expect(view.textContent).toContain('Effective date is required');
  });

  it('sends the new amount and date and reports the new record', async () => {
    await submitValid();

    const request = http.expectOne(`${API}/employees/${ID}/salary-revisions`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ amount: '150000', effectiveStart: '2025-01-01' });
    request.flush(NEW_RECORD, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    expect(revised).toEqual([NEW_RECORD]);
  });

  it('shows a rejected date next to its field and keeps what was typed', async () => {
    await submitValid();

    http.expectOne(`${API}/employees/${ID}/salary-revisions`).flush(
      {
        code: 'REVISION_DATE_INVALID',
        errors: [
          {
            field: 'effectiveStart',
            message: "must be after the current salary's start date (2024-03-01)",
          },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(view.textContent).toContain('start date (2024-03-01)');
    expect((view.querySelector('[formcontrolname="amount"]') as HTMLInputElement).value).toBe(
      '150000',
    );
    expect(revised).toEqual([]);
  });

  it('shows a rejected amount next to its field', async () => {
    await submitValid();

    http.expectOne(`${API}/employees/${ID}/salary-revisions`).flush(
      {
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'amount', message: 'must be a positive amount with at most 2 decimals' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(view.textContent).toContain('New salary must be a positive amount');
  });

  it('clears a server complaint once the value is changed', async () => {
    await submitValid();
    http.expectOne(`${API}/employees/${ID}/salary-revisions`).flush(
      {
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'amount', message: 'must be a positive amount' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    type('amount', '160000');
    fixture.detectChanges();

    expect(fixture.componentInstance.form.controls.amount.hasError('server')).toBe(false);
  });

  it('shows a conflict banner with a way to reload when another revision won', async () => {
    await submitValid();

    http
      .expectOne(`${API}/employees/${ID}/salary-revisions`)
      .flush({ code: 'REVISION_OVERLAP' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(view.querySelector('.banner')?.textContent).toContain('revised by someone else');
    (view.querySelector('button.reload') as HTMLButtonElement).click();
    expect(reloads).toBe(1);
    expect((view.querySelector('[formcontrolname="amount"]') as HTMLInputElement).value).toBe(
      '150000',
    );
  });

  it('shows a general message when the server fails', async () => {
    await submitValid();

    http
      .expectOne(`${API}/employees/${ID}/salary-revisions`)
      .flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(view.querySelector('.banner')?.textContent).toContain('could not be saved');
  });

  it('can be cancelled', () => {
    (view.querySelector('button.cancel') as HTMLButtonElement).click();

    expect(cancels).toBe(1);
  });
  it('previews the new salary in the chosen format as it is typed', async () => {
    type('amount', '120,000');
    fixture.detectChanges();

    expect(view.querySelector('.preview')?.textContent).toContain('120,000.00 AUD');

    TestBed.inject(MoneyFormatService).setStyle('symbol');
    fixture.detectChanges();
    expect(view.querySelector('.preview')?.textContent).toContain('A$120,000.00');
    localStorage.removeItem(FORMAT_KEY);
  });

  it('shows no preview until there is a usable amount', async () => {
    expect(view.querySelector('.preview')).toBeNull();

    type('amount', 'abc');
    fixture.detectChanges();

    expect(view.querySelector('.preview')).toBeNull();
  });

  it('accepts thousands separators and sends the plain digits', async () => {
    type('amount', '120,000.50');
    type('effectiveStart', '2025-01-01');
    await submit();

    const request = http.expectOne(`${API}/employees/${ID}/salary-revisions`);
    expect(request.request.body).toEqual({ amount: '120000.50', effectiveStart: '2025-01-01' });
    request.flush(NEW_RECORD, { status: 201, statusText: 'Created' });
  });
});
