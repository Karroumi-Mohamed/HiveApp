<script setup lang="ts">
import Facts from "./Facts.vue";
import ResourceState from "./ResourceState.vue";
import { useResource } from "@/composables/useResource";
import { read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
const sections = [
  {
    title: "Services",
    permission: p.observabilityReadHealth,
    api: adminApi.observabilityHealth,
  },
  {
    title: "Backlogs",
    permission: p.observabilityReadBacklogs,
    api: adminApi.observabilityBacklogs,
  },
  {
    title: "Registry",
    permission: p.registrySync,
    api: adminApi.latestRegistrySync,
  },
  {
    title: "Log access",
    permission: p.observabilityReadLogAccess,
    api: adminApi.observabilityLogAccess,
  },
]
  .filter((s) => can(s.permission))
  .map((s) => ({
    ...s,
    result: useResource(
      () => read(s.permission, s.api as () => Promise<unknown>),
      [],
      s.title === "Services" ? 15000 : 0,
    ),
  }));
</script>
<template>
  <section
    v-for="section in sections"
    :key="section.title"
    class="report-section"
  >
    <h2>{{ section.title }}</h2>
    <ResourceState
      :loading="section.result.loading.value"
      :error="section.result.error.value"
      @retry="section.result.refresh()"
      ><Facts :data="section.result.data.value"
    /></ResourceState>
  </section>
  <p v-if="!sections.length" class="notice">
    You do not have access to system status.
  </p>
</template>
