<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import ViewTabs from "@/components/ViewTabs.vue";
import AnalyticsReport from "@/components/AnalyticsReport.vue";
import PageHeading from "@/components/PageHeading.vue";
import ResourceState from "@/components/ResourceState.vue";
import RevenueChart from "@/components/RevenueChart.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import { gateway, read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import { useResource } from "@/composables/useResource";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { money, number, date, label } from "@/lib/format";
import { destination } from "@/lib/destination";
import { paymentEnabled } from "@/lib/capabilities";
import { withReturnTo } from "@/lib/navigation";
const route = useRoute(),
  router = useRouter(),
  days = computed(() => (Number(route.query.days) === 7 ? 7 : 30));
const reportAccess = computed(
  () =>
    (paymentEnabled && can(p.analyticsReadFinancialSeries)) ||
    can(p.analyticsReadSubscriptionSeries) ||
    can(p.analyticsReadOfferSeries),
);
const view = computed(() =>
  reportAccess.value &&
  (route.query.view === "reports" ||
    !can(
      p.plansOverview,
      p.analyticsReadSummary,
      p.analyticsReadOperations,
      p.activitiesRead,
    ))
    ? "reports"
    : "overview",
);
const overview = useResource(() =>
    can(p.plansOverview) ? gateway.overview() : Promise.resolve(undefined),
  ),
  analytics = useResource(
    () =>
      can(p.analyticsReadSummary)
        ? gateway.analytics(days.value)
        : Promise.resolve(undefined),
    [days],
  ),
  series = useResource(
    () =>
      paymentEnabled && can(p.analyticsReadFinancialSeries)
        ? gateway.series(days.value)
        : Promise.resolve(undefined),
    [days],
  ),
  activities = useResource(() =>
    can(p.activitiesRead)
      ? read(p.activitiesRead, () =>
          adminApi.activities({ actorSurface: "PLATFORM_ADMIN", size: 6 }),
        )
      : Promise.resolve(undefined),
  );
const attention = useResource(() =>
  can(p.analyticsReadOperations)
    ? read(p.analyticsReadOperations, () =>
        adminApi.commercialAttention({ size: 10 }),
      )
    : Promise.resolve(undefined),
);
const o = overview.data,
  a = analytics.data,
  s = series.data,
  t = attention.data,
  events = activities.data,
  dimension = ref(0);
const dimensions = computed(() => {
  const values = [
    ...(s.value?.dimensions.map((x) => x.dimension) || []),
    ...(a.value?.configuredRecurringValues.map((x) => x.dimension) || []),
    ...(paymentEnabled
      ? a.value?.financialTotals.map((x) => x.dimension) || []
      : []),
  ];
  return values.filter(
    (x, i) =>
      values.findIndex(
        (v) =>
          v.currencyCode === x.currencyCode &&
          v.billingCycle === x.billingCycle,
      ) === i,
  );
});
const selectedDimension = computed(
  () => dimensions.value[dimension.value] || dimensions.value[0],
);
const matches = (d: { currencyCode: string; billingCycle: string }) =>
  d.currencyCode === selectedDimension.value?.currencyCode &&
  d.billingCycle === selectedDimension.value?.billingCycle;
