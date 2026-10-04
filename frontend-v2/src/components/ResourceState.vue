<script setup lang="ts">
import Icon from "./Icon.vue";
defineProps<{
  loading?: boolean;
  error?: string;
  empty?: boolean;
  title?: string;
  description?: string;
}>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="loading" class="loading-block" role="status" aria-label="Loading">
    <div class="skeleton" />
    <div class="skeleton" />
    <div class="skeleton short" />
    <span class="sr-only">Loading…</span>
  </div>
  <div v-else-if="error" class="empty-state error-state" role="alert">
    <Icon name="alert" :size="28" />
    <h3>We couldn’t load this view</h3>
    <p>{{ error }}</p>
    <button class="button" @click="$emit('retry')">
      <Icon name="refresh" />Try again
    </button>
  </div>
  <div v-else-if="empty" class="empty-state">
    <h3>{{ title || "Nothing here yet" }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot name="action" />
  </div>
  <slot v-else />
</template>
