<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import Icon from "./Icon.vue";
const props = defineProps<{ open: boolean; title: string; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
watch(
  () => props.open,
  async (open) => {
    await nextTick();
    if (open && !dialog.value?.open) dialog.value?.showModal();
    else if (!open && dialog.value?.open) dialog.value?.close();
  },
  { immediate: true },
);
</script>
<template>
  <dialog
    ref="dialog"
    class="app-dialog"
    :class="{ wide }"
    @cancel.prevent="emit('close')"
    @click="
      (event) => {
        if (event.target === dialog) emit('close');
      }
    "
    :aria-label="title"
  >
    <div class="dialog-header">
      <h2>{{ title }}</h2>
      <button
        class="icon-button"
        aria-label="Close dialog"
        @click="emit('close')"
      >
        <Icon name="close" />
      </button>
    </div>
    <div class="dialog-body"><slot /></div>
  </dialog>
</template>
