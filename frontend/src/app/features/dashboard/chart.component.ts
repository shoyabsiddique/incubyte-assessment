import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  AGGREGATIONS,
  MEASURES,
  AggregationKey,
  ChartModel,
  ChartType,
  MeasureKey,
  formatValue,
  kpiValue,
  niceTicks,
} from './chart-data';

interface Mark {
  x: number;
  y: number;
  width: number;
  height: number;
  series: number;
  tip: string;
}

interface Tick {
  position: number;
  label: string;
}

interface Slice {
  path: string;
  series: number;
  label: string;
  share: number;
}

const SERIES_COLOURS = 8;
const WIDTH = 640;

/** Draws a pivot as a column, bar or line chart, a donut or a table, in plain SVG with no charting library. */
@Component({
  selector: 'app-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chart.component.html',
  styleUrl: './chart.component.scss',
})
export class ChartComponent {
  readonly model = input.required<ChartModel>();
  readonly type = input.required<ChartType>();
  readonly measure = input.required<MeasureKey>();
  readonly aggregation = input.required<AggregationKey>();

  protected readonly colour = (index: number) => `s${index % SERIES_COLOURS}`;
  protected readonly width = WIDTH;

  protected readonly empty = computed(() => this.model().categories.length === 0);
  protected readonly multiple = computed(() => this.model().series.length > 1);

  protected readonly summary = computed(() => {
    const m = this.model();
    return `${this.type()} chart of ${m.categories.length} categories and ${m.series.length} series`;
  });

  /** The type that decides which drawing is used: a pie is a donut without a hole, a stack is a column. */
  protected readonly view = computed<ChartType>(() => {
    const type = this.type();
    return type === 'pie' ? 'donut' : type === 'stackedColumn' ? 'column' : type;
  });

  private readonly stacked = computed(
    () => this.type() === 'stackedColumn' || this.type() === 'area',
  );

  /** The tallest a stack gets: the sum of every series in the fullest category. */
  private readonly scaleMax = computed(() => {
    const m = this.model();
    if (!this.stacked()) {
      return m.max;
    }
    return Math.max(
      0,
      ...m.categories.map((_, ci) => m.series.reduce((sum, s) => sum + s.values[ci], 0)),
    );
  });

  private readonly ticks = computed(() => niceTicks(this.scaleMax()));
  private readonly top = computed(() => this.ticks()[this.ticks().length - 1]);

  private format(value: number, compact: boolean): string {
    return formatValue(value, this.measure(), this.aggregation(), compact);
  }

  private tip(category: string, series: string, value: number): string {
    const label = this.multiple() ? `${category} · ${series}` : category;
    return `${label}: ${this.format(value, false)}`;
  }

  protected shorten(label: string): string {
    return label.length > 16 ? `${label.slice(0, 15)}…` : label;
  }

  // ---- vertical charts (column and line) ------------------------------------------------------
  private readonly left = 56;
  private readonly right = 12;
  private readonly topMargin = 12;
  protected readonly vHeight = 320;
  protected readonly bottom = computed(() => (this.model().categories.length > 6 ? 84 : 44));
  private readonly plotWidth = WIDTH - this.left - this.right;
  private readonly plotHeight = computed(() => this.vHeight - this.topMargin - this.bottom());

  protected readonly rotate = computed(() => this.model().categories.length > 6);

  private y(value: number): number {
    return this.topMargin + this.plotHeight() - (value / this.top()) * this.plotHeight();
  }

  protected readonly valueTicks = computed<Tick[]>(() =>
    this.ticks().map((value) => ({ position: this.y(value), label: this.format(value, true) })),
  );

  protected readonly categoryCentres = computed(() => {
    const count = this.model().categories.length;
    const step = this.plotWidth / count;
    return this.model().categories.map((c, index) => ({
      label: c.label,
      x: this.left + step * index + step / 2,
    }));
  });

  protected readonly columns = computed<Mark[]>(() => {
    const m = this.model();
    const step = this.plotWidth / m.categories.length;
    const inner = step * 0.72;
    if (this.type() === 'stackedColumn') {
      const below = m.categories.map(() => 0);
      return m.series.flatMap((s, si) =>
        s.values.map((value, ci) => {
          const from = below[ci];
          below[ci] += value;
          return {
            x: this.left + step * ci + (step - inner) / 2,
            y: this.y(below[ci]),
            width: inner,
            height: Math.max(0, this.y(from) - this.y(below[ci])),
            series: si,
            tip: this.tip(m.categories[ci].label, s.label, value),
          };
        }),
      );
    }
    const barWidth = inner / m.series.length;
    return m.series.flatMap((s, si) =>
      s.values.map((value, ci) => ({
        x: this.left + step * ci + (step - inner) / 2 + barWidth * si,
        y: this.y(value),
        width: Math.max(1, barWidth - 1),
        height: Math.max(0, this.topMargin + this.plotHeight() - this.y(value)),
        series: si,
        tip: this.tip(m.categories[ci].label, s.label, value),
      })),
    );
  });

  protected readonly lines = computed(() => {
    const m = this.model();
    const step = this.plotWidth / m.categories.length;
    return m.series.map((s, si) => {
      const points = s.values.map((value, ci) => ({
        x: this.left + step * ci + step / 2,
        y: this.y(value),
        tip: this.tip(m.categories[ci].label, s.label, value),
      }));
      return { series: si, points, path: points.map((p) => `${p.x},${p.y}`).join(' ') };
    });
  });

