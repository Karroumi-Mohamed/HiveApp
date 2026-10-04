<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { AdminPermission } from "@/api/contracts";
import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { read } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import ResourceState from "@/components/ResourceState.vue";
import { label } from "@/lib/format";

const props = defineProps<{
  id: string;
  labelledBy: string;
  modelValue?: string[];
  maxSelections?: number;
  knownPermissions?: AdminPermission[];
}>();
const emit = defineEmits<{ "update:modelValue": [value: string[]] }>();
const search = ref(""),
  selectedOnly = ref(false),
  selectionError = ref("");
const grantable = useResource(() =>
  read(p.rolesListGrantable, adminApi.grantableRolePermissions),
);
const selected = computed(() => [...new Set(props.modelValue || [])]);
const excessCount = computed(() =>
  props.maxSelections === undefined
    ? 0
    : Math.max(0, selected.value.length - props.maxSelections),
);
const selectedIds = computed(() => new Set(selected.value));
const query = computed(() => search.value.trim().toLowerCase());
const availableIds = computed(
  () =>
    new Set((grantable.data.value || []).map((permission) => permission.id)),
);
const unavailable = computed(() =>
  selected.value
    .filter((id) => !availableIds.value.has(id))
    .map((id) => ({
      id,
      name:
        props.knownPermissions?.find((permission) => permission.id === id)
          ?.name || "Previously selected permission",
    })),
);
const groups = computed(() => {
  const grouped = new Map<string, AdminPermission[]>();
  for (const permission of grantable.data.value || []) {
    const resource =
      permission.resource || permission.code.split(".").slice(0, -1).join(".");
    grouped.set(resource, [...(grouped.get(resource) || []), permission]);
  }
  return [...grouped]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([resource, permissions]) => {
      const matching = permissions.filter(
        (permission) =>
          (!selectedOnly.value || selectedIds.value.has(permission.id)) &&
          (!query.value ||
            [
              permission.name,
              permission.code,
              permission.description,
              permission.resource,
              permission.action,
            ]
              .join(" ")
              .replaceAll("_", " ")
              .toLowerCase()
              .includes(query.value)),
      );
      return {
        resource,
        title: label(
          resource.replace(/^platform\./, "").replaceAll(".", " · "),
        ),
        permissions,
        matching,
        selectedCount: permissions.filter((permission) =>
          selectedIds.value.has(permission.id),
        ).length,
        matchingSelectedCount: matching.filter((permission) =>
          selectedIds.value.has(permission.id),
        ).length,
      };
    })
    .filter((group) => group.matching.length);
});
const matchingCount = computed(() =>
  groups.value.reduce((count, group) => count + group.matching.length, 0),
);
watch(
  () => props.modelValue,
  () => (selectionError.value = ""),
);

