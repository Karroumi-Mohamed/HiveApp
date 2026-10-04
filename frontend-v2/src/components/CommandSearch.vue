<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import { resources } from "@/resources";
import { rows } from "@/resources/types";
import { gateway, read } from "@/data/gateway";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { taskNavigation, withReturnTo } from "@/lib/navigation";
import { useResource } from "@/composables/useResource";
import ResourceState from "./ResourceState.vue";
import Icon from "./Icon.vue";
const emit = defineEmits<{ navigate: [string] }>();
const route = useRoute();
const search = ref("");
const query = ref("");
const scope = ref("pages");
let timer: ReturnType<typeof setTimeout>;
watch(search, (value) => {
  clearTimeout(timer);
  timer = setTimeout(() => (query.value = value.trim()), 250);
});
onUnmounted(() => clearTimeout(timer));
const scopes = computed(() =>
  [
    { key: "pages", label: "Pages and tasks", permission: "" },
    { key: "customers", label: "Customers", permission: p.subscriptionsSearch },
    { key: "plans", label: "Plans", permission: p.plansList },
    { key: "offers", label: "Offers", permission: p.offersList },
    {
      key: "agreements",
      label: "Agreements",
      permission: p.subscriptionsSearchSpecialAgreements,
    },
  ].filter((s) => !s.permission || can(s.permission)),
);
const links = computed(() =>
  taskNavigation.filter(
    (entry) =>
      can(...entry.permissions) &&
      entry.title.toLowerCase().includes(search.value.toLowerCase()),
  ),
);
const result = useResource(async () => {
  if (scope.value === "pages" || !query.value) return [];
  if (scope.value === "customers") {
    const page = await gateway.accounts({
      query: query.value,
      page: 0,
      size: 20,
    });
    return page.content.map((r) => ({
      name: r.name,
      to: "/customers/" + r.id,
    }));
  }
  const resource = resources[scope.value]!;
  const page = await read(resource.listPermission, () =>
    resource.list({ search: query.value, page: 0, size: 20 }),
  );
  return rows(page).map((r) => ({
    name: r.name || r.planName || r.code || r.id,
    to: resource.columns[0]?.link?.(r) || resource.base + "/" + r.id,
  }));
}, [query, scope]);
function navigate(path: string) {
  emit("navigate", withReturnTo(path, route.fullPath));
}
function submit() {
  if (scope.value === "pages" && links.value[0]) navigate(links.value[0].path);
  else if (result.data.value?.[0]) navigate(result.data.value[0].to);
}
</script>
<template>
  <form class="command-search-form" @submit.prevent="submit">
    <label class="search-field command-search"
      ><Icon name="search" /><input
        v-model="search"
        autofocus
        type="search"
        aria-label="Find pages or records"
        placeholder="Find pages or records"
    /></label>
    <select v-model="scope" aria-label="Search in">
      <option v-for="option in scopes" :key="option.key" :value="option.key">
        {{ option.label }}
      </option>
    </select>
  </form>
  <div v-if="scope === 'pages'" class="command-results">
    <button
      v-for="item in links"
      :key="item.path"
      class="command-item"
      @click="navigate(item.path)"
    >
      <Icon :name="item.icon" />{{ item.title }}
    </button>
    <p v-if="!links.length" class="muted">No matching pages.</p>
  </div>
  <ResourceState
    v-else
    :loading="result.loading.value"
    :error="result.error.value"
    :empty="!result.data.value?.length"
    :title="query ? 'No matching records' : 'Enter a name to search'"
    @retry="result.refresh()"
  >
    <div class="command-results">
      <button
        v-for="item in result.data.value"
        :key="item.to"
        class="command-item"
        @click="navigate(item.to)"
      >
        {{ item.name }}
      </button>
    </div>
  </ResourceState>
</template>
