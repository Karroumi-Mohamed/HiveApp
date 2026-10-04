<script setup lang="ts">
defineProps<{ title: string }>();
</script>
<template>
  <header class="page-heading">
    <div>
      <h1>{{ title }}</h1>
    </div>
    <div class="heading-actions"><slot /></div>
  </header>
</template>
