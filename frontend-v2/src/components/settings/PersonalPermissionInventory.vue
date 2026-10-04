<script setup lang="ts">
import { computed, ref } from "vue";
import { label } from "@/lib/format";
const props = defineProps<{ permissions: string[] }>();
const search = ref("");
const groups = computed(() => {
  const entries = new Map<string, string[]>();
  const query = search.value.trim().toLowerCase();
  for (const permission of [...props.permissions].sort()) {
    if (query && !permission.replaceAll("_", " ").toLowerCase().includes(query))
      continue;
    const parts = permission.split(".");
    const resource = parts.slice(0, -1).join(".");
    entries.set(resource, [...(entries.get(resource) || []), permission]);
  }
  return [...entries].map(([resource, permissions]) => ({
    resource,
    permissions,
    title: label(resource.replace(/^platform\./, "").replaceAll(".", " · ")),
  }));
});
const count = computed(() =>
  groups.value.reduce((sum, group) => sum + group.permissions.length, 0),
);
</script>
<template>
  <section class="report-section">
    <div class="row spread">
      <h2>Effective permissions</h2>
      <span class="muted small"
        >{{ props.permissions.length }} permissions</span
      >
    </div>
    <label class="search-field" style="max-width: 24rem; margin: 1rem 0">
      <input
        v-model="search"
        type="search"
        aria-label="Search my permissions"
        placeholder="Search permissions"
      />
    </label>
    <p v-if="!groups.length" class="muted">
      {{
        props.permissions.length
          ? "No matching permissions."
          : "No permissions are assigned to this access."
      }}
    </p>
    <p v-if="search && groups.length" class="muted small" role="status">
      {{ count }} matching permissions
    </p>
    <details
      v-for="group in groups"
      :key="group.resource"
      :open="!!search"
      class="permission-group"
    >
      <summary
        style="
          padding: 0.875rem 0;
          cursor: pointer;
          border-bottom: 1px solid var(--border);
        "
      >
        {{ group.title }} · {{ group.permissions.length }}
      </summary>
      <ul style="margin: 0.75rem 0 1rem; padding-left: 1.5rem">
        <li
          v-for="permission in group.permissions"
          :key="permission"
          style="padding: 0.35rem 0"
        >
          {{ label(permission.split(".").at(-1)) }}
          <small
            class="muted"
            style="display: block; overflow-wrap: anywhere"
            >{{ permission }}</small
          >
        </li>
      </ul>
    </details>
  </section>
</template>
