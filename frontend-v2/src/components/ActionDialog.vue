<script setup lang="ts">
import { computed, reactive, ref, watch, onUnmounted, nextTick } from "vue";
import { useRouter } from "vue-router";
import AccessResult from "./AccessResult.vue";
import AppDialog from "./AppDialog.vue";
import FieldInput from "./FieldInput.vue";
import Facts from "./Facts.vue";
import type { Action, RecordData } from "@/resources/types";
import { normalizeInput, validateFields } from "@/resources/types";
import { write, read } from "@/data/gateway";
import { notify } from "@/data/session";
import { errorMessage, date } from "@/lib/format";
const props = defineProps<{ action: Action | undefined; data: RecordData }>(),
  emit = defineEmits<{ close: []; done: [unknown] }>(),
  router = useRouter();
const accessResult = ref<RecordData>();
let nextPath: string | undefined;
const input = reactive<RecordData>({}),
  preview = ref<RecordData>(),
  busy = ref(false),
  error = ref(""),
  confirmed = ref(false),
  now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;
watch(
  () => props.action,
  (action) => {
    Object.keys(input).forEach((k) => delete input[k]);
    Object.assign(
      input,
      JSON.parse(JSON.stringify(action?.defaults?.(props.data) || {})),
    );
    input.reason = "";
    input.page = 0;
    input._idempotencyKey = crypto.randomUUID();
    preview.value = undefined;
    error.value = "";
    confirmed.value = false;
    if (timer) clearInterval(timer);
    if (action) timer = setInterval(() => (now.value = Date.now()), 1000);
  },
  { immediate: true },
);
watch(
  input,
  () => {
    preview.value = undefined;
    confirmed.value = false;
  },
  { deep: true },
);
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
const expired = computed(
  () =>
    preview.value?.expiresAt &&
    Date.parse(preview.value.expiresAt) <= now.value,
);
const blockers = computed(
  () =>
    preview.value?.blockers ||
    preview.value?.issues?.filter(
      (x: RecordData) => x.severity !== "WARNING",
    ) ||
    [],
);
const blocked = computed(
  () =>
    !!expired.value ||
    preview.value?.allowed === false ||
    preview.value?.eligible === false ||
    preview.value?.activatable === false ||
    preview.value?.schedulable === false ||
    preview.value?.ready === false ||
    preview.value?.valid === false ||
    preview.value?.deletable === false ||
    preview.value?.confirmable === false ||
    preview.value?.reviewInvalidated === true ||
    preview.value?.executionSupported === false ||
    blockers.value.length > 0,
);
function payload() {
  return {
    _idempotencyKey: input._idempotencyKey,
    ...(props.action?.paginated ? { page: input.page } : {}),
    ...normalizeInput(input, props.action?.fields || []),
    ...(props.action?.reason === false
      ? {}
      : { reason: String(input.reason || "").trim() }),
  };
}
async function submit() {
  const action = props.action;
  if (!action) return;
  error.value = "";
  const issue = validateFields(input, action.fields || []);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  try {
    if (action.preview && !preview.value) {
      preview.value = (await read(action.permission, () =>
        action.preview!(props.data, payload()),
      )) as RecordData;
      return;
    }
    if (!confirmed.value || blocked.value) return;
    const result = await write(action.permission, () =>
      action.execute(props.data, payload(), preview.value),
    );
    notify(action.label + " completed.");
    nextPath = action.destination?.(result as RecordData, props.data);
    emit("done", result);
    if (
      (result && (result as RecordData).temporaryPassword) ||
      (result as RecordData)?.emailDelivery ||
      (result as RecordData)?.failures
    ) {
      accessResult.value = result as RecordData;
      return;
    }
    emit("close");
    if (nextPath) await router.push(nextPath);
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
const evidencePage = computed(() =>
  props.action?.paginated
    ? preview.value?.accounts || preview.value
    : undefined,
);
async function evidencePageChange(page: number) {
  input.page = page;
  preview.value = undefined;
  await nextTick();
  await submit();
}
async function finishAccess() {
  accessResult.value = undefined;
  emit("close");
  if (nextPath) await router.push(nextPath);
}
</script>
<template>
  <AppDialog
    :open="!!action && !accessResult"
    :title="action?.label || 'Action'"
    wide
    @close="!busy && emit('close')"
    ><form @submit.prevent="submit">
      <p v-if="data.ids?.length">{{ data.ids.length }} selected records</p>
      <div class="editor-grid">
        <FieldInput
          v-for="field in action?.fields || []"
          :key="field.key + field.label"
          :field="field"
          :data="input"
        />
      </div>
      <label v-if="action?.reason !== false" class="field section-gap"
        >Reason<textarea
          v-model="input.reason"
          required
          minlength="5"
          maxlength="1000"
          rows="2"
        />
      </label>
      <div v-if="preview" class="action-preview">
        <h3>Review</h3>
        <Facts :data="preview" />
        <div v-if="evidencePage?.totalPages > 1" class="row section-gap">
          <button
            type="button"
            class="button"
            :disabled="busy || evidencePage.page === 0"
            @click="evidencePageChange(evidencePage.page - 1)"
          >
            Previous</button
          ><span
            >Page {{ evidencePage.page + 1 }} of
            {{ evidencePage.totalPages }}</span
          ><button
            type="button"
            class="button"
            :disabled="busy || evidencePage.last"
            @click="evidencePageChange(evidencePage.page + 1)"
          >
            Next
          </button>
        </div>
        <p v-if="expired" class="notice error">
          Review expired. Close and review again.
        </p>
      </div>
      <p v-if="error" class="notice error section-gap" role="alert">
        {{ error }}
      </p>
      <label
        v-if="!action?.readOnly && (!action?.preview || preview)"
        class="acknowledgement section-gap"
        ><input v-model="confirmed" type="checkbox" />Confirm
        {{ action?.label.toLowerCase() }}</label
      >
      <div class="dialog-actions">
        <button
          type="button"
          class="button"
          :disabled="busy"
          @click="emit('close')"
        >
          Cancel</button
        ><button
          v-if="!action?.readOnly || !preview"
          class="button"
          :class="action?.destructive ? 'danger' : 'primary'"
          :disabled="
            busy ||
            blocked ||
            (!action?.readOnly && (!action?.preview || preview) && !confirmed)
          "
        >
          {{
            busy
              ? "Processing…"
              : action?.preview && !preview
                ? "Review"
                : action?.label
          }}
        </button>
      </div>
    </form></AppDialog
  ><AccessResult :result="accessResult" @close="finishAccess" />
</template>
