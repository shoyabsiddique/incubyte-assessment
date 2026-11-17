import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  of,
  switchMap,
  tap,
} from 'rxjs';
import {
  EmployeePage,
  EmployeeQuery,
  EmployeeService,
  EmployeeSummary,
  FilterOptions,
} from './employee.service';

type FilterName = 'country' | 'department' | 'jobTitle' | 'employmentType' | 'status';

const FIRST_QUERY: EmployeeQuery = {
  page: 0,
  size: 20,
  sort: 'employeeId',
  direction: 'asc',
  q: '',
  country: '',
  department: '',
  jobTitle: '',
  employmentType: '',
  status: '',
};

@Component({
  selector: 'app-employees-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
  ],
  templateUrl: './employees.page.html',
  styleUrl: './employees.page.scss',
})
export class EmployeesPage {
  private readonly service = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queries = new BehaviorSubject<EmployeeQuery>(FIRST_QUERY);

  protected readonly columns = [
    'employeeId',
    'name',
    'jobTitle',
    'department',
    'country',
    'status',
    'salary',
    'salaryUsd',
  ];
  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly query = signal<EmployeeQuery>(FIRST_QUERY);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly rows = signal<EmployeeSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly filters = signal<FilterOptions | null>(null);

  constructor() {
    this.queries
      .pipe(
        tap((query) => {
          this.query.set(query);
          this.state.set('loading');
        }),
        // switchMap drops an older answer if a newer request has been made since
        switchMap((query) =>
          this.service.search(query).pipe(catchError(() => of<EmployeePage | null>(null))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        if (page === null) {
          this.state.set('error');
          return;
        }
        this.rows.set(page.items);
        this.total.set(page.totalElements);
        this.state.set('ready');
      });

    this.service
      .filters()
      .pipe(
        catchError(() => of<FilterOptions | null>(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((options) => this.filters.set(options));

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.change({ q, page: 0 }));
  }

  protected setFilter(name: FilterName, value: string): void {
    this.change({ [name]: value, page: 0 });
  }

  protected onSort(sort: Sort): void {
    if (!sort.direction) {
      this.change({ sort: FIRST_QUERY.sort, direction: 'asc', page: 0 });
      return;
    }
    this.change({ sort: sort.active, direction: sort.direction, page: 0 });
  }

  protected onPage(event: PageEvent): void {
    this.change({ page: event.pageIndex, size: event.pageSize });
  }

  protected retry(): void {
    this.queries.next({ ...this.query() });
  }

  private change(changes: Partial<EmployeeQuery>): void {
    this.queries.next({ ...this.query(), ...changes });
  }
}
