import { CdkDragHandle } from '@angular/cdk/drag-drop';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { AnalyticsService, PivotResponse, StatsFilters } from '../analytics/analytics.service';
import { ChartComponent } from './chart.component';
import {
  AGGREGATIONS,
  AggregationKey,
  CHART_TYPES,
  ChartType,
  DIMENSIONS,
  DimensionKey,
  MEASURES,
  MeasureKey,
  WidgetConfig,
  WidgetSize,
  autoTitle,
  normalize,
  prepare,
  validAggregations,
} from './chart-data';

export const FIELD_MIME = 'application/x-dashboard-field';

export interface DraggedField {
  kind: 'dimension' | 'measure';
  key: string;
}

type Well = 'x' | 'series' | 'value';
type LoadState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: PivotResponse };

/** One chart on the dashboard: its data, its settings and the wells fields can be dropped on. */
@Component({
  selector: 'app-dashboard-widget',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkDragHandle, ChartComponent, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './widget.component.html',
  styleUrl: './widget.component.scss',
})
export class WidgetComponent {
  private readonly analytics = inject(AnalyticsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();

  readonly config = input.required<WidgetConfig>();
  readonly filters = input.required<StatsFilters>();
  readonly first = input(false);
  readonly last = input(false);
  readonly configChange = output<WidgetConfig>();
  readonly removed = output<void>();
  readonly moved = output<number>();

  protected readonly dimensions = DIMENSIONS;
  protected readonly measures = MEASURES;
  protected readonly chartTypes = CHART_TYPES;
  protected readonly tops = [0, 5, 10, 20];
  protected readonly editing = signal(false);
  protected readonly hover = signal<Well | null>(null);
  protected readonly title = computed(() => autoTitle(this.config()));
  protected readonly aggregations = computed(() =>
    AGGREGATIONS.filter((a) => validAggregations(this.config().measure).includes(a.key)),
  );

  /** What the server is asked: cosmetic settings (chart type, sort, size) never trigger a new request. */
  private readonly query = computed(
    () => {
      const c = this.config();
      return {
        x: c.x,
        series: c.series,
        measure: c.measure,
        aggregation: c.aggregation,
        filters: this.filters(),
      };
    },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) },
  );

  protected readonly state = signal<LoadState>({ status: 'loading' });
  protected readonly model = computed(() => {
    const state = this.state();
    return state.status === 'ready' ? prepare(state.data, this.config()) : null;
  });

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap((query) =>
          this.retries.pipe(
            startWith(undefined),
            switchMap(() => {
              this.state.set({ status: 'loading' });
              return this.analytics.pivot(query, query.filters).pipe(
                map((data): LoadState => ({ status: 'ready', data })),
                catchError(() => of<LoadState>({ status: 'error' })),
              );
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected retry(): void {
    this.retries.next();
  }

  private change(patch: Partial<WidgetConfig>): void {
    this.configChange.emit(normalize({ ...this.config(), ...patch }));
  }

  protected setX(key: string): void {
    this.change({ x: key as DimensionKey });
  }

  protected setSeries(key: string): void {
    this.change({ series: key === '' ? null : (key as DimensionKey) });
  }

  protected setMeasure(key: string): void {
    const measure = key as MeasureKey;
    const wasHeadcount = this.config().measure === 'headcount';
    // Leaving headcount, "count" is rarely what is meant: an average is the useful default.
    const aggregation: AggregationKey =
      wasHeadcount && measure !== 'headcount' ? 'average' : this.config().aggregation;
    this.change({ measure, aggregation });
  }

  protected setAggregation(key: string): void {
    this.change({ aggregation: key as AggregationKey });
  }

  protected setChart(type: ChartType): void {
    this.change({ chart: type });
  }

  protected setSort(sort: string): void {
    this.change({ sort: sort === 'value' ? 'value' : 'label' });
  }

  protected setTop(top: string): void {
    this.change({ top: Number(top) });
  }

  protected setSize(size: string): void {
    this.change({ size: size as WidgetSize });
  }

  // ---- drag and drop of fields onto the wells ----------------------------------------------------
  protected allow(event: DragEvent, well: Well): void {
    if (event.dataTransfer?.types?.includes(FIELD_MIME)) {
      event.preventDefault();
      this.hover.set(well);
    }
  }

  protected leave(): void {
    this.hover.set(null);
  }

  protected drop(event: DragEvent, well: Well): void {
    event.preventDefault();
    this.hover.set(null);
    let field: DraggedField;
    try {
      field = JSON.parse(event.dataTransfer?.getData(FIELD_MIME) ?? '') as DraggedField;
    } catch {
      return;
    }
    if (well === 'value') {
      if (field.kind === 'measure' && MEASURES.some((m) => m.key === field.key)) {
        this.setMeasure(field.key);
      }
      return;
    }
    if (field.kind !== 'dimension' || !DIMENSIONS.some((d) => d.key === field.key)) {
      return;
    }
    if (well === 'x') {
      this.setX(field.key);
    } else {
      this.setSeries(field.key);
    }
  }
}
