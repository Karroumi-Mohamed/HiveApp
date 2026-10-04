<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import ActionDialog from "./ActionDialog.vue";
import type { Action, RecordData } from "@/resources/types";
import { resources } from "@/resources";
import { rows, get } from "@/resources/types";
import { read } from "@/data/gateway";
import { can } from "@/data/session";
import { useResource } from "@/composables/useResource";
import ResourceState from "./ResourceState.vue";
import CellValue from "@/components/CellValue.vue";
import StatusBadge from "./StatusBadge.vue";
import Pagination from "./Pagination.vue";
import { date, money, label } from "@/lib/format";
const props = defineProps<{ resourceKey: string }>(),
  route = useRoute(),
  router = useRouter(),
  resource = computed(() => resources[props.resourceKey]!);
const page = computed(() => Math.max(0, Number(route.query.page) || 0)),
  search = ref(String(route.query.q || "")),
  status = computed(() => String(route.query.status || ""));
let timer: ReturnType<typeof setTimeout>;
watch([() => props.resourceKey, () => route.query.q], () => {
  search.value = String(route.query.q || "");
});
function query(values: Record<string, unknown>) {
  void router.replace({ query: { ...route.query, ...values } as any });
}
watch(search, (value) => {
  clearTimeout(timer);
  timer = setTimeout(
    () => query({ q: value || undefined, page: undefined }),
    250,
  );
});
const effectiveFilters = computed(() =>
  props.resourceKey === "plans" &&
  can("platform.plans.list_families") &&
  (route.query.group || (route.query.status ? "revisions" : "families")) !==
    "revisions"
    ? []
    : resource.value.filters,
);
const result = useResource(
  () =>
    read(
      props.resourceKey === "plans" &&
        can("platform.plans.list_families") &&
        route.query.group !== "revisions" &&
        !status.value
        ? "platform.plans.list_families"
        : resource.value.listPermission,
      () =>
        resource.value.list({
          page: page.value,
          size: 20,
          ...(props.resourceKey === "plans"
            ? { group: route.query.group }
            : {}),
          ...(props.resourceKey === "rollouts"
            ? { planId: route.query.planId }
            : {}),
          search: String(route.query.q || "") || undefined,
          status: status.value || undefined,
          ...Object.fromEntries(
            (resource.value.filters || []).map((f) => [
              f.key,
              route.query[f.key] || undefined,
            ]),
          ),
        }),
    ),
  [() => props.resourceKey, () => route.query, page, status],
);
onUnmounted(() => clearTimeout(timer));
const selected = ref<string[]>([]);
const bulkActions = computed(
  () => resource.value.bulkActions?.filter((a) => can(a.permission)) || [],
);
watch(
  () => props.resourceKey,
  () => (selected.value = []),
);
const action = ref<Action>(),
  actionData = ref<RecordData>({});
