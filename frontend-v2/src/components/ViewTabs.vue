<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
const props = defineProps<{
  tabs: { key: string; label: string; count?: number }[];
  current: string;
  parameter?: string;
}>();
const route = useRoute();
const router = useRouter();
const link = (key: string) => ({
  path: route.path,
  query: {
    ...route.query,
    [props.parameter || "view"]: key,
    page: undefined,
    q: undefined,
    status: undefined,
  },
});
</script>
<template>
  <nav class="view-tabs" aria-label="Section views">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.key"
      :to="link(tab.key)"
      :class="{ selected: current === tab.key }"
      :aria-current="current === tab.key ? 'page' : undefined"
      >{{ tab.label
      }}<span v-if="tab.count !== undefined" class="tab-count">{{
        tab.count
      }}</span></RouterLink
    >
  </nav>
</template>
