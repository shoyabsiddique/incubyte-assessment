import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppConfigService } from '../../core/config/app-config.service';
import { AuditHistoryComponent } from './audit-history.component';

const API = 'http://api.test/api/v1';

const UPDATE = {
  id: 'a2',
  userId: 'u1',
  userEmail: 'priya@acme.example',
  entityType: 'EMPLOYEE',
  entityId: 'e1',
  subjectEmployeeId: 'e1',
  operation: 'UPDATE',
  before: { jobTitle: 'Engineer' },
  after: { jobTitle: 'Senior Engineer' },
  createdAt: '2026-03-10T10:00:00Z',
};

function page(items: object[], totalElements = items.length) {
  return { items, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

describe('AuditHistoryComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AuditHistoryComponent>;
  let view: HTMLElement;

  async function open(employeeId: string | null) {
    await TestBed.configureTestingModule({
      imports: [AuditHistoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: API } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AuditHistoryComponent);
    if (employeeId) {
      fixture.componentRef.setInput('employeeId', employeeId);
    }
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows an employee trail from the employee endpoint, without filters', async () => {
    await open('e1');

    http.expectOne((r) => r.url === `${API}/employees/e1/audit`).flush(page([UPDATE]));
    await settle();

    expect(view.querySelector('.filters')).toBeNull();
    expect(view.querySelectorAll('tr[mat-row]')).toHaveLength(1);
  });

  it('shows who, when, what and the changed fields', async () => {
    await open('e1');
    http.expectOne((r) => r.url === `${API}/employees/e1/audit`).flush(page([UPDATE]));
    await settle();

    const row = (view.querySelector('tr[mat-row]')?.textContent ?? '').replace(/\s+/g, ' ');
    expect(row).toContain('priya@acme.example');
    expect(row).toContain('Mar 10, 2026');
    expect(row).toContain('Update');
    expect(row).toContain('Employee');
    expect(row).toContain('Job title');
    expect(row).toContain('Engineer');
    expect(row).toContain('Senior Engineer');
  });

  it('shows a loading bar until the answer arrives', async () => {
    await open('e1');

    expect(view.querySelector('[role="progressbar"]')).not.toBeNull();

    http.expectOne((r) => r.url === `${API}/employees/e1/audit`).flush(page([UPDATE]));
    await settle();

    expect(view.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('says so when there is no history yet', async () => {
    await open('e1');
    http.expectOne((r) => r.url === `${API}/employees/e1/audit`).flush(page([]));
    await settle();

    expect(view.textContent).toContain('No changes have been recorded');
  });

  it('shows an error with a retry', async () => {
    await open('e1');
    http
      .expectOne((r) => r.url === `${API}/employees/e1/audit`)
      .flush({}, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(view.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');

    (view.querySelector('button.retry') as HTMLButtonElement).click();
    expect(http.expectOne((r) => r.url === `${API}/employees/e1/audit`)).toBeTruthy();
  });

  it('shows the organisation trail with filters and a subject link', async () => {
    await open(null);

    http.expectOne((r) => r.url === `${API}/audit`).flush(page([UPDATE]));
    await settle();

    expect(view.querySelector('.filters')).not.toBeNull();
    const link = view.querySelector('tr[mat-row] a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/employees/e1');
  });

  it('applies the chosen filters and returns to the first page', async () => {
    await open(null);
    http.expectOne((r) => r.url === `${API}/audit`).flush(page([UPDATE]));
    await settle();

    const from = view.querySelector('input.from') as HTMLInputElement;
    from.value = '2026-03-01';
    from.dispatchEvent(new Event('input'));
    from.dispatchEvent(new Event('change'));
    await settle();

    const request = http.expectOne((r) => r.url === `${API}/audit`);
    expect(request.request.params.get('from')).toBe('2026-03-01');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([]));
  });

  it('offers the people seen so far as a Changed by choice', async () => {
    await open(null);
    http.expectOne((r) => r.url === `${API}/audit`).flush(page([UPDATE]));
    await settle();

    const options = fixture.componentInstance.knownUsers();
    expect(options).toEqual([{ id: 'u1', email: 'priya@acme.example' }]);
  });
});
