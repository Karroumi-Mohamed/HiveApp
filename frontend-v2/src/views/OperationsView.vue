<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceList from "@/components/ResourceList.vue";
import FieldInput from "@/components/FieldInput.vue";
import { planField } from "@/resources/fields";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { withReturnTo } from "@/lib/navigation";
const route = useRoute(),
  router = useRouter();
const executionKinds = computed(() =>
  [
    {
      key: "jobs",
      label: "Subscription changes",
      permission: p.subscriptionsListChangeJobs,
    },
    { key: "repricing", label: "Repricing", permission: p.repricingList },
    {
      key: "rollouts",
      label: "Plan revision applications",
      permission: p.plansListApplications,
    },
  ].filter((t) => can(t.permission)),
);
const deliveryKinds = computed(() =>
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
const tabs = computed(() => [
  ...(can(p.analyticsReadOperations)
    ? [{ key: "queue", label: "Work queue" }]
    : []),
  ...(executionKinds.value.length
    ? [{ key: "executions", label: "Executions" }]
    : []),
  ...(deliveryKinds.value.length
    ? [{ key: "delivery", label: "Delivery" }]
    : []),
]);
const view = computed(() =>
  String(
    route.query.view ||
      tabs.value[0]?.key ||
      (can(p.activitiesRead) ? "activity" : ""),
  ),
);
const allowed = computed(
  () =>
    tabs.value.some((t) => t.key === view.value) ||
    (view.value === "activity" && can(p.activitiesRead)),
);
const executionKind = computed(() =>
  String(route.query.kind || executionKinds.value[0]?.key || ""),
);
const deliveryKind = computed(() =>
  String(route.query.delivery || deliveryKinds.value[0]?.key || ""),
);
const selectedExecution = computed(() =>
  executionKinds.value.some((t) => t.key === executionKind.value),
);
const selectedDelivery = computed(() =>
  deliveryKinds.value.some((t) => t.key === deliveryKind.value),
);
const listKey = computed(() =>
  view.value === "queue"
    ? "attention"
    : view.value === "executions"
      ? executionKind.value
      : view.value === "delivery"
        ? deliveryKind.value
        : "activity",
);
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
function changeKind(parameter: string, value: string) {
  void router.push({
    query: {
      ...route.query,
      [parameter]: value,
      page: undefined,
      q: undefined,
      status: undefined,
      sort: undefined,
      direction: undefined,
    },
  });
}
</script>
<template>
  <PageHeading title="Operations">
    <details
      v-if="
        view !== 'executions' &&
        can(
          p.subscriptionsPreviewChangeJob,
          p.repricingPreview,
          p.plansCreateApplication,
        )
      "
      class="more-actions"
    >
      <summary class="button">New execution</summary>
      <div class="action-menu">
        <RouterLink
          v-if="can(p.subscriptionsPreviewChangeJob)"
          :to="withReturnTo('/changes/new', route.fullPath)"
          >Change subscriptions</RouterLink
        ><RouterLink
          v-if="can(p.repricingPreview)"
          :to="withReturnTo('/operations/repricing/new', route.fullPath)"
          >Reprice subscriptions</RouterLink
        ><RouterLink
          v-if="can(p.plansCreateApplication)"
          :to="withReturnTo('/operations/rollouts/new', route.fullPath)"
          >Apply plan revision</RouterLink
        >
      </div>
    </details>
    <RouterLink
      v-if="
        view === 'executions' &&
        executionKind === 'jobs' &&
        can(p.subscriptionsPreviewChangeJob)
      "
      class="button primary"
      :to="withReturnTo('/changes/new', route.fullPath)"
      >Change subscriptions</RouterLink
    >
    <RouterLink
      v-if="
        view === 'executions' &&
        executionKind === 'repricing' &&
        can(p.repricingPreview)
      "
      class="button primary"
      :to="withReturnTo('/operations/repricing/new', route.fullPath)"
      >Reprice subscriptions</RouterLink
    >
    <RouterLink
      v-if="
        view === 'executions' &&
        executionKind === 'rollouts' &&
        can(p.plansCreateApplication)
      "
      class="button primary"
      :to="
        withReturnTo(
          '/operations/rollouts/new' +
            (rolloutFilter.planId ? '?targetPlan=' + rolloutFilter.planId : ''),
          route.fullPath,
        )
      "
      >Apply revision</RouterLink
    >
  </PageHeading>
  <div class="workspace-navigation">
    <ViewTabs :tabs="tabs" :current="view" /><RouterLink
      v-if="can(p.activitiesRead)"
      class="text-link auxiliary-link"
      :class="{ selected: view === 'activity' }"
      to="/operations?view=activity"
      >Audit activity</RouterLink
    >
  </div>
  <template v-if="!allowed"
    ><div class="related-actions">
      <RouterLink
        v-if="can(p.subscriptionsPreviewChangeJob)"
        class="button primary"
        :to="withReturnTo('/changes/new', route.fullPath)"
        >Change subscriptions</RouterLink
      ><RouterLink
        v-if="can(p.repricingPreview)"
        class="button"
        :to="withReturnTo('/operations/repricing/new', route.fullPath)"
        >Reprice subscriptions</RouterLink
      ><RouterLink
        v-if="can(p.plansCreateApplication)"
        class="button"
        :to="withReturnTo('/operations/rollouts/new', route.fullPath)"
        >Apply revision</RouterLink
      >
    </div>
    <p class="notice">
      Viewing existing executions requires additional access.
    </p></template
  >
  <template v-else>
    <div v-if="view === 'executions'" class="resource-toolbar">
      <label class="inline-field"
        >Execution type<select
          :value="executionKind"
          @change="
            changeKind('kind', ($event.target as HTMLSelectElement).value)
          "
        >
          <option
            v-for="kind in executionKinds"
            :key="kind.key"
            :value="kind.key"
          >
            {{ kind.label }}
          </option>
        </select></label
      >
      <FieldInput
        v-if="executionKind === 'rollouts'"
        :field="planField('planId', 'Plan revision')"
        :data="rolloutFilter"
      />
    </div>
    <div v-if="view === 'delivery'" class="resource-toolbar">
      <label class="inline-field"
        >Delivery type<select
          :value="deliveryKind"
          @change="
            changeKind('delivery', ($event.target as HTMLSelectElement).value)
          "
        >
          <option
            v-for="kind in deliveryKinds"
            :key="kind.key"
            :value="kind.key"
          >
            {{ kind.label }}
          </option>
        </select></label
      >
    </div>
    <p
      v-if="
        (view === 'executions' && !selectedExecution) ||
        (view === 'delivery' && !selectedDelivery)
      "
      class="notice"
    >
      You do not have access to this type of work.
    </p>
    <p
      v-else-if="
        view === 'executions' &&
        executionKind === 'rollouts' &&
        !rolloutFilter.planId
      "
      class="notice"
    >
      Choose a plan revision to view its applications. You can also open
      applications from the plan’s Versions section.
    </p>
    <ResourceList v-else :resource-key="listKey" />
  </template>
</template>
