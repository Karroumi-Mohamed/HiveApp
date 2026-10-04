<script setup lang="ts">
import { computed, ref } from "vue";
import AppDialog from "./AppDialog.vue";
import Facts from "./Facts.vue";
import type { RecordData } from "@/resources/types";
const props = defineProps<{ result: RecordData | undefined }>(),
  emit = defineEmits<{ close: [] }>();
const copied = ref(false);
const details = computed(() =>
  props.result
    ? Object.fromEntries(
        Object.entries(props.result).filter(
          ([key]) => !["temporaryPassword", "operator"].includes(key),
        ),
      )
    : {},
);
async function copy() {
  await navigator.clipboard.writeText(props.result!.temporaryPassword);
  copied.value = true;
}
</script>
<template>
  <AppDialog
    :open="!!result"
    :title="
      result?.operator || result?.method || result?.temporaryPassword
        ? 'Operator access'
        : 'Results'
    "
    @close="emit('close')"
    ><template v-if="result?.temporaryPassword"
      ><p>This password is shown once.</p>
      <p class="credential-output">{{ result.temporaryPassword }}</p>
      <button class="button" @click="copy">
        {{ copied ? "Copied" : "Copy password" }}
      </button></template
    ><Facts :data="details" />
    <div class="dialog-actions">
      <button class="button primary" @click="emit('close')">Done</button>
    </div></AppDialog
  >
</template>
