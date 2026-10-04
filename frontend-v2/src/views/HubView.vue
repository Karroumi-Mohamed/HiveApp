<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceList from "@/components/ResourceList.vue";
import SystemStatus from "@/components/SystemStatus.vue";
import { withReturnTo } from "@/lib/navigation";
import { adminPermissions as p } from "@/auth/permissions";
import { resources } from "@/resources";
import { can } from "@/data/session";
const canCreate = (key: string) => {
  const resource = resources[key]!;
  return (
    !!resource.save &&
    (resource.createPermissions || [resource.createPermission || ""]).some(
      (permission) => can(permission),
    ) &&
    (resource.createRequirements || []).every((permission) => can(permission))
  );
};
const route = useRoute();
const tabs = computed(() =>
  ((route.meta.resources as string[]) || [])
    .map((key) => ({
      key,
      label:
        (
          {
            segments: "Audiences",
            policies: "Commercial rules",
            operators: "Operators",
            roles: "Access roles",
            features: "Platform controls",
          } as Record<string, string>
        )[key] || resources[key]!.title,
      permissions: [
        ...(resources[key]!.listPermissions || [
          resources[key]!.listPermission,
        ]),
        ...(resources[key]!.createPermissions ||
          (resources[key]!.createPermission
            ? [resources[key]!.createPermission!]
            : [])),
      ],
    }))
    .filter(
      (t) =>
        can(
          ...(resources[t.key]!.listPermissions || [
            resources[t.key]!.listPermission,
          ]),
        ) || canCreate(t.key),
    )
    .concat(
      route.path === "/settings" &&
        can(
          p.observabilityReadHealth,
          p.observabilityReadBacklogs,
          p.observabilityReadLogAccess,
          p.registrySync,
        )
        ? [{ key: "health", label: "System status", permissions: [] }]
        : [],
    ),
);
const canList = (key: string) =>
  can(...(resources[key]!.listPermissions || [resources[key]!.listPermission]));
const view = computed(() =>
  route.query.view
    ? tabs.value.find((t) => t.key === route.query.view)?.key
    : tabs.value[0]?.key,
);
</script>
<template>
  <PageHeading :title="String(route.meta.title)" /><ViewTabs
    :tabs="tabs"
    :current="view || ''"
  /><SystemStatus v-if="view === 'health'" /><ResourceList
    v-else-if="view && canList(view)"
    :resource-key="view"
  /><template v-else-if="view"
    ><RouterLink
      v-if="canCreate(view)"
      class="button primary"
      :to="withReturnTo(resources[view]!.base + '/new', route.fullPath)"
      >New {{ resources[view]!.singular.toLowerCase() }}</RouterLink
    >
    <p class="notice">
      Viewing existing records requires additional access.
    </p></template
  >
  <p v-else class="notice">You do not have access to this workspace.</p>
</template>
