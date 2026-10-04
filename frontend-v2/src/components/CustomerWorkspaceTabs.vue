<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import ViewTabs from "./ViewTabs.vue";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const route = useRoute();
const tabs = computed(() =>
  [
    {
      key: "directory",
      label: "Directory",
      permissions: [
        p.subscriptionsSearch,
        p.subscriptionsChooseAccounts,
        p.subscriptionsChooseChangeOptions,
        p.subscriptionsPreviewChange,
        p.subscriptionsApplyChange,
      ],
    },
    {
      key: "agreements",
      label: "Agreements",
      permissions: [p.subscriptionsSearchSpecialAgreements],
    },
    {
      key: "messages",
      label: "Communications",
      permissions: [
        p.customerCommunicationsRead,
        p.customerCommunicationsCreate,
      ],
    },
    {
      key: "invoices",
      label: "Invoices",
      permissions: [p.billingListInvoices],
    },
  ].filter((t) => can(...t.permissions)),
);
const current = computed(() =>
  String(route.query.view || tabs.value[0]?.key || ""),
);
</script>
<template><ViewTabs :tabs="tabs" :current="current" /></template>
