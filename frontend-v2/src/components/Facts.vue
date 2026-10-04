<script setup lang="ts">
import { computed } from "vue";
import { label, date } from "@/lib/format";
import type { RecordData } from "@/resources/types";
const props = defineProps<{ data: unknown; depth?: number }>();
const internal = new Set([
  "id",
  "previewToken",
  "fingerprint",
  "registryVersion",
  "catalogRevision",
  "expectedVersion",
  "idempotencyKey",
  "userId",
  "lineageId",
  "expectedSubscriptionVersion",
  "sourceSubscriptionId",
  "resultSubscriptionId",
  "schemaVersion",
  "requestedByUserId",
  "createdByUserId",
  "version",
  "definitionVersion",
  "fingerprintHash",
  "snapshotHash",
]);
const objects = computed(() =>
  Array.isArray(props.data) &&
  props.data.length > 0 &&
  props.data.every((x) => x && typeof x === "object" && !Array.isArray(x))
    ? (props.data as RecordData[])
    : undefined,
);
const keys = computed(() =>
  objects.value
    ? [...new Set(objects.value.flatMap((x) => Object.keys(x)))]
        .filter(
          (k) =>
            !internal.has(k) &&
            objects.value!.some(
              (x) => x[k] != null && typeof x[k] !== "object",
            ),
        )
        .slice(0, 6)
    : [],
);
const entries = computed(() =>
  props.data && typeof props.data === "object" && !Array.isArray(props.data)
    ? Object.entries(props.data as RecordData).filter(
        ([key]) => !internal.has(key),
      )
    : [],
);
function title(key: string) {
  if (key === "sample") return "Results";
  return label(key.replace(/([a-z])([A-Z])/g, "$1 $2"));
}
function text(value: unknown, key: string) {
  if (value == null) return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (
    typeof value === "string" &&
    /At$|From$|Until$/.test(key) &&
    value.includes("T")
  )
    return date(value, true);
  return typeof value === "string" &&
    !/code$/i.test(key) &&
    /^[A-Z][A-Z_]+$/.test(value)
    ? label(value)
    : String(value);
}
function extra(row: RecordData) {
  return Object.fromEntries(
    Object.entries(row).filter(
      ([k]) => !keys.value.includes(k) && !internal.has(k),
    ),
  );
}
</script>
<template>
  <div v-if="objects && keys.length" class="table-scroll">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="key in keys" :key="key">{{ title(key) }}</th>
          <th>Details</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in objects" :key="row.id || index">
          <td v-for="key in keys" :key="key">{{ text(row[key], key) }}</td>
          <td>
            <details v-if="Object.keys(extra(row)).length">
              <summary class="text-link">View</summary>
              <Facts :data="extra(row)" :depth="(depth || 0) + 1" />
            </details>
            <span v-else>—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-else-if="Array.isArray(data)">
    <p v-if="!data.length" class="muted small">No records</p>
    <div v-for="(item, index) in data" :key="index">
      <Facts
        v-if="item && typeof item === 'object'"
        :data="item"
        :depth="(depth || 0) + 1"
      /><span v-else
        >{{ text(item, "") }}{{ index < data.length - 1 ? ", " : "" }}</span
      >
    </div>
  </div>
  <table v-else-if="entries.length" class="facts-table">
    <tbody>
      <tr v-for="[key, value] in entries" :key="key">
        <th>{{ title(key) }}</th>
        <td>
          <Facts
            v-if="value && typeof value === 'object'"
            :data="value"
            :depth="(depth || 0) + 1"
          /><span v-else>{{ text(value, key) }}</span>
        </td>
      </tr>
    </tbody>
  </table>
  <p v-else class="muted small">
    {{
      data == null
        ? "No details available"
        : typeof data === "object"
          ? "No records"
          : String(data)
    }}
  </p>
</template>
