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
import { safeReturnTo, contextualPath } from "@/lib/navigation";
import ExecutionSummary from "@/components/ExecutionSummary.vue";
import ExecutionResults from "@/components/ExecutionResults.vue";
const route = useRoute(),
  router = useRouter(),
  kind = computed(() => String(route.meta.kind));
const form = reactive<RecordData>({
  sourcePriceId: route.query.price || null,
  sourcePlanId: route.query.sourcePlan || null,
  audience: "ALL",
  scope: "VERSION",
  accountIds: [],
  excludedAccountIds: [],
  statuses: [],
  application: { timing: "AT_RENEWAL", notBefore: null, reason: "" },
  notificationPolicy: "IN_APP",
  email: false,
  notBefore: null,
  targetPlanId: route.query.targetPlan || route.query.plan || null,
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
const stage = ref(0);
const stages = ["Revisions and prices", "Customers", "Timing and notices"];
const sourceKeys = [
  "sourcePriceId",
  "targetPriceId",
  "sourcePlanId",
  "targetPlanId",
  "scope",
];
const audienceKeys = [
  "audience",
  "accountIds",
  "excludedAccountIds",
  "planId",
  "segmentId",
  "subscriptionStatus",
  "statuses",
  "search",
  "currency",
  "billingCycle",
];
const fields = computed(() =>
  executionFields[kind.value]!.map((field) => {
    if (
      kind.value !== "rollouts" ||
      field.key !== "sourcePlanId" ||
      !form.targetPlanId ||
      !can(p.plansListVersions)
    )
      return field;
    return {
      ...field,
      label: "Source revision in this family",
      contextKeys: ["targetPlanId"],
      load: async (_search: string, page: number) => {
        const result = await read(p.plansListVersions, () =>
          adminApi.planVersions(form.targetPlanId, { page, size: 20 }),
        );
        return {
          options: result.versions.content
            .filter((plan) => plan.id !== form.targetPlanId)
            .map((plan) => ({
              value: plan.id,
              label: plan.name + " · revision " + plan.revisionNumber,
            })),
          totalPages: result.versions.totalPages,
        };
      },
    };
  }),
);
const currentFields = computed(() =>
  fields.value.filter((field) =>
    stage.value === 0
      ? sourceKeys.includes(field.key)
      : stage.value === 1
        ? audienceKeys.includes(field.key)
        : !sourceKeys.includes(field.key) && !audienceKeys.includes(field.key),
  ),
);
const preview = ref<RecordData>(),
  confirmed = ref(false),
  busy = ref(false),
  error = ref("");
async function submit() {
  if (!preview.value && stage.value < 2) {
    const issue = validateFields(form, currentFields.value);
    if (issue) {
      error.value = issue;
      return;
    }
    stage.value++;
    error.value = "";
    return;
  }
  const issue = validateFields(form, fields.value);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (preview.value) {
      if (!confirmed.value) return;
      if (!can(p.repricingConfirm))
        throw Error(
          "You can review repricing. Confirmation requires additional access.",
        );
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
      await router.push(
        contextualPath(
          "/operations/repricing/" + preview.value.summary.id,
          route,
        ),
      );
      return;
    }
    const input = normalizeInput(form, fields.value);
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
      await router.push(
        contextualPath("/operations/rollouts/" + result.summary.id, route),
      );
    }
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <RouterLink
    class="back-link"
    :to="safeReturnTo(route.query.returnTo) || '/catalog'"
    >Back to previous view</RouterLink
  ><PageHeading
    :title="
      kind === 'repricing' ? 'Reprice subscriptions' : 'Apply plan revision'
    "
  />
  <nav v-if="!preview" class="flow-steps" aria-label="Execution setup">
    <button
      v-for="(title, index) in stages"
      :key="title"
      type="button"
      :disabled="busy || index > stage"
      :aria-current="stage === index ? 'step' : undefined"
      :class="{ active: stage === index }"
      @click="stage = index"
    >
      {{ index + 1 }}. {{ title }}
    </button>
  </nav>
  <form @submit.prevent="submit">
    <div v-if="!preview" class="editor-grid">
      <FieldInput
        v-for="field in currentFields"
        :key="field.key + field.label"
        :field="field"
        :data="form"
      />
    </div>
    <template v-else
      ><ExecutionSummary :data="preview" kind="repricing" /><ExecutionResults
        :id="preview.summary.id"
        kind="repricing"
      />
      <details class="evidence-disclosure">
        <summary>Review evidence</summary>
        <Facts :data="preview" />
      </details>
      <p v-if="!can(p.repricingConfirm)" class="notice">
        You can review repricing. Confirmation requires additional access.
      </p>
      <label v-if="can(p.repricingConfirm)" class="acknowledgement section-gap"
        ><input v-model="confirmed" type="checkbox" />Confirm repricing</label
      ></template
    >
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div class="dialog-actions">
      <button
        v-if="!preview && stage > 0"
        type="button"
        class="button"
        :disabled="busy"
        @click="stage--"
      >
        Back
      </button>
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
            (!can(p.repricingConfirm) ||
              !confirmed ||
              Date.parse(preview.expiresAt) <= now ||
              !preview.summary.readyCount))
        "
      >
        {{
          busy
            ? "Processing…"
            : preview
              ? "Confirm"
              : stage < 2
                ? "Continue"
                : kind === "rollouts"
                  ? "Assess customers"
                  : "Review impact"
        }}
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
