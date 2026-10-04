<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import ResourceState from "./ResourceState.vue";
import RevenueChart from "./RevenueChart.vue";
import Facts from "./Facts.vue";
import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import { date, label, money, number } from "@/lib/format";
import type { RecordData } from "@/resources/types";
import { paymentEnabled } from "@/lib/capabilities";
const route = useRoute(),
  router = useRouter();
const reports = computed(() =>
  [
    {
      key: "financial",
      label: "Revenue",
      permission: p.analyticsReadFinancialSeries,
    },
    {
      key: "subscriptions",
      label: "Subscription activity",
      permission: p.analyticsReadSubscriptionSeries,
    },
    {
      key: "offers",
      label: "Offer outcomes",
      permission: p.analyticsReadOfferSeries,
    },
    {
      key: "holdings",
      label: "Current product holdings",
      permission: p.analyticsReadSubscriptionSeries,
    },
  ].filter(
    (x) => (x.key !== "financial" || paymentEnabled) && can(x.permission),
  ),
);
const kind = computed(
  () =>
    reports.value.find((x) => x.key === route.query.report)?.key ||
    reports.value[0]?.key,
);
const days = computed(() => (Number(route.query.days) === 7 ? 7 : 30));
const dimension = ref(0);
function query() {
  return {
    from: new Date(Date.now() - days.value * 86400000).toISOString(),
    until: new Date().toISOString(),
    timezone: "Africa/Casablanca",
    interval: "DAY" as const,
  };
}
const report = useResource<RecordData | undefined>(async () => {
  switch (kind.value) {
    case "financial":
      return read(p.analyticsReadFinancialSeries, () =>
        adminApi.commercialFinancialSeries(query()),
      );
    case "subscriptions":
      return read(p.analyticsReadSubscriptionSeries, () =>
        adminApi.commercialSubscriptionSeries(query()),
      );
    case "offers":
      return read(p.analyticsReadOfferSeries, () =>
        adminApi.commercialOfferSeries(query()),
      );
    case "holdings":
      return {
        holdings: await read(
          p.analyticsReadSubscriptionSeries,
          adminApi.commercialProductHoldings,
        ),
      };
    default:
      return undefined;
  }
}, [kind, days]);
const data = report.data;
const current = computed(
  () =>
    data.value?.dimensions?.[dimension.value] || data.value?.dimensions?.[0],
);
const empty = computed(
  () =>
    !data.value ||
    (kind.value === "financial"
      ? !data.value.dimensions.length
      : kind.value === "holdings"
        ? !data.value.holdings.length
        : !data.value.points.length),
);
function filter(key: string, value: string) {
  void router.replace({ query: { ...route.query, [key]: value } });
}
</script>
<template>
  <div class="resource-toolbar">
    <select
      :value="kind"
      aria-label="Report"
      @change="filter('report', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="item in reports" :key="item.key" :value="item.key">
        {{ item.label }}
      </option>
    </select>
    <select
      v-if="kind !== 'holdings'"
      :value="days"
      aria-label="Reporting period"
      @change="filter('days', ($event.target as HTMLSelectElement).value)"
    >
      <option value="30">Last 30 days</option>
      <option value="7">Last 7 days</option>
    </select>
    <select
      v-if="kind === 'financial' && data?.dimensions.length > 1"
      v-model="dimension"
      aria-label="Currency and billing cycle"
    >
      <option
        v-for="(item, index) in data?.dimensions"
        :key="index"
        :value="index"
      >
        {{ item.dimension.currencyCode }} ·
        {{ label(item.dimension.billingCycle) }}
      </option>
    </select>
  </div>
  <ResourceState
    :loading="report.loading.value"
    :error="report.error.value"
    :empty="empty"
    title="No records for this report"
    @retry="report.refresh()"
  >
    <template v-if="kind === 'financial' && current">
      <RevenueChart
        :points="current.points"
        :dimension="current.dimension"
        :timezone="data?.metadata.timezone"
      />
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Invoiced</th>
              <th>Collected</th>
              <th>Credited</th>
              <th>Refunded</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="point in current.points" :key="point.bucketStart">
              <td>
                {{
                  date(
                    new Date(Date.parse(point.bucketEnd) - 1).toISOString(),
                    false,
                    data?.metadata.timezone,
                  )
                }}<small v-if="point.provisional" class="muted">
                  · Provisional</small
                >
              </td>
              <td
                v-for="key in ['invoiced', 'collected', 'credited', 'refunded']"
                :key="key"
              >
                {{ money(point[key], current.dimension.currencyCode) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <template v-else-if="kind === 'subscriptions'">
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Products added</th>
              <th>Products removed</th>
              <th>Lifecycle actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="point in data?.points" :key="point.bucketStart">
              <td>
                {{
                  date(
                    new Date(Date.parse(point.bucketEnd) - 1).toISOString(),
                    false,
                    data?.metadata.timezone,
                  )
                }}<small v-if="point.provisional" class="muted">
                  · Provisional</small
                >
              </td>
              <td>{{ number(point.productsAdded) }}</td>
              <td>{{ number(point.productsRemoved) }}</td>
              <td>
                <details v-if="Object.keys(point.lifecycleActions).length">
                  <summary class="text-link">View actions</summary>
                  <Facts :data="point.lifecycleActions" />
                </details>
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <section class="report-section">
        <h2>Product movements</h2>
        <Facts :data="data?.productMovements" />
      </section>
    </template>
    <div v-else-if="kind === 'offers'" class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Reserved</th>
            <th>Applied</th>
            <th>Cancelled</th>
            <th>Failed</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="point in data?.points" :key="point.bucketStart">
            <td>
              {{
                date(
                  new Date(Date.parse(point.bucketEnd) - 1).toISOString(),
                  false,
                  data?.metadata.timezone,
                )
              }}<small v-if="point.provisional" class="muted">
                · Provisional</small
              >
            </td>
            <td
              v-for="key in ['reserved', 'applied', 'cancelled', 'failed']"
              :key="key"
            >
              {{ number(point[key]) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="kind === 'holdings'" class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Type</th>
            <th>Subscriptions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in data?.holdings"
            :key="item.productType + item.productCode"
          >
            <td>{{ item.productCode }}</td>
            <td>{{ label(item.productType) }}</td>
            <td>{{ number(item.subscriptions) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="data?.metadata" class="muted small section-gap">
      Complete through
      {{ date(data.metadata.completeThrough, true, "UTC") }} · UTC · Reporting
      timezone {{ data.metadata.timezone }}
    </p>
  </ResourceState>
</template>
