import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppConfigService } from '../../core/config/app-config.service';
import { EmployeeFormComponent } from './employee-form.component';
import { EmployeeSummary } from './employee.service';

const API = 'http://api.test/api/v1';

const EXISTING: EmployeeSummary = {
  id: 'e1',
  employeeId: 'EMP-000001',
  firstName: 'Charlotte',
  lastName: 'Smith',
  workEmail: 'charlotte.smith@acme.example',
  jobTitle: 'Software Engineer',
  department: 'Engineering',
  country: 'AUSTRALIA',
  countryName: 'Australia',
  city: 'Sydney',
  employmentType: 'FULL_TIME',
  status: 'ACTIVE',
  hireDate: '2018-04-23',
  salary: { amount: '145700.00', currency: 'AUD' },
  salaryUsd: { amount: '94705.00', currency: 'USD' },
  version: 3,
};

describe('EmployeeFormComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EmployeeFormComponent>;
  let view: HTMLElement;
  let saved: EmployeeSummary[];
  let reloads: number;

  async function open(mode: 'create' | 'edit') {
    await TestBed.configureTestingModule({
      imports: [EmployeeFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeFormComponent);
    fixture.componentRef.setInput('mode', mode);
    if (mode === 'edit') {
      fixture.componentRef.setInput('employee', EXISTING);
    }
    saved = [];
    reloads = 0;
    fixture.componentInstance.saved.subscribe((e) => saved.push(e));
    fixture.componentInstance.reload.subscribe(() => reloads++);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function input(name: string): HTMLInputElement {
    return view.querySelector(`[formcontrolname="${name}"]`) as HTMLInputElement;
  }

  function type(name: string, value: string) {
    const control = input(name);
    control.value = value;
    control.dispatchEvent(new Event('input'));
  }

  async function submit() {
    (view.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function fillValidNewEmployee() {
    type('firstName', 'Ada');
    type('lastName', 'Lovelace');
    type('workEmail', 'ada@acme.example');
    type('jobTitle', 'Engineer');
    type('department', 'Engineering');
    type('city', 'Sydney');
    type('hireDate', '2024-03-01');
    type('salaryAmount', '100000');
    type('salaryEffectiveStart', '2024-03-01');
    fixture.componentInstance.setCountry('AUSTRALIA');
    fixture.componentInstance.form.patchValue({ employmentType: 'FULL_TIME', status: 'ACTIVE' });
    fixture.detectChanges();
  }

  describe('adding an employee', () => {
    beforeEach(() => open('create'));

    it('does not call the server while required fields are missing and says which', async () => {
      await submit();

      http.expectNone(`${API}/employees`);
      expect(view.textContent).toContain('First name is required');
      expect(view.textContent).toContain('Salary is required');
    });

    it('shows the salary currency of the chosen country, which can not be edited', () => {
      fixture.componentInstance.setCountry('INDIA');
      fixture.detectChanges();

      expect(view.querySelector('.currency')?.textContent).toContain('INR');
      expect(view.querySelector('input.currency')).toBeNull();
    });

    it('sends the new employee with the salary in the country currency and reports the result', async () => {
      fillValidNewEmployee();

      await submit();

      const request = http.expectOne(`${API}/employees`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toMatchObject({
        firstName: 'Ada',
        workEmail: 'ada@acme.example',
        country: 'AUSTRALIA',
        salaryAmount: '100000',
        salaryCurrency: 'AUD',
        salaryEffectiveStart: '2024-03-01',
      });
      request.flush({ ...EXISTING, id: 'new-1' });
      await fixture.whenStable();
      expect(saved.map((e) => e.id)).toEqual(['new-1']);
    });

    it('shows server field errors next to their fields and keeps what was typed', async () => {
      fillValidNewEmployee();
      await submit();

      http.expectOne(`${API}/employees`).flush(
        {
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'salaryAmount', message: 'must be a positive amount' }],
        },
        { status: 400, statusText: 'Bad Request' },
      );
      await fixture.whenStable();
      fixture.detectChanges();

      expect(view.textContent).toContain('must be a positive amount');
      expect(input('firstName').value).toBe('Ada');
      expect(saved).toEqual([]);
    });

    it('shows a duplicate email next to the email field', async () => {
      fillValidNewEmployee();
      await submit();

      http.expectOne(`${API}/employees`).flush(
        { code: 'DUPLICATE_EMPLOYEE_EMAIL' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      fixture.detectChanges();

      expect(view.textContent).toContain('already uses this work email');
    });

    it('shows a general message when the server fails', async () => {
      fillValidNewEmployee();
      await submit();

      http.expectOne(`${API}/employees`).flush({}, { status: 500, statusText: 'Server Error' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(view.querySelector('[role="alert"]')?.textContent).toContain('could not be saved');
    });
  });

  describe('editing an employee', () => {
    beforeEach(() => open('edit'));

    it('starts from the current profile and has no salary fields', () => {
      expect(input('firstName').value).toBe('Charlotte');
      expect(input('workEmail').value).toBe('charlotte.smith@acme.example');
      expect(input('salaryAmount')).toBeNull();
    });

    it('sends the changes with the version they were based on', async () => {
      type('jobTitle', 'Staff Engineer');

      await submit();

      const request = http.expectOne(`${API}/employees/e1`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toMatchObject({ jobTitle: 'Staff Engineer', version: 3 });
      request.flush({ ...EXISTING, jobTitle: 'Staff Engineer', version: 4 });
      await fixture.whenStable();
      expect(saved[0].jobTitle).toBe('Staff Engineer');
    });

    it('offers to reload the latest data when someone else changed the employee', async () => {
      type('jobTitle', 'Staff Engineer');
      await submit();

      http.expectOne(`${API}/employees/e1`).flush(
        { code: 'CONCURRENT_MODIFICATION' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      fixture.detectChanges();

      expect(view.querySelector('[role="alert"]')?.textContent).toContain('changed by someone else');
      expect(input('jobTitle').value).toBe('Staff Engineer');
      (view.querySelector('button.reload') as HTMLButtonElement).click();
      expect(reloads).toBe(1);
    });
  });
});
