<script setup lang="ts">
import { computed, reactive, ref, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppDialog from "@/components/AppDialog.vue";
import { useDraftGuard } from "@/composables/useDraftGuard";
import PageHeading from "@/components/PageHeading.vue";
import FieldInput from "@/components/FieldInput.vue";
import Facts from "@/components/Facts.vue";
import { executionFields } from "@/resources/operations";
import { normalizeInput, validateFields } from "@/resources/types";
import type { RecordData } from "@/resources/types";
import { repricingApi } from "@/api/repricing-api";
import { planApplicationApi } from "@/api/plan-application-api";
import { adminApi } from "@/api/admin-api";
import { can } from "@/data/session";
import { write, read } from "@/data/gateway";
import { adminPermissions as p } from "@/auth/permissions";
import { errorMessage } from "@/lib/format";
const route = useRoute(),
  router = useRouter(),
  kind = computed(() => String(route.meta.kind));
const form = reactive<RecordData>({
  sourcePriceId: route.query.price || null,
  sourcePlanId: route.query.plan || null,
  audience: "ALL",
  scope: "VERSION",
  accountIds: [],
  excludedAccountIds: [],
  statuses: [],
  application: { timing: "AT_RENEWAL", notBefore: null, reason: "" },
  notificationPolicy: "IN_APP",
  email: false,
  notBefore: null,
  planId: null,
  segmentId: null,
  subscriptionStatus: null,
  reason: "",
});
if (kind.value === "repricing") form.audience = "TARIFF_HOLDERS";
let sourceRequest = 0;
watch(
  () => form.sourcePriceId,
  async (id) => {
    const current = ++sourceRequest;
    form._sourceProductId = null;
    if (kind.value !== "repricing" || !id || !can(p.priceBooksRead)) return;
    try {
      const price = await read(p.priceBooksRead, () =>
        adminApi.productPrice(id),
      );
      if (current === sourceRequest) form._sourceProductId = price.productId;
    } catch {
      /* The picker and review surface the authorized API error. */
    }
  },
  { immediate: true },
);
const draft = useDraftGuard(() =>
  Object.fromEntries(
    Object.entries(form).filter(([key]) => !key.startsWith("_")),
  ),
);
const now = ref(Date.now()),
  timer = setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => clearInterval(timer));
const preview = ref<RecordData>(),
  confirmed = ref(false),
  busy = ref(false),
  error = ref("");
async function submit() {
  const issue = validateFields(form, executionFields[kind.value]!);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (preview.value) {
      if (!confirmed.value) return;
      if (Date.parse(preview.value.expiresAt) <= now.value)
        throw Error("Review expired. Review again.");
      if (!preview.value.summary.readyCount)
        throw Error("No customers are ready.");
      await write(p.repricingConfirm, () =>
        repricingApi.confirm(
          preview.value!.summary.id,
          preview.value!.previewToken,
        ),
      );
      draft.saved();
      await router.push("/operations/repricing/" + preview.value.summary.id);
      return;
    }
    const input = normalizeInput(form, executionFields[kind.value]!);
    if (kind.value === "repricing") {
      preview.value = await write(p.repricingPreview, () =>
        repricingApi.preview(input as any),
      );
    } else {
      const result = await write(p.plansCreateApplication, () =>
        planApplicationApi.create(input.targetPlanId, {
          ...form,
          ...input,
        } as any),
      );
      draft.saved();
      await router.push("/operations/rollouts/" + result.summary.id);
    }
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <RouterLink class="back-link" to="/catalog">Catalog</RouterLink
  ><PageHeading
    :title="
      kind === 'repricing' ? 'Reprice subscriptions' : 'Apply plan revision'
    "
  />
  <form @submit.prevent="submit">
    <div v-if="!preview" class="editor-grid">
      <FieldInput
        v-for="field in executionFields[kind]"
        :key="field.key + field.label"
        :field="field"
        :data="form"
      />
    </div>
    <template v-else
      ><Facts :data="preview" /><label class="acknowledgement section-gap"
        ><input v-model="confirmed" type="checkbox" />Confirm repricing</label
      ></template
    >
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div class="dialog-actions">
      <button
        v-if="preview"
        type="button"
        class="button"
        @click="
          preview = undefined;
          confirmed = false;
        "
      >
        Edit</button
      ><button
        class="button primary"
        :disabled="
          busy ||
          (!!preview &&
            (!confirmed ||
              Date.parse(preview.expiresAt) <= now ||
              !preview.summary.readyCount))
        "
      >
        {{ busy ? "Processing…" : preview ? "Confirm" : "Review" }}
      </button>
    </div>
  </form>
  <AppDialog
    :open="draft.open.value"
    title="Discard changes?"
    @close="draft.answer(false)"
    ><div class="dialog-actions">
      <button class="button" @click="draft.answer(false)">Keep editing</button
      ><button class="button danger" @click="draft.answer(true)">
        Discard
      </button>
    </div></AppDialog
  >
</template>
