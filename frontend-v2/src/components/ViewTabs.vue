<script lang="ts">
const rememberedViews = new Map<string, Record<string, any>>();
</script>
<script setup lang="ts">
import { watch } from "vue";
import { useRoute } from "vue-router";
import { session } from "@/data/session";
const props = defineProps<{
  tabs: { key: string; label: string; count?: number }[];
  current: string;
  parameter?: string;
}>();
const route = useRoute();
const stateKey = (view: string) =>
  (session.me?.id || "") +
  ":" +
  route.path +
  ":" +
  (props.parameter || "view") +
  ":" +
  view;
watch(
  () => route.fullPath,
  () => {
    rememberedViews.set(stateKey(props.current), { ...route.query });
  },
  { immediate: true },
);
const link = (key: string) => {
  const remembered = rememberedViews.get(stateKey(key));
  return {
    path: route.path,
    query: {
      ...(remembered || {}),
      returnTo: route.query.returnTo,
      account: route.query.account,
      [props.parameter || "view"]: key,
      sectionPage: undefined,
      edit: undefined,
    },
  };
};
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