const currentSeries = computed(() =>
  s.value?.dimensions.find((x) => matches(x.dimension)),
);
const recurring = computed(() =>
  a.value?.configuredRecurringValues.find((x) => matches(x.dimension)),
);
const totals = computed(() =>
  a.value?.financialTotals.find((x) => matches(x.dimension)),
);
</script>
<template>
  <PageHeading title="Platform Overview"
    ><RouterLink
      v-if="can(p.subscriptionsPreviewChangeJob)"
      class="button primary"
      to="/changes/new"
      >Change subscriptions</RouterLink
    ></PageHeading
  >
  <ViewTabs
    v-if="reportAccess"
    :tabs="[
      { key: 'overview', label: 'Overview' },
      { key: 'reports', label: 'Reports' },
    ]"
    :current="view"
  />
  <AnalyticsReport v-if="view === 'reports'" />
  <template v-else>
    <div
      v-if="can(p.plansOverview, p.analyticsReadSummary)"
      class="report-summary"
    >
      <div>
        <span>Active subscriptions</span
        ><strong>{{
          o || a
            ? number(
                o?.activeSubscriptions ?? a?.currentSubscriptions.ACTIVE ?? 0,
              )
            : "—"
        }}</strong>
      </div>
      <div v-if="can(p.analyticsReadSummary)">
        <span>Recurring value</span
        ><strong>{{
          recurring
            ? money(recurring.amount, recurring.dimension.currencyCode)
            : "—"
        }}</strong
        ><small v-if="recurring">{{
          label(recurring.dimension.billingCycle)
        }}</small
        ><select
          v-if="dimensions.length > 1"
          v-model="dimension"
          aria-label="Recurring value currency and billing cycle"
        >
          <option v-for="(d, i) in dimensions" :key="i" :value="i">
            {{ d.currencyCode }} · {{ label(d.billingCycle) }}
          </option>
        </select>
      </div>
      <div v-if="paymentEnabled">
        <span>Collected</span
        ><strong>{{
          totals ? money(totals.collected, totals.dimension.currencyCode) : "—"
        }}</strong>
      </div>
      <div>
        <span>{{
          can(p.analyticsReadOperations) ? "Work queue" : "Changes to review"
        }}</span
        ><RouterLink
          v-if="can(p.analyticsReadOperations)"
          to="/operations?view=queue"
          ><strong>{{ t?.totalElements ?? "—" }}</strong></RouterLink
        ><strong v-else>{{ o?.changesNeedingAttention ?? "—" }}</strong>
      </div>
    </div>
    <p
      v-if="overview.error.value || analytics.error.value"
      class="notice error"
      role="alert"
    >
      {{ overview.error.value || analytics.error.value }}
      <button
        class="text-link"
        @click="
          overview.refresh();
          analytics.refresh();
        "
      >
        Retry
      </button>
    </p>
    <section
      v-if="paymentEnabled && can(p.analyticsReadFinancialSeries)"
      class="report-section"
    >
      <header class="section-heading">
        <h2>Revenue</h2>
        <div class="row">
          <select
            v-if="dimensions.length > 1"
            v-model="dimension"
            aria-label="Currency and billing cycle"
          >
            <option v-for="(d, i) in dimensions" :value="i">
              {{ d.currencyCode }} ·
              {{ label(d.billingCycle) }}
            </option></select
          ><select
            :value="days"
            aria-label="Reporting period"
            @change="
              router.replace({
                query: {
                  ...route.query,
                  days: ($event.target as HTMLSelectElement).value,
                },
              })
            "
          >
            <option value="30">Last 30 days</option>
            <option value="7">Last 7 days</option>
          </select>
        </div>
      </header>
      <ResourceState
        :loading="series.loading.value"
        :error="series.error.value"
        :empty="!s?.dimensions.length"
        title="No revenue recorded"
        @retry="series.refresh()"
        ><RevenueChart
          v-if="currentSeries"
          :points="currentSeries.points"
          :dimension="currentSeries.dimension"
          :timezone="s?.metadata.timezone"
      /></ResourceState>
    </section>
    <section v-if="can(p.analyticsReadOperations)" class="report-section">
      <header class="section-heading">
        <h2>Work queue</h2>
        <RouterLink class="text-link" to="/operations?view=queue"
          >View all</RouterLink
        >
      </header>
      <ResourceState
        :loading="attention.loading.value"
        :error="attention.error.value"
        :empty="!t?.content.length"
        title="No outstanding items"
        @retry="attention.refresh()"
        ><div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Issue</th>
                <th>Status</th>
                <th>Due</th>
                <th>Amount</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in t?.content" :key="row.recordId">
                <td>
                  <RouterLink
                    class="text-link"
                    :to="
                      withReturnTo(
                        '/customers/' + row.accountId,
                        route.fullPath,
                      )
                    "
                    >{{ row.accountName }}</RouterLink
                  >
                </td>
                <td>{{ label(row.type) }}</td>
                <td><StatusBadge :status="row.status" /></td>
                <td>{{ date(row.dueAt) }}</td>
                <td>
                  {{
                    row.amount
                      ? money(row.amount, row.currencyCode || "MAD")
                      : "—"
                  }}
                </td>
                <td>
                  <RouterLink
                    class="text-link"
                    :to="
                      withReturnTo(destination(row.destination), route.fullPath)
                    "
                    >Review</RouterLink
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div></ResourceState
      >
    </section>
    <section v-if="can(p.activitiesRead)" class="report-section">
      <header class="section-heading">
        <h2>Recent activity</h2>
        <RouterLink class="text-link" to="/operations?view=activity"
          >View all</RouterLink
        >
      </header>
      <ResourceState
        :loading="activities.loading.value"
        :error="activities.error.value"
        :empty="!events?.content.length"
        title="No activity recorded"
        @retry="activities.refresh()"
        ><div
          v-for="event in events?.content.slice(0, 6)"
          :key="event.id"
          class="activity-record"
        >
          <span>{{ label(event.action.replaceAll(".", " ")) }}</span
          ><RouterLink
            v-if="event.targetAccountId"
            class="text-link"
            :to="
              withReturnTo(
                '/customers/' + event.targetAccountId,
                route.fullPath,
              )
            "
            >{{ "Customer" }}</RouterLink
          ><time>{{ date(event.occurredAt, true) }}</time>
        </div></ResourceState
      >
    </section>
  </template>
</template>
