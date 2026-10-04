<script setup lang="ts">
import { computed } from "vue";
import Facts from "./Facts.vue";
import { label } from "@/lib/format";
const props = defineProps<{ value: unknown; name?: string }>();
const quota = computed(() =>
  Array.isArray(props.value) &&
  props.value.length > 0 &&
  props.value.every(
    (x) => x && typeof x === "object" && "resource" in x && "mode" in x,
  )
    ? props.value
    : undefined,
);
const names = computed(() =>
  Array.isArray(props.value) &&
  props.value.every(
    (x) => x == null || typeof x !== "object" || x.name || x.code,
  )
    ? props.value.map((x) => x?.name || x?.code || x).join(", ")
    : undefined,
);
const text = computed(() =>
  typeof props.value === "string" &&
  !/code$|id$/i.test(props.name || "") &&
  /^[A-Z][A-Z_]+$/.test(props.value)
    ? label(props.value)
    : props.value,
);
</script>
<template>
  <span v-if="value == null || (Array.isArray(value) && !value.length)">—</span>
  <div v-else-if="quota">
    <div v-for="item in quota" :key="item.resource">
      {{ label(item.resource) }} ·
      {{ item.mode === "UNLIMITED" ? "Unlimited" : item.limit }}
    </div>
  </div>
  <span v-else-if="names !== undefined">{{ names || "—" }}</span>
  <details v-else-if="typeof value === 'object'">
    <summary class="text-link">View details</summary>
    <Facts :data="value" />
  </details>
  <span v-else>{{ text }}</span>
</template>