  protected readonly areas = computed(() => {
    const m = this.model();
    const step = this.plotWidth / m.categories.length;
    const below = m.categories.map(() => 0);
    return m.series.map((s, si) => {
      const lower = [...below];
      const upper = s.values.map((value, ci) => (below[ci] += value));
      const x = (ci: number) => this.left + step * ci + step / 2;
      const forward = upper.map((v, ci) => `${x(ci)},${this.y(v)}`);
      const back = lower.map((v, ci) => `${x(ci)},${this.y(v)}`).reverse();
      return { series: si, path: `M ${[...forward, ...back].join(' L ')} Z` };
    });
  });

  // ---- horizontal bars --------------------------------------------------------------------------
  protected readonly hLeft = 150;
  private readonly hRight = 24;
  private readonly rowHeight = 18;
  protected readonly hBottom = 28;

  protected readonly hHeight = computed(
    () =>
      this.topMargin +
      this.hBottom +
      this.model().categories.length * (this.model().series.length * this.rowHeight + 8),
  );

  private x(value: number): number {
    return this.hLeft + (value / this.top()) * (WIDTH - this.hLeft - this.hRight);
  }

  protected readonly hTicks = computed<Tick[]>(() =>
    this.ticks().map((value) => ({ position: this.x(value), label: this.format(value, true) })),
  );

  protected readonly rows = computed(() => {
    const m = this.model();
    const group = m.series.length * this.rowHeight + 8;
    return m.categories.map((c, ci) => ({
      label: c.label,
      y: this.topMargin + group * ci,
      middle: this.topMargin + group * ci + (m.series.length * this.rowHeight) / 2 + 4,
    }));
  });

  protected readonly hBars = computed<Mark[]>(() => {
    const m = this.model();
    const group = m.series.length * this.rowHeight + 8;
    return m.series.flatMap((s, si) =>
      s.values.map((value, ci) => ({
        x: this.hLeft,
        y: this.topMargin + group * ci + si * this.rowHeight,
        width: Math.max(0, this.x(value) - this.hLeft),
        height: this.rowHeight - 3,
        series: si,
        tip: this.tip(m.categories[ci].label, s.label, value),
      })),
    );
  });

  // ---- donut -------------------------------------------------------------------------------------
  protected readonly slices = computed<Slice[]>(() => {
    const m = this.model();
    const series = m.series[0];
    const total = series.values.reduce((sum, v) => sum + v, 0);
    if (total <= 0) {
      return [];
    }
    let angle = -Math.PI / 2;
    return series.values.map((value, index) => {
      const share = value / total;
      const sweep = share * Math.PI * 2;
      const hole = this.type() === 'pie' ? 0 : 62;
      const path = ring(110, 110, 100, hole, angle, angle + Math.min(sweep, Math.PI * 2 - 0.0001));
      angle += sweep;
      return { path, series: index, label: m.categories[index].label, share };
    });
  });

  protected percent(share: number): string {
    return `${Math.round(share * 100)}%`;
  }

  protected sliceTip(slice: Slice): string {
    const value = this.model().series[0].values[slice.series];
    return `${slice.label}: ${this.format(value, false)} (${this.percent(slice.share)})`;
  }

  // ---- heatmap -------------------------------------------------------------------------------
  protected readonly heatTop = 34;
  protected readonly heatLeft = 150;

  protected readonly heat = computed(() => {
    const m = this.model();
    const cell = Math.min(110, (WIDTH - this.heatLeft - 12) / m.series.length);
    const rowHeight = 26;
    const max = m.max || 1;
    const cells = m.categories.flatMap((c, ci) =>
      m.series.map((s, si) => {
        const value = s.values[ci];
        const opacity = Math.round((0.1 + 0.9 * (value / max)) * 100) / 100;
        return {
          x: this.heatLeft + cell * si,
          y: this.heatTop + rowHeight * ci,
          width: cell - 2,
          height: rowHeight - 2,
          opacity,
          label: this.format(value, true),
          dark: opacity > 0.55,
          tip: this.tip(c.label, s.label, value),
        };
      }),
    );
    return {
      cells,
      height: this.heatTop + rowHeight * m.categories.length + 8,
      columns: m.series.map((s, si) => ({
        label: s.label,
        x: this.heatLeft + cell * si + cell / 2,
      })),
      rows: m.categories.map((c, ci) => ({
        label: c.label,
        y: this.heatTop + rowHeight * ci + rowHeight / 2 + 4,
      })),
    };
  });

  // ---- single number --------------------------------------------------------------------------
  protected readonly kpi = computed(() => {
    const value = kpiValue(this.model(), this.aggregation());
    const measure = MEASURES.find((m) => m.key === this.measure())!.label;
    const aggregation = AGGREGATIONS.find((a) => a.key === this.aggregation())!.label;
    const categories = this.model().categories.length;
    return {
      text: value === null ? '—' : this.format(value, false),
      caption:
        this.aggregation() === 'median'
          ? 'A median can not be combined across groups; pick another aggregation for a single number.'
          : `${this.measure() === 'headcount' ? measure : `${aggregation} ${measure.toLowerCase()}`} across ${categories} categories`,
    };
  });

  // ---- table ---------------------------------------------------------------------------------------
  protected cell(seriesIndex: number, categoryIndex: number): string {
    return this.format(this.model().series[seriesIndex].values[categoryIndex], false);
  }
}

/** The outline of a ring segment between two angles. */
function ring(
  cx: number,
  cy: number,
  outer: number,
  inner: number,
  from: number,
  to: number,
): string {
  const large = to - from > Math.PI ? 1 : 0;
  const point = (r: number, a: number) => `${cx + r * Math.cos(a)} ${cy + r * Math.sin(a)}`;
  return (
    `M ${point(outer, from)} A ${outer} ${outer} 0 ${large} 1 ${point(outer, to)} ` +
    `L ${point(inner, to)} A ${inner} ${inner} 0 ${large} 0 ${point(inner, from)} Z`
  );
}