const data = result.data;
const items = computed(() => rows(data.value));
function render(row: any, column: any) {
  const value = get(row, column.key);
  if (column.format === "date") return date(value, true);
  if (column.format === "money")
    return value == null
      ? "—"
      : money(
          String(value),
          get(row, column.currencyKey || "currencyCode") || "MAD",
        );
  if (column.format === "boolean")
    return value == null ? "—" : value ? "Yes" : "No";
  return Array.isArray(value)
    ? value
        .map((v) => (typeof v === "object" ? v.name || v.code : String(v)))
        .join(", ")
    : (value ?? "—");
}
</script>
<template>
  <div class="resource-toolbar">
    <select
      v-if="resourceKey === 'plans' && can('platform.plans.list_families')"
      :value="
        route.query.group || (route.query.status ? 'revisions' : 'families')
      "
      aria-label="Plan grouping"
      @change="
        query({
          group: ($event.target as HTMLSelectElement).value,
          page: undefined,
          status: undefined,
        })
      "
    >
      <option value="families">Plan families</option>
      <option v-if="can('platform.plans.list')" value="revisions">
        All revisions
      </option>
    </select>
    <label v-if="resource.searchable !== false" class="search-field"
      ><input
        v-model="search"
        type="search"
        :aria-label="'Search ' + resource.title"
        placeholder="Search" /></label
    ><label v-for="f in effectiveFilters"
      ><select
        :value="route.query[f.key] || ''"
        :aria-label="f.label"
        @change="
          query({
            [f.key]: ($event.target as HTMLSelectElement).value || undefined,
            page: undefined,
          })
        "
      >
        <option value="">All {{ f.label.toLowerCase() }}</option>
        <option v-for="v in f.values" :value="v">{{ label(v) }}</option>
      </select></label
    ><span class="muted small">{{
      result.loading.value
        ? "Loading…"
        : result.error.value
          ? "Unavailable"
          : ((data as any)?.totalElements ?? items.length) + " records"
    }}</span
    ><button class="button small" @click="result.refresh()">Refresh</button
    ><RouterLink
      v-if="
        resource.createPermission &&
        can(resource.createPermission) &&
        (resource.createRequirements || []).every((x) => can(x))
      "
      class="button primary"
      :to="resource.base + '/new'"
      >New {{ resource.singular.toLowerCase() }}</RouterLink
    >
  </div>
  <div v-if="selected.length" class="selection-bar">
    <strong>{{ selected.length }} selected</strong
    ><button
      v-for="a in bulkActions"
      class="button small"
      @click="
        action = a;
        actionData = { ids: [...selected] };
      "
    >
      {{ a.label }}</button
    ><button class="text-link" @click="selected = []">Clear</button>
  </div>
  <ResourceState
    :loading="result.loading.value"
    :error="result.error.value"
    :empty="!items.length"
    :title="'No ' + resource.title.toLowerCase()"
    @retry="result.refresh()"
    ><div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th v-if="bulkActions.length">
              <input
                type="checkbox"
                aria-label="Select current page"
                :checked="
                  !!items.length && items.every((r) => selected.includes(r.id))
                "
                @change="
                  selected = ($event.target as HTMLInputElement).checked
                    ? [...new Set([...selected, ...items.map((r) => r.id)])]
                    : selected.filter((id) => !items.some((r) => r.id === id))
                "
              />
            </th>
            <th v-for="column in resource.columns" :key="column.key">
              {{ column.label }}
            </th>
            <th v-if="resource.listActions">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id || row.code">
            <td v-if="bulkActions.length">
              <input
                v-model="selected"
                type="checkbox"
                :value="row.id"
                :aria-label="'Select ' + (row.name || row.email)"
              />
            </td>
            <td v-for="(column, index) in resource.columns" :key="column.key">
              <StatusBadge
                v-if="column.format === 'status'"
                :status="String(get(row, column.key) || 'UNKNOWN')"
              /><RouterLink
                v-else-if="column.link || (index === 0 && !resource.listOnly)"
                class="resource-link"
                :to="{
                  path: column.link?.(row) || resource.base + '/' + row.id,
                  query: route.query.account
                    ? { account: route.query.account }
                    : undefined,
                }"
                >{{ render(row, column) }}</RouterLink
              ><CellValue
                v-else
                :value="
                  column.format ? render(row, column) : get(row, column.key)
                "
                :name="column.key"
              />
            </td>
            <td v-if="resource.listActions">
              <button
                v-for="a in resource.listActions.filter(
                  (a) => can(a.permission) && (!a.visible || a.visible(row)),
                )"
                class="text-link"
                @click="
                  action = a;
                  actionData = row;
                "
              >
                {{ a.label }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination
      v-if="(data as any)?.totalElements !== undefined"
      :page="(data as any).page"
      :size="(data as any).size"
      :total="(data as any).totalElements"
      @change="query({ page: $event })" /></ResourceState
  ><ActionDialog
    :action="action"
    :data="actionData"
    @close="action = undefined"
    @done="
      result.refresh();
      selected = [];
    "
  />
</template>
