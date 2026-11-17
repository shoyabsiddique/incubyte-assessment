import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-employees-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Employees</h2>
    <p>Search, filter and open employee records here.</p>
  `,
})
export class EmployeesPage {}
