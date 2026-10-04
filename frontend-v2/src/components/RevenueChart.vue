<script setup lang="ts">
import { computed } from "vue";
import { date, money } from "@/lib/format";
import type {
  CommercialFinancialPoint,
  CommercialMoneyDimension,
} from "@/api/contracts";
const props = defineProps<{
  points: CommercialFinancialPoint[];
  dimension: CommercialMoneyDimension;
  timezone?: string;
}>();
const maximum = computed(() =>
  Math.max(
    1,
    ...props.points.map((p) => Number(p.invoiced)),
    ...props.points.map((p) => Number(p.collected)),
  ),
);
const point = (i: number, value: string) =>
  `${20 + (i * 700) / Math.max(1, props.points.length - 1)},${180 - (Number(value) / maximum.value) * 150}`;
const collected = computed(() =>
  props.points.map((p, i) => point(i, p.collected)).join(" "),
);
const invoiced = computed(() =>
  props.points.map((p, i) => point(i, p.invoiced)).join(" "),
);
</script>
<template>
  <figure class="revenue-chart">
    <div class="chart-legend">
      <span><i class="collected-key" />Collected</span
      ><span><i class="invoiced-key" />Invoiced</span
      ><small
        >{{ dimension.currencyCode }} ·
        {{ dimension.billingCycle.toLowerCase() }}</small
      >
    </div>
    <svg
      viewBox="0 0 740 210"
      role="img"
      :aria-label="`Collected and invoiced amounts in ${dimension.currencyCode}`"
    >
      <defs>
        <linearGradient id="chart-fill" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stop-color="var(--accent)" stop-opacity=".11" />
          <stop offset="100%" stop-color="var(--accent)" stop-opacity="0" />
        </linearGradient>
      </defs>
      <line
        v-for="y in [30, 80, 130, 180]"
        :key="y"
        x1="20"
        x2="720"
        :y1="y"
        :y2="y"
        stroke="var(--border)"
        stroke-dasharray="3 5"
      />
      <polygon
        v-if="points.length"
        :points="`20,180 ${collected} 720,180`"
        fill="url(#chart-fill)"
      />
      <polyline
        :points="invoiced"
        fill="none"
        stroke="var(--stone-400)"
        stroke-width="2"
        stroke-dasharray="5 5"
      />
      <polyline
        :points="collected"
        fill="none"
        stroke="var(--accent)"
        stroke-width="2.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle
        v-if="points.length"
        :cx="720"
        :cy="180 - (Number(points.at(-1)!.collected) / maximum) * 150"
        r="4"
        fill="var(--accent)"
        stroke="var(--white)"
        stroke-width="2"
      />
    </svg>
    <div class="chart-labels">
      <span
        v-for="p in points.filter(
          (_, i) => i % Math.max(1, Math.floor(points.length / 5)) === 0,
        )"
        :key="p.bucketStart"
        >{{
          date(
            new Date(Date.parse(p.bucketEnd) - 1).toISOString(),
            false,
            timezone,
          )
        }}</span
      >
    </div>
    <details class="chart-data">
      <summary>View chart data</summary>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Collected</th>
              <th>Invoiced</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in points" :key="p.bucketStart">
              <td>
                {{
                  date(
                    new Date(Date.parse(p.bucketEnd) - 1).toISOString(),
                    false,
                    timezone,
                  )
                }}{{ p.provisional ? " · provisional" : "" }}
              </td>
              <td>{{ money(p.collected, dimension.currencyCode) }}</td>
              <td>{{ money(p.invoiced, dimension.currencyCode) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </figure>
</template>
<style scoped>
.revenue-chart {
  margin: 0;
}
.chart-legend {
  display: flex;
  gap: 18px;
  align-items: center;
  padding-bottom: 16px;
  font-size: 10px;
  color: var(--muted);
}
.chart-legend span {
  display: flex;
  gap: 6px;
  align-items: center;
}
.chart-legend small {
  margin-left: auto;
  font-size: 9px;
}
.chart-legend i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
}
.collected-key {
  background: var(--accent);
}
.invoiced-key {
  background: var(--stone-200);
}
svg {
  display: block;
  width: 100%;
  height: 200px;
}
.chart-labels {
  display: flex;
  justify-content: space-between;
  color: var(--subtle);
  font-size: 9px;
  padding: 0 10px;
}
.chart-data {
  margin-top: 14px;
  font-size: 9px;
  color: var(--muted);
}
.chart-data summary {
  cursor: pointer;
}
@media (max-width: 700px) {
  svg {
    height: 150px;
  }
  .chart-legend {
    gap: 12px;
  }
}
</style>
