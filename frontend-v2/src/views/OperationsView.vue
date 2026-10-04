<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceList from "@/components/ResourceList.vue";
import ResourceState from "@/components/ResourceState.vue";
import FieldInput from "@/components/FieldInput.vue";
import { planField } from "@/resources/fields";
import Facts from "@/components/Facts.vue";
import { useResource } from "@/composables/useResource";
import { read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
const route = useRoute(),
  router = useRouter();
const rolloutFilter = reactive({ planId: route.query.planId || null });
watch(
  () => route.query.planId,
  (value) => (rolloutFilter.planId = value || null),
);
watch(
  () => rolloutFilter.planId,
  (value) => {
    if (value !== route.query.planId)
      void router.replace({
        query: { ...route.query, planId: value || undefined, page: undefined },
      });
  },
);
const deliveryViews = computed(() =>
  [
    {
      key: "delivery",
      label: "Access emails",
      permission: p.communicationsRead,
    },
    {
      key: "notification-events",
      label: "Notifications",
      permission: p.notificationsDelivery,
    },
    {
      key: "notification-emails",
      label: "Notification emails",
      permission: p.notificationsDelivery,
    },
  ].filter((t) => can(t.permission)),
);
const tabs = computed(() =>
  [
    {
      key: "jobs",
      label: "Subscription changes",
      permission: p.subscriptionsListChangeJobs,
    },
    { key: "repricing", label: "Repricing", permission: p.repricingList },
    {
      key: "rollouts",
      label: "Plan applications",
      permission: p.plansListApplications,
    },
    {
      key: "messages",
      label: "Messages",
      permission: p.customerCommunicationsRead,
    },
    { key: "inbox", label: "Notifications", permission: p.notificationsRead },
    {
      key: "attention",
      label: "Attention",
      permission: p.analyticsReadOperations,
    },
    { key: "activity", label: "Activity", permission: p.activitiesRead },
    {
      key: "delivery",
      label: "Delivery",
      permission: can(p.communicationsRead)
        ? p.communicationsRead
        : p.notificationsDelivery,
    },
    {
      key: "health",
      label: "System status",
      permission:
        [
          p.observabilityReadHealth,
          p.observabilityReadBacklogs,
          p.observabilityReadLogAccess,
          p.registrySync,
        ].find((x) => can(x)) || p.observabilityReadHealth,
    },
  ].filter((t) => can(t.permission)),
);
const view = computed(
  () =>
    (deliveryViews.value.some((t) => t.key === route.query.view)
      ? "delivery"
      : tabs.value.find((t) => t.key === route.query.view)?.key) ||
    tabs.value[0]?.key,
);
const deliveryKind = computed(
  () =>
    deliveryViews.value.find((t) => t.key === route.query.view)?.key ||
    deliveryViews.value[0]?.key,
);
const listKey = computed(() =>
  view.value === "delivery" ? deliveryKind.value : view.value,
);
const health = useResource(
  () =>
    view.value === "health" && can(p.observabilityReadHealth)
      ? read(p.observabilityReadHealth, adminApi.observabilityHealth)
      : Promise.resolve(undefined),
  [view],
  15000,
  () => view.value === "health",
);
const backlog = useResource(
  () =>
    view.value === "health" && can(p.observabilityReadBacklogs)
      ? read(p.observabilityReadBacklogs, adminApi.observabilityBacklogs)
      : Promise.resolve(undefined),
  [view],
);
const logs = useResource(
  () =>
    view.value === "health" && can(p.observabilityReadLogAccess)
      ? read(p.observabilityReadLogAccess, adminApi.observabilityLogAccess)
      : Promise.resolve(undefined),
  [view],
);
const registry = useResource(
  () =>
    view.value === "health" && can(p.registrySync)
      ? read(p.registrySync, adminApi.latestRegistrySync)
      : Promise.resolve(undefined),
  [view],
);
</script>
<template>
  <PageHeading title="Operations"
    ><RouterLink
      v-if="view === 'repricing' && can(p.repricingPreview)"
      class="button primary"
      to="/operations/repricing/new"
      >Reprice subscriptions</RouterLink
    ><RouterLink
      v-else-if="view === 'rollouts' && can(p.plansCreateApplication)"
      class="button primary"
      to="/operations/rollouts/new"
      >Apply plan revision</RouterLink
    ><RouterLink
      v-else-if="can(p.subscriptionsPreviewChangeJob)"
      class="button primary"
      to="/changes/new"
      >Change subscriptions</RouterLink
    ></PageHeading
  ><ViewTabs :tabs="tabs" :current="view || ''" />
  <div v-if="view === 'rollouts'" class="resource-toolbar">
    <FieldInput :field="planField('planId', 'Plan')" :data="rolloutFilter" />
  </div>
  <div v-if="view === 'delivery'" class="resource-toolbar">
    <select
      :value="deliveryKind"
      aria-label="Delivery type"
      @change="
        router.replace({
          query: { view: ($event.target as HTMLSelectElement).value },
        })
      "
    >
      <option v-for="t in deliveryViews" :key="t.key" :value="t.key">
        {{ t.label }}
      </option>
    </select>
  </div>
  <ResourceList
    v-if="
      listKey &&
      view !== 'health' &&
      (view !== 'rollouts' || rolloutFilter.planId)
    "
    :resource-key="listKey"
  /><template v-else-if="view === 'health'"
    ><section v-if="can(p.observabilityReadHealth)" class="report-section">
      <h2>Services</h2>
      <ResourceState
        :loading="health.loading.value"
        :error="health.error.value"
        @retry="health.refresh()"
        ><Facts :data="health.data.value"
      /></ResourceState>
    </section>
    <section v-if="can(p.observabilityReadBacklogs)" class="report-section">
      <h2>Backlogs</h2>
      <ResourceState
        :loading="backlog.loading.value"
        :error="backlog.error.value"
        @retry="backlog.refresh()"
        ><Facts :data="backlog.data.value"
      /></ResourceState>
    </section>
    <section v-if="can(p.registrySync)" class="report-section">
      <h2>Registry</h2>
      <ResourceState
        :loading="registry.loading.value"
        :error="registry.error.value"
        @retry="registry.refresh()"
        ><Facts :data="registry.data.value"
      /></ResourceState>
    </section>
    <section v-if="can(p.observabilityReadLogAccess)" class="report-section">
      <h2>Logs</h2>
      <ResourceState
        :loading="logs.loading.value"
        :error="logs.error.value"
        @retry="logs.refresh()"
        ><Facts :data="logs.data.value"
      /></ResourceState></section
  ></template>
</template>
