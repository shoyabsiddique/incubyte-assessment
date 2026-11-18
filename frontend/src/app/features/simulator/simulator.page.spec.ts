import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { SimulatorPage } from './simulator.page';

const API = 'http://api.test/api/v1';

const RESULT = {
  percent: '10',
  scope: 'ORGANIZATION',
  value: null,
  employees: 9000,
  currentPayroll: { amount: '1000000.00', currency: 'USD' },
  additionalExpenditure: { amount: '100000.00', currency: 'USD' },
  projectedPayroll: { amount: '1100000.00', currency: 'USD' },
};

describe('SimulatorPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<SimulatorPage>;
  let view: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SimulatorPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SimulatorPage);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne(`${API}/employees/filters`).flush({
      countries: [{ code: 'INDIA', name: 'India' }],
      departments: ['Engineering'],
      jobTitles: [],
    });
    await fixture.whenStable();
    fixture.detectChanges();
  });

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function typePercent(value: string) {
    const input = view.querySelector('input[name="percent"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  async function chooseScope(value: string) {
    const select = view.querySelector('select[name="scope"]') as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await settle();
  }

  async function run() {
    (view.querySelector('button.run') as HTMLButtonElement).click();
    await settle();
  }

  it('says the simulation is read-only and changes no salaries', () => {
    expect(view.querySelector('h2')?.textContent).toContain('Salary simulator');
    expect(view.querySelector('.read-only')?.textContent).toContain('changes no salaries');
  });

  it('offers no value picker for the whole organisation and one for a country or department', async () => {
    expect(view.querySelector('select[name="value"]')).toBeNull();

    await chooseScope('COUNTRY');
    expect(view.querySelectorAll('select[name="value"] option').length).toBe(2);
    expect(view.querySelector('select[name="value"]')?.textContent).toContain('India');

    await chooseScope('DEPARTMENT');
    expect(view.querySelector('select[name="value"]')?.textContent).toContain('Engineering');
  });

  it('does not call the server without a percentage and says so', async () => {
    await run();

    http.expectNone(`${API}/simulations`);
    expect(view.textContent).toContain('Enter a percentage');
  });

  it('runs a 10% organisation simulation and shows the payroll impact in USD', async () => {
    await typePercent('10');
    await run();

    const request = http.expectOne(`${API}/simulations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ percent: '10', scope: 'ORGANIZATION', value: null });
    request.flush(RESULT);
    await settle();

    const results = view.querySelector('.results')?.textContent ?? '';
    expect(results).toContain('1,000,000.00 USD');
    expect(results).toContain('100,000.00 USD');
    expect(results).toContain('1,100,000.00 USD');
    expect(results).toContain('9,000');
  });

  it('sends the chosen country', async () => {
    await typePercent('5');
    await chooseScope('COUNTRY');
    const select = view.querySelector('select[name="value"]') as HTMLSelectElement;
    select.value = 'INDIA';
    select.dispatchEvent(new Event('change'));
    await settle();

    await run();

    expect(http.expectOne(`${API}/simulations`).request.body).toEqual({
      percent: '5',
      scope: 'COUNTRY',
      value: 'INDIA',
    });
  });

  it('removes the previous result as soon as an input changes', async () => {
    await typePercent('10');
    await run();
    http.expectOne(`${API}/simulations`).flush(RESULT);
    await settle();
    expect(view.querySelector('.results')).not.toBeNull();

    await typePercent('12');

    expect(view.querySelector('.results')).toBeNull();
  });

  it('shows a rejected percentage next to the input and keeps what was typed', async () => {
    await typePercent('150');
    await run();

    http.expectOne(`${API}/simulations`).flush(
      {
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'percent', message: 'must be a number from 0 to 100' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(view.textContent).toContain('must be a number from 0 to 100');
    expect((view.querySelector('input[name="percent"]') as HTMLInputElement).value).toBe('150');
    expect(view.querySelector('.results')).toBeNull();
  });

  it('shows a server error without clearing the inputs', async () => {
    await typePercent('10');
    await run();

    http.expectOne(`${API}/simulations`).flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(view.querySelector('[role="alert"]')?.textContent).toContain('could not be run');
    expect((view.querySelector('input[name="percent"]') as HTMLInputElement).value).toBe('10');
  });

  it('shows a progress indicator while the simulation runs', async () => {
    await typePercent('10');
    await run();

    expect(view.querySelector('[role="progressbar"]')).not.toBeNull();
    http.expectOne(`${API}/simulations`).flush(RESULT);
    await settle();
    expect(view.querySelector('[role="progressbar"]')).toBeNull();
  });
});
