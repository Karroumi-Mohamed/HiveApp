import { useId } from "react";
import type { ExactDecimal } from "@/api/contracts";
import { cn } from "@/lib/utils";

export type AnalyticsChartSeries = {
  key: string;
  label: string;
  values: Array<ExactDecimal | number>;
  className: string;
};

const WIDTH = 720;
const HEIGHT = 250;
const LEFT = 38;
const TOP = 18;
const PLOT_WIDTH = 662;
const PLOT_HEIGHT = 190;

function scaled(value: ExactDecimal | number): bigint {
  if (typeof value === "number") return BigInt(Math.max(0, Math.trunc(value))) * 10_000n;
  const [integer = "0", fraction = ""] = value.split(".");
  return BigInt(integer) * 10_000n + BigInt(fraction.padEnd(4, "0"));
}

function points(values: Array<ExactDecimal | number>, maximum: bigint) {
  if (values.length === 0) return "";
  return values
    .map((value, index) => {
      const x = LEFT + (values.length === 1 ? PLOT_WIDTH / 2 : (index * PLOT_WIDTH) / (values.length - 1));
      const y = TOP + PLOT_HEIGHT - Number((scaled(value) * BigInt(PLOT_HEIGHT)) / maximum);
      return `${index ? "L" : "M"}${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

export function AnalyticsSeriesChart({
  title,
  labels,
  series,
  provisionalIndex,
}: {
  title: string;
  labels: string[];
  series: AnalyticsChartSeries[];
  provisionalIndex?: number;
}) {
  const titleId = useId();
  const values = series.flatMap((item) => item.values).map(scaled);
  const maximum = values.reduce((max, value) => (value > max ? value : max), 1n);
  const provisionalX =
    provisionalIndex === undefined || provisionalIndex < 0 || provisionalIndex >= labels.length || labels.length < 2
      ? null
      : LEFT + (provisionalIndex * PLOT_WIDTH) / (labels.length - 1) - PLOT_WIDTH / Math.max(labels.length - 1, 1) / 2;

  return (
    <figure aria-labelledby={titleId} className="min-w-0">
      <figcaption className="flex flex-wrap items-center justify-between gap-3" id={titleId}>
        <span className="text-sm font-semibold">{title}</span>
        <span className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
          {series.map((item) => (
            <span className="inline-flex items-center gap-1.5" key={item.key}>
              <span aria-hidden="true" className={cn("h-0.5 w-5", item.className.replace("text-", "bg-"))} />
              {item.label}
            </span>
          ))}
        </span>
      </figcaption>
      <div className="mt-4 overflow-x-auto" dir="ltr">
        <svg
          aria-labelledby={titleId}
          className="h-auto min-w-[38rem] w-full"
          role="img"
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        >
          <title>{title}</title>
          <g className="stroke-border" strokeWidth="1">
            {[0, 1, 2, 3, 4].map((line) => {
              const y = TOP + (line * PLOT_HEIGHT) / 4;
              return <line key={line} x1={LEFT} x2={LEFT + PLOT_WIDTH} y1={y} y2={y} />;
            })}
          </g>
          {provisionalX !== null ? (
            <g>
              <rect
                className="fill-warning/8"
                height={PLOT_HEIGHT}
                width={Math.max(0, LEFT + PLOT_WIDTH - provisionalX)}
                x={provisionalX}
                y={TOP}
              />
              <line
                className="stroke-warning/60"
                strokeDasharray="4 4"
                x1={provisionalX}
                x2={provisionalX}
                y1={TOP}
                y2={TOP + PLOT_HEIGHT}
              />
            </g>
          ) : null}
          {series.map((item) => (
            <path
              className={cn("fill-none stroke-current", item.className)}
              d={points(item.values, maximum)}
              key={item.key}
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2.5"
              vectorEffect="non-scaling-stroke"
            />
          ))}
          {labels.length ? (
            <g className="fill-muted-foreground text-[11px]">
              <text textAnchor="start" x={LEFT} y={HEIGHT - 12}>
                {labels[0]}
              </text>
              <text textAnchor="end" x={LEFT + PLOT_WIDTH} y={HEIGHT - 12}>
                {labels.at(-1)}
              </text>
            </g>
          ) : null}
        </svg>
      </div>
    </figure>
  );
}
