import { CdkDrag, CdkDropList, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { StatsFilters } from '../analytics/analytics.service';
import { EmployeeService, FilterOptions } from '../employees/employee.service';
import { DIMENSIONS, MEASURES, WidgetConfig } from './chart-data';
import { DEFAULT_WIDGETS, DashboardLayoutStore } from './dashboard-layout.store';
import { DraggedField, FIELD_MIME, WidgetComponent } from './widget.component';

/** A canvas of charts. Each chart picks its own fields; fields can be dragged onto it and charts dragged around. */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkDrag, CdkDropList, MatButtonModule, MatIconModule, WidgetComponent],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage {
  private readonly employees = inject(EmployeeService);
  private readonly layout = inject(DashboardLayoutStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dimensions = DIMENSIONS;
  protected readonly measures = MEASURES;
  protected readonly options = signal<FilterOptions | null>(null);
  protected readonly filters = signal<StatsFilters>({ country: '', department: '', jobTitle: '' });
  protected readonly widgets = signal<WidgetConfig[]>(this.layout.load() ?? freshDefaults());

  constructor() {
    this.employees
      .filters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (o) => this.options.set(o), error: () => undefined });
  }

  private set(widgets: WidgetConfig[]): void {
    this.widgets.set(widgets);
    this.layout.save(widgets);
  }

  protected setFilter(name: keyof StatsFilters, value: string): void {
    this.filters.update((current) => ({ ...current, [name]: value }));
  }

  protected add(): void {
    this.set([
      ...this.widgets(),
      {
        id: `chart-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
        chart: 'column',
        x: 'department',
        series: null,
        measure: 'headcount',
        aggregation: 'count',
        size: 'medium',
        sort: 'value',
        top: 0,
      },
    ]);
  }

  protected remove(id: string): void {
    this.set(this.widgets().filter((w) => w.id !== id));
  }

  protected update(config: WidgetConfig): void {
    this.set(this.widgets().map((w) => (w.id === config.id ? config : w)));
  }

  protected move(id: string, delta: number): void {
    const list = [...this.widgets()];
    const from = list.findIndex((w) => w.id === id);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= list.length) {
      return;
    }
    moveItemInArray(list, from, to);
    this.set(list);
  }

  protected reorder(event: Pick<CdkDragDrop<unknown>, 'previousIndex' | 'currentIndex'>): void {
    const list = [...this.widgets()];
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    this.set(list);
  }

  protected reset(): void {
    this.layout.clear();
    this.widgets.set(freshDefaults());
  }

  protected startDrag(event: DragEvent, kind: DraggedField['kind'], key: string): void {
    event.dataTransfer?.setData(FIELD_MIME, JSON.stringify({ kind, key } satisfies DraggedField));
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'copy';
    }
  }
}

/** The default charts, with new ids so a reset never collides with charts that were just removed. */
function freshDefaults(): WidgetConfig[] {
  return DEFAULT_WIDGETS.map((w) => ({ ...w }));
}