function change(ids: string[], checked: boolean) {
  const legalIds = ids.filter((id) => availableIds.value.has(id));
  const next = checked
    ? [...new Set([...selected.value, ...legalIds])]
    : selected.value.filter((id) => !ids.includes(id));
  if (
    checked &&
    props.maxSelections !== undefined &&
    next.length > props.maxSelections
  ) {
    selectionError.value = `A new role can include up to ${props.maxSelections} permissions. Choose permissions individually or remove some selections.`;
    return false;
  }
  selectionError.value = "";
  emit("update:modelValue", next);
  return true;
}
function selectionChanged(ids: string[], event: Event) {
  const input = event.target as HTMLInputElement;
  if (!change(ids, input.checked)) {
    const count = ids.filter((id) => selectedIds.value.has(id)).length;
    input.checked = count === ids.length;
    input.indeterminate = count > 0 && count < ids.length;
  }
}
function atLimit(id: string) {
  return (
    !selectedIds.value.has(id) &&
    props.maxSelections !== undefined &&
    selected.value.length >= props.maxSelections
  );
}
</script>
<template>
  <div
    :id="id"
    class="role-permissions-editor"
    role="group"
    :aria-labelledby="labelledBy"
  >
    <div class="permission-toolbar">
      <label class="search-field"
        ><input
          v-model="search"
          type="search"
          aria-label="Search grantable permissions"
          placeholder="Search permissions"
          @input.stop
      /></label>
      <label class="selected-filter"
        ><input
          v-model="selectedOnly"
          type="checkbox"
          @input.stop
          @change.stop
        />Selected only</label
      >
      <span class="muted small" role="status"
        >{{ selected.length
        }}{{
          maxSelections !== undefined ? " / " + maxSelections : ""
        }}
        selected<span v-if="grantable.data.value">
          · {{ grantable.data.value.length }} available</span
        ></span
      >
    </div>
    <p class="muted small">
      Only permissions available to your access are listed.
    </p>
    <p
      v-if="query || selectedOnly"
      class="muted small permission-matches"
      role="status"
    >
      {{ matchingCount }} matches. Group selection applies to these matches.
    </p>
    <p v-if="selectionError" class="notice warning" role="alert">
      {{ selectionError }}
    </p>
    <p v-else-if="excessCount" class="notice warning" role="alert">
      Remove {{ excessCount }} permissions to stay within the new-role limit of
      {{ maxSelections }}.
    </p>
    <ResourceState
      :loading="grantable.loading.value"
      :error="grantable.error.value"
      @retry="grantable.refresh()"
    >
      <section v-if="unavailable.length" class="permission-unavailable">
        <h3>Unavailable for new grants</h3>
        <p class="muted small">
          Existing selections are kept until you remove them.
        </p>
        <div
          v-for="permission in unavailable"
          :key="permission.id"
          class="unavailable-permission"
        >
          <span>{{ permission.name }}</span>
          <button
            type="button"
            class="text-link"
            :aria-label="'Remove ' + permission.name"
            @click="change([permission.id], false)"
          >
            Remove
          </button>
        </div>
      </section>
      <details
        v-for="group in groups"
        :key="group.resource"
        :open="!!query || selectedOnly"
        class="permission-domain"
      >
        <summary>
          <strong>{{ group.title }}</strong
          ><span class="muted small"
            >{{ group.selectedCount }} /
            {{ group.permissions.length }} selected</span
          >
        </summary>
        <div class="permission-group-tools">
          <label class="permission-select-group">
            <input
              type="checkbox"
              :checked="group.matchingSelectedCount === group.matching.length"
              :indeterminate.prop="
                group.matchingSelectedCount > 0 &&
                group.matchingSelectedCount < group.matching.length
              "
              :aria-label="
                'Select ' +
                (query || selectedOnly
                  ? 'matching permissions in '
                  : 'permissions in ') +
                group.title
              "
              @change="
                selectionChanged(
                  group.matching.map((permission) => permission.id),
                  $event,
                )
              "
            />
            {{ query || selectedOnly ? "Select matches" : "Select group" }}
          </label>
          <span v-if="query || selectedOnly" class="muted small"
            >{{ group.matching.length }} shown</span
          >
        </div>
        <div class="permission-options">
          <label
            v-for="permission in group.matching"
            :key="permission.id"
            class="permission-option"
          >
            <input
              type="checkbox"
              :checked="selectedIds.has(permission.id)"
              :disabled="atLimit(permission.id)"
              @change="selectionChanged([permission.id], $event)"
            />
            <span
              ><strong>{{ permission.name || label(permission.action) }}</strong
              ><small
                v-if="
                  permission.description &&
                  permission.description !== permission.name
                "
                class="muted"
                >{{ permission.description }}</small
              ></span
            >
          </label>
        </div>
      </details>
      <p v-if="!groups.length" class="muted permission-empty">
        {{
          query || selectedOnly
            ? "No matching permissions."
            : "No permissions are available for new grants."
        }}
      </p>
    </ResourceState>
  </div>
</template>
<style scoped>
.role-permissions-editor {
  min-width: 0;
}
.permission-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.permission-toolbar .search-field {
  flex: 1;
  min-width: min(100%, 15rem);
}
.selected-filter,
.permission-select-group {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  cursor: pointer;
}
input[type="checkbox"] {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  margin: 0;
}
.permission-matches {
  margin-top: 8px;
}
.permission-domain {
  border-bottom: 1px solid var(--border);
}
.permission-domain:first-of-type {
  margin-top: 16px;
  border-top: 1px solid var(--border);
}
.permission-domain summary {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
  padding: 16px 2px;
  cursor: pointer;
}
.permission-domain summary strong:before {
  content: "›";
  display: inline-block;
  width: 18px;
  color: var(--muted);
}
.permission-domain[open] summary strong:before {
  content: "⌄";
}
.permission-group-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 18px;
  background: var(--surface-soft);
  border-radius: 7px;
}
.permission-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
  padding: 12px 0 16px;
}
.permission-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-height: 44px;
  padding: 12px 2px;
  cursor: pointer;
}
.permission-option input {
  margin-top: 1px;
}
.permission-option span {
  min-width: 0;
}
.permission-option strong {
  display: block;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.permission-option small {
  display: block;
  line-height: 1.5;
  margin-top: 3px;
}
.permission-option:has(input:disabled) {
  opacity: 0.65;
  cursor: default;
}
.permission-unavailable {
  border-left: 3px solid var(--warning);
  padding: 12px 16px;
  margin-top: 16px;
}
.permission-unavailable p {
  margin-top: 6px;
}
.unavailable-permission {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  min-height: 44px;
}
.permission-empty {
  padding: 20px 0;
}
@media (max-width: 700px) {
  .permission-options {
    grid-template-columns: 1fr;
  }
  .permission-domain summary {
    align-items: flex-start;
  }
}
</style>
