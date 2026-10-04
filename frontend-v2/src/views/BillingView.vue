<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import Facts from "@/components/Facts.vue";
import ResourceState from "@/components/ResourceState.vue";
import { useResource } from "@/composables/useResource";
import { read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceList from "@/components/ResourceList.vue";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const route = useRoute();
const tabs = computed(() =>
  [
    { key: "invoices", label: "Invoices", permission: p.billingListInvoices },
    {
      key: "agreements",
      label: "Agreements",
      permission: p.subscriptionsSearchSpecialAgreements,
    },
    {
      key: "provider-commands",
      label: "Payment commands",
      permission: p.billingListReconciliation,
    },
    {
      key: "provider-events",
      label: "Provider events",
      permission: p.billingListProviderEvents,
    },
  ].filter((t) => can(t.permission)),
);
const view = computed(
  () =>
    tabs.value.find((t) => t.key === route.query.view)?.key ||
    tabs.value[0]?.key,
);
const analytics = useResource(
  () =>
    view.value === "agreements" &&
    can(p.subscriptionsReadSpecialAgreementAnalytics)
      ? read(
          p.subscriptionsReadSpecialAgreementAnalytics,
          adminApi.specialAgreementAnalytics,
        )
      : Promise.resolve(undefined),
  [view],
);
</script>
<template>
  <PageHeading title="Billing" /><ViewTabs :tabs="tabs" :current="view || ''" />
  <section
    v-if="
      view === 'agreements' && can(p.subscriptionsReadSpecialAgreementAnalytics)
    "
    class="report-section"
  >
    <h2>Agreement summary</h2>
    <ResourceState
      :loading="analytics.loading.value"
      :error="analytics.error.value"
      @retry="analytics.refresh()"
      ><details>
        <summary class="text-link">View summary</summary>
        <Facts :data="analytics.data.value" /></details
    ></ResourceState>
  </section>
  <ResourceList v-if="view" :resource-key="view" />
</template>
