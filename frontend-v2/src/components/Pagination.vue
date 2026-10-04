<script setup lang="ts">
import Icon from "./Icon.vue";
import { number } from "@/lib/format";
defineProps<{ page: number; size: number; total: number }>();
defineEmits<{ change: [page: number] }>();
</script>
<template>
  <div class="pagination">
    <span
      >{{ total ? number(page * size + 1) : 0 }}–{{
        number(Math.min((page + 1) * size, total))
      }}
      of {{ number(total) }}</span
    >
    <div>
      <button
        class="icon-button"
        aria-label="Previous page"
        :disabled="page === 0"
        @click="$emit('change', page - 1)"
      >
        <Icon name="previous" /></button
      ><span>Page {{ page + 1 }}</span
      ><button
        class="icon-button"
        aria-label="Next page"
        :disabled="(page + 1) * size >= total"
        @click="$emit('change', page + 1)"
      >
        <Icon name="next" />
      </button>
    </div>
  </div>
</template>
