<script setup lang="ts">
import Icon from "@/components/Icon.vue";
</script>
<template>
  <div class="empty-state">
    <span class="empty-symbol"><Icon name="search" :size="26" /></span>
    <h1>Page not found</h1>
    <RouterLink class="button primary" to="/overview"
      >Back to overview<Icon name="right" :size="14"
    /></RouterLink>
  </div>
</template>
