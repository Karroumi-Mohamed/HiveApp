<script setup lang="ts">
import { computed, reactive, ref, watch, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import OwnerLookup from "@/components/OwnerLookup.vue";
import PageHeading from "@/components/PageHeading.vue";
import CustomerWorkspaceTabs from "@/components/CustomerWorkspaceTabs.vue";
import { withReturnTo } from "@/lib/navigation";
import ResourceState from "@/components/ResourceState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import Pagination from "@/components/Pagination.vue";
import Icon from "@/components/Icon.vue";
import { useResource } from "@/composables/useResource";
import { gateway } from "@/data/gateway";
import { initials, money, date } from "@/lib/format";
import type { SubscriptionStatus } from "@/api/contracts";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const route = useRoute();
const router = useRouter();
const query = ref(String(route.query.q || ""));
const status = computed(() => String(route.query.status || ""));
const currentPage = computed(() => Math.max(0, Number(route.query.page) || 0));
const sorting = computed(() => String(route.query.sort || "name"));
const accountActive = computed(() =>
  ["true", "false"].includes(String(route.query.accountActive))
    ? String(route.query.accountActive)
    : "",
);
const hasSubscription = computed(() =>
  ["true", "false"].includes(String(route.query.hasSubscription))
    ? String(route.query.hasSubscription)
    : "",
);
const planCode = computed(() => String(route.query.planCode || "").trim());
const activeFilterCount = computed(
  () =>
    Number(!!status.value) +
    Number(!!accountActive.value) +
    Number(!!hasSubscription.value) +
    Number(!!planCode.value),
);
const filtersOpen = ref(activeFilterCount.value > 0);
const filters = reactive({
  accountActive: accountActive.value,
  hasSubscription: hasSubscription.value,
  planCode: planCode.value,
});
const filtersDirty = computed(
  () =>
    filters.accountActive !== accountActive.value ||
    filters.hasSubscription !== hasSubscription.value ||
    filters.planCode.trim() !== planCode.value,
);
const canBulkReview = computed(() => can(p.subscriptionsPreviewChangeJob));
const selected = ref<string[]>([]);
const resource = useResource(
  () =>
    gateway.accounts({
      query: String(route.query.q || ""),
      subscriptionStatus: (status.value || undefined) as
        SubscriptionStatus | undefined,
      accountActive: accountActive.value
        ? accountActive.value === "true"
        : undefined,
      hasSubscription: hasSubscription.value
        ? hasSubscription.value === "true"
        : undefined,
      planCode: planCode.value || undefined,
      page: currentPage.value,
      size: 10,
      sort: sorting.value === "createdAt" ? "createdAt" : "name",
      direction: sorting.value === "createdAt" ? "desc" : "asc",
    }),
  [() => route.query],
);
const data = resource.data;
let debounce: ReturnType<typeof setTimeout>;
onUnmounted(() => clearTimeout(debounce));
watch(query, (value) => {
  clearTimeout(debounce);
  if (value === String(route.query.q || "")) return;
  debounce = setTimeout(
    () => update({ q: value || undefined, page: undefined }),
    250,
  );
});
watch(
  () => route.query.q,
  (value) => {
    query.value = String(value || "");
  },
);
watch([accountActive, hasSubscription, planCode], () => {
  Object.assign(filters, {
    accountActive: accountActive.value,
    hasSubscription: hasSubscription.value,
    planCode: planCode.value,
  });
});
watch(
  [
    () => route.query.q,
    status,
    accountActive,
    hasSubscription,
    planCode,
    sorting,
  ],
  () => (selected.value = []),
);
watch(canBulkReview, (allowed) => {
  if (!allowed) selected.value = [];
});
function update(values: Record<string, string | undefined>) {
  const next = { ...route.query, ...values };
  if (next.hasSubscription === "false" && (values.status || values.planCode))
    next.hasSubscription = undefined;
  void router.replace({ query: next });
}
function applyFilters() {
  const noSubscription = filters.hasSubscription === "false";
  update({
    accountActive: filters.accountActive || undefined,
    hasSubscription: filters.hasSubscription || undefined,
    planCode: noSubscription ? undefined : filters.planCode.trim() || undefined,
    status: noSubscription ? undefined : status.value || undefined,
    page: undefined,
  });
}
function clearFilters() {
  Object.assign(filters, {
    accountActive: "",
    hasSubscription: "",
    planCode: "",
  });
  update({
    status: undefined,
    accountActive: undefined,
    hasSubscription: undefined,
    planCode: undefined,
    page: undefined,
  });
}
function toggle(id: string) {
  if (!canBulkReview.value) return;
  selected.value = selected.value.includes(id)
    ? selected.value.filter((x) => x !== id)
    : [...selected.value, id];
}
const allSelected = computed(
  () =>
    !!data.value?.content.length &&
    data.value.content.every((c) => selected.value.includes(c.id)),
);
function togglePage() {
  if (!canBulkReview.value) return;
  if (allSelected.value)
    selected.value = selected.value.filter(
      (id) => !data.value?.content.some((c) => c.id === id),
    );
  else
    selected.value = [
      ...new Set([
        ...selected.value,
        ...(data.value?.content.map((c) => c.id) || []),
      ]),
    ];
}
</script>
<template>
  <PageHeading title="Customers"
    ><OwnerLookup /><RouterLink
      v-if="canBulkReview"
      class="button primary"
      :to="withReturnTo('/changes/new?population=true', route.fullPath)"
      ><Icon name="plus" :size="15" />Change subscriptions</RouterLink
    ></PageHeading
  >
  <CustomerWorkspaceTabs />
  <div class="toolbar">
    <label class="search-field"
      ><Icon name="search" :size="16" /><input
        v-model="query"
        placeholder="Search customers…"
        aria-label="Search customers"
    /></label>
    <div class="toolbar-group">
      <label
        ><span class="sr-only">Subscription status</span
        ><select
          :value="status"
          @change="
            update({
              status: ($event.target as HTMLSelectElement).value || undefined,
              page: undefined,
            })
          "
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="TRIALING">In trial</option>
          <option value="PAST_DUE">Past due</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="EXPIRED">Expired</option>
        </select></label
      ><label
        ><span class="sr-only">Sort customers</span
        ><select
          :value="sorting"
          @change="
            update({
              sort: ($event.target as HTMLSelectElement).value,
              page: undefined,
            })
          "
        >
          <option value="name">Name A–Z</option>
          <option value="createdAt">Newest first</option>
        </select></label
      ><button
        class="button"
        :aria-expanded="filtersOpen"
        aria-controls="customer-filters"
        @click="filtersOpen = !filtersOpen"
      >
        <Icon name="filters" :size="15" />Filters<span
          v-if="activeFilterCount"
          class="pill"
          >{{ activeFilterCount }}</span
        ></button
      ><button
        class="icon-button"
        aria-label="Refresh customers"
        @click="resource.refresh()"
      >
        <Icon name="refresh" :size="16" />
      </button>
    </div>
  </div>
  <form
    v-if="filtersOpen"
    id="customer-filters"
    class="customer-filters"
    @submit.prevent="applyFilters"
  >
    <label class="field"
      >Account status<select v-model="filters.accountActive">
        <option value="">All accounts</option>
        <option value="true">Active accounts</option>
        <option value="false">Inactive accounts</option>
      </select></label
    >
    <label class="field"
      >Subscription<select v-model="filters.hasSubscription">
        <option value="">With or without subscription</option>
        <option value="true">Has a subscription</option>
        <option value="false">No subscription</option>
      </select></label
    >
    <label class="field"
      >Plan code<input
        v-model="filters.planCode"
        :disabled="filters.hasSubscription === 'false'"
        placeholder="Any plan"
        maxlength="255"
    /></label>
    <div class="filter-actions">
      <button
        type="button"
        class="button"
        :disabled="!activeFilterCount && !filtersDirty"
        @click="clearFilters"
      >
        Clear filters</button
      ><button class="button primary" :disabled="!filtersDirty">Apply</button>
    </div>
  </form>
  <div v-if="canBulkReview && selected.length" class="selection-bar">
    <Icon name="check" :size="16" /><strong
      >{{ selected.length }} selected</strong
    ><button class="text-link button ghost small" @click="selected = []">
      Clear selection</button
    ><RouterLink
      class="button primary small"
      :to="
        withReturnTo(
          '/changes/new?population=true&accounts=' + selected.join(','),
          route.fullPath,
        )
      "
      >Prepare a change<Icon name="right" :size="13"
    /></RouterLink>
  </div>
  <section class="panel">
    <ResourceState
      :loading="resource.loading.value"
      :error="resource.error.value"
      :empty="!data?.content.length"
      title="No customers match this view"
      description="Try a different name or clear the filters."
      @retry="resource.refresh()"
      ><div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th v-if="canBulkReview" class="check-cell">
                <input
                  type="checkbox"
                  :checked="allSelected"
                  aria-label="Select customers on this page"
                  @change="togglePage"
                />
              </th>
              <th>Customer</th>
              <th>Subscription</th>
              <th>Status</th>
              <th class="numeric">Recurring price</th>
              <th>Renews</th>
              <th><span class="sr-only">Open workspace</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(customer, i) in data?.content" :key="customer.id">
              <td v-if="canBulkReview" class="check-cell">
                <input
                  type="checkbox"
                  :checked="selected.includes(customer.id)"
                  :aria-label="`Select ${customer.name}`"
                  @change="toggle(customer.id)"
                />
              </td>
              <td>
                <RouterLink
                  :to="
                    withReturnTo(`/customers/${customer.id}`, route.fullPath)
                  "
                  class="cell-main cell-link"
                  ><span
                    class="avatar company-avatar"
                    :class="`color-${i % 4}`"
                    >{{ initials(customer.name) }}</span
                  ><span
                    ><strong>{{ customer.name }}</strong
                    ><small
                      >{{ customer.slug
                      }}{{
                        !customer.active ? " · Inactive account" : ""
                      }}</small
                    ></span
                  ></RouterLink
                >
              </td>
              <td>
                <strong>{{
                  customer.latestSubscription?.planName || "No subscription"
                }}</strong
                ><span v-if="customer.latestSubscription" class="plan-version"
                  >v{{ customer.latestSubscription.planRevisionNumber }}</span
                >
              </td>
              <td>
                <StatusBadge
                  :status="customer.latestSubscription?.status || 'NONE'"
                  :text="
                    customer.latestSubscription ? undefined : 'Unsubscribed'
                  "
                />
              </td>
              <td class="numeric mono">
                {{
                  customer.latestSubscription
                    ? money(
                        customer.latestSubscription.currentPrice,
                        customer.latestSubscription.currencyCode,
                      )
                    : "—"
                }}<small v-if="customer.latestSubscription" class="price-period"
                  >/
                  {{
                    customer.latestSubscription.billingCycle === "YEARLY"
                      ? "year"
                      : "month"
                  }}</small
                >
              </td>
              <td class="muted">
                {{ date(customer.latestSubscription?.currentPeriodEnd) }}
              </td>
              <td>
                <RouterLink
                  class="icon-button"
                  :to="
                    withReturnTo(`/customers/${customer.id}`, route.fullPath)
                  "
                  :aria-label="`Open ${customer.name} workspace`"
                  ><Icon name="arrow" :size="15"
                /></RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        v-if="data"
        :page="currentPage"
        :size="10"
        :total="data.totalElements"
        @change="update({ page: String($event) })"
    /></ResourceState>
  </section>
</template>
<style scoped>
.customer-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 16px;
  padding: 18px 0;
  margin-top: -12px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.customer-filters .field {
  flex: 1;
  min-width: 190px;
}
.filter-actions {
  display: flex;
  gap: 8px;
}
.directory-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 0 22px;
  margin-top: -6px;
  border-top: 1px solid var(--border);
}
.directory-intro > div {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}
.directory-intro strong {
  font-size: 15px;
}
.directory-intro span,
.directory-intro p {
  color: var(--muted);
  font-size: 10px;
}
.plan-version {
  font-size: 9px;
  color: var(--muted);
  margin-left: 7px;
  background: var(--stone-100);
  padding: 2px 4px;
  border-radius: 3px;
}
.price-period {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 3px;
}
@media (max-width: 700px) {
  .directory-intro p {
    display: none;
  }
}
</style>
