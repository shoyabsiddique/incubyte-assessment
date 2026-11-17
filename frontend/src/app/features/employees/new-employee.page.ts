import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { EmployeeFormComponent } from './employee-form.component';
import { EmployeeSummary } from './employee.service';

@Component({
  selector: 'app-new-employee-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule, EmployeeFormComponent],
  template: `
    <a class="back" mat-button routerLink="/employees">Back to employees</a>
    <h2>Add employee</h2>
    <app-employee-form mode="create" (saved)="open($event)" (cancelled)="cancel()" />
  `,
  styles: `
    :host {
      display: block;
      padding: 16px 24px;
    }
  `,
})
export class NewEmployeePage {
  private readonly router = inject(Router);

  protected open(employee: EmployeeSummary): void {
    void this.router.navigate(['/employees', employee.id]);
  }

  protected cancel(): void {
    void this.router.navigate(['/employees']);
  }
}
