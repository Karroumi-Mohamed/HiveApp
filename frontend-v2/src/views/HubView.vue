<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceList from "@/components/ResourceList.vue";
import { resources } from "@/resources";
import { can } from "@/data/session";
const route = useRoute();
const tabs = computed(() =>
  ((route.meta.resources as string[]) || [])
    .map((key) => ({
      key,
      label: resources[key]!.title,
      permissions: resources[key]!.listPermissions || [
        resources[key]!.listPermission,
      ],
    }))
    .filter((t) => can(...t.permissions)),
);
const view = computed(
  () =>
    tabs.value.find((t) => t.key === route.query.view)?.key ||
    tabs.value[0]?.key,
);
</script>
<template>
  <PageHeading :title="String(route.meta.title)" /><ViewTabs
    :tabs="tabs"
    :current="view || ''"
  /><ResourceList v-if="view" :resource-key="view" />
  <p v-else class="notice">You do not have access to this workspace.</p>
</template>
