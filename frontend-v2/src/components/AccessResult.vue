<script setup lang="ts">
import { computed, ref, watch } from "vue";
import AppDialog from "./AppDialog.vue";
import Facts from "./Facts.vue";
import type { RecordData } from "@/resources/types";
const props = defineProps<{ result: RecordData | undefined }>(),
  emit = defineEmits<{ close: [] }>();
const copied = ref(false),
  copying = ref(false),
  copyError = ref("");
watch(
  () => props.result,
  () => {
    copied.value = false;
    copying.value = false;
    copyError.value = "";
  },
);
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
  const result = props.result;
  if (!result?.temporaryPassword || copying.value) return;
  copying.value = true;
  copyError.value = "";
  try {
    await navigator.clipboard.writeText(result.temporaryPassword);
    if (props.result === result) copied.value = true;
  } catch {
    if (props.result === result) {
      copied.value = false;
      copyError.value = "Could not copy. Select and copy the password.";
    }
  } finally {
    if (props.result === result) copying.value = false;
  }
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
      <button class="button" :disabled="copying" @click="copy">
        {{ copying ? "Copying…" : copied ? "Copied" : "Copy password" }}
      </button>
      <p v-if="copyError" class="notice error" role="alert">
        {{ copyError }}
      </p></template
    ><Facts :data="details" />
    <div class="dialog-actions">
      <button class="button primary" @click="emit('close')">Done</button>
    </div></AppDialog
  >
</template>
