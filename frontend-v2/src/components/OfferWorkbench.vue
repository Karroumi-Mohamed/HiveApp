<script setup lang="ts">
import { computed, reactive, ref, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import FieldInput from "./FieldInput.vue";
import AppDialog from "./AppDialog.vue";
import StatusBadge from "./StatusBadge.vue";
import { useDraftGuard } from "@/composables/useDraftGuard";
import { adminOfferApi as offers } from "@/api/admin-offer-api";
import type {
  OfferDefinitionPreview,
  OfferClientProduct,
} from "@/api/offer-contracts";
import { adminPermissions as p } from "@/auth/permissions";
import { can, session, invalidate } from "@/data/session";
import { read } from "@/data/gateway";
import { date, label, money, errorMessage } from "@/lib/format";
import { withReturnTo } from "@/lib/navigation";
import { offerFields, offerDefinitionInput } from "@/resources/commercial";
import { normalizeInput, validateFields } from "@/resources/types";
import type { Resource, RecordData } from "@/resources/types";
const props = defineProps<{
  resource: Resource;
  id?: string;
  data?: RecordData;
  creating: boolean;
}>();
const emit = defineEmits<{ saved: [result: RecordData]; cancel: [] }>();
const route = useRoute(),
  router = useRouter();
const form = reactive<RecordData>({});
const step = ref(0),
  busy = ref(false),
  error = ref("");
const preview = ref<OfferDefinitionPreview>();
const restored = ref(false);
const draftStorageKey = computed(() =>
  props.creating && typeof route.query.offerDraft === "string"
    ? "hive-v2-offer-campaign-draft:" +
      session.me?.id +
      ":" +
      route.query.offerDraft
    : undefined,
);
let reviewedBody: RecordData | undefined;
const stages = [
  {
    title: "Campaign and dates",
    keys: ["name", "description", "campaignId", "startsAt", "endsAt"],
  },
  {
    title: "Products",
    keys: [
      "selection.planId",
      "selection.planPriceId",
      "selection.timing",
      "selection.addOns",
      "selection.quotaPackages",
    ],
  },
  {
    title: "Terms",
    keys: [
      "discovery",
      "acceptance",
      "customerCodeMode",
      "customerCode",
      "globalLimit",
      "perAccountLimit",
      "effects.discountType",
      "effects.discountAmount",
      "effects.percentage",
      "effects.percentageCap",
      "effects.finiteQuotaBonuses",
    ],
  },
  { title: "Review", keys: [] },
];
const fields = computed(() =>
  offerFields
    .filter((field) => stages[step.value]!.keys.includes(field.key))
    .map((field) =>
      field.key === "campaignId" ? { ...field, emptyLink: undefined } : field,
    ),
);
const previewPermission = computed(() =>
  props.creating
    ? p.offersPreviewCreateDefinition
    : p.offersPreviewUpdateDefinition,
);
const canReview = computed(() => can(previewPermission.value));
const canCreateCampaign = computed(() => can(p.campaignsCreate));
const draft = useDraftGuard(() => form);
watch(
  () => [props.id, props.data] as const,
  async () => {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(
      form,
      JSON.parse(JSON.stringify(props.resource.defaults || {})),
      JSON.parse(JSON.stringify(props.data || {})),
    );
    restored.value = false;
    if (draftStorageKey.value) {
      try {
        const stored = sessionStorage.getItem(draftStorageKey.value);
        if (stored) {
          Object.assign(form, JSON.parse(stored));
          restored.value = true;
        }
      } catch {
        /* The offer can still be created if browser draft storage is unavailable. */
      }
    }
    if (props.creating && typeof route.query.campaign === "string")
      form.campaignId = route.query.campaign;
    step.value = 0;
    preview.value = undefined;
    reviewedBody = undefined;
    await nextTick();
    if (!restored.value) draft.saved();
  },
  { immediate: true },
);
watch(
  () => JSON.stringify(form),
  () => {
    preview.value = undefined;
    reviewedBody = undefined;
    error.value = "";
    if (draftStorageKey.value && restored.value) {
      try {
        sessionStorage.setItem(draftStorageKey.value, draftSnapshot());
      } catch {
        /* Saving remains available through the backend. */
      }
    }
  },
);
function draftSnapshot() {
  const { customerCode, ...configuration } = form;
  return JSON.stringify(configuration);
}
async function createCampaign() {
  if (busy.value) return;
  busy.value = true;
  const token = crypto.randomUUID();
  const key = "hive-v2-offer-campaign-draft:" + session.me?.id + ":" + token;
  try {
    sessionStorage.setItem(key, draftSnapshot());
    const returnTo = new URL(route.fullPath, window.location.origin);
    returnTo.searchParams.set("offerDraft", token);
    draft.saved();
    await router.push(
      withReturnTo(
        "/commercial/campaigns/new",
        returnTo.pathname + returnTo.search,
      ),
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
function discard() {
  try {
    if (draftStorageKey.value) sessionStorage.removeItem(draftStorageKey.value);
  } catch {
    /* Browser storage must not prevent leaving the form. */
  }
  draft.answer(true);
}
function changeStep(index: number) {
  if (busy.value) return;
  if (index > step.value) {
    const issue = validateFields(form, fields.value);
    if (issue) {
      error.value = issue;
      return;
    }
  }
  error.value = "";
  step.value = index;
}
async function review() {
  if (busy.value) return;
  const issue = validateFields(form, offerFields);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const configuration = JSON.stringify(form);
    const id = props.creating ? undefined : props.id;
    const body = offerDefinitionInput(
      id,
      normalizeInput(form, offerFields),
      props.data,
    );
    const result = await read(previewPermission.value, () =>
      id
        ? offers.previewUpdateDefinition(id, body as any)
        : offers.previewCreateDefinition(body as any),
    );
    if (JSON.stringify(form) !== configuration) {
      error.value =
        "The offer changed during review. Review the updated definition.";
      return;
    }
    reviewedBody = JSON.parse(JSON.stringify(body));
    preview.value = result;
    step.value = 3;
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function save() {
  if (!preview.value?.valid || !reviewedBody || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    // Notify the host before refreshing resources can unmount this editor.
    const result = await read(
      props.creating ? p.offersCreate : p.offersUpdate,
      () =>
        props.creating
          ? offers.create(reviewedBody as any)
          : offers.update(props.id!, reviewedBody as any),
    );
    draft.saved();
    try {
      if (draftStorageKey.value)
        sessionStorage.removeItem(draftStorageKey.value);
    } catch {
      /* The backend save succeeded even if browser draft cleanup is unavailable. */
    }
    emit("saved", result);
    invalidate();
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
const products = computed(() =>
  preview.value?.resolvedSelection
    ? [
        { kind: "Plan", product: preview.value.resolvedSelection.plan },
        ...preview.value.resolvedSelection.addOns.map((product) => ({
          kind: "Add-on",
          product,
        })),
        ...preview.value.resolvedSelection.quotaPackages.map((product) => ({
          kind: "Capacity",
          product,
        })),
      ]
    : [],
);
const reviewedDiscount = computed(() => {
  const effect = form.effects;
  const currency = preview.value?.resolvedSelection?.plan.currencyCode;
  if (!effect || effect.discountType === "NONE") return "None";
  if (effect.discountType === "FIXED")
    return money(effect.discountAmount, currency);
  return effect.percentage + "% · cap " + money(effect.percentageCap, currency);
});
const reviewedLimits = computed(() => ({
  total:
    form.globalLimit == null || form.globalLimit === ""
      ? "Unlimited"
      : form.globalLimit,
  account:
    form.perAccountLimit == null || form.perAccountLimit === ""
      ? "Unlimited"
      : form.perAccountLimit,
}));
function price(product: OfferClientProduct) {
  return product.pricingMode === "FREE"
    ? "Complimentary"
    : money(product.amount, product.currencyCode) +
        " / " +
        label(product.billingCycle).toLowerCase();
}
function fixIssue(path: string) {
  step.value = path.startsWith("selection")
    ? 1
    : /^(effects|discovery|acceptance|customerCode|globalLimit|perAccountLimit|lineageTerms)/.test(
          path,
        )
      ? 2
      : 0;
}
</script>

<template>
  <section class="offer-workbench">
    <nav class="workbench-steps" aria-label="Offer setup">
      <button
        v-for="(stage, index) in stages"
        :key="stage.title"
        type="button"
        :class="{ current: step === index }"
        :aria-current="step === index ? 'step' : undefined"
        :disabled="busy || index > step || (index === 3 && !preview)"
        @click="changeStep(index)"
      >
        <span>{{ index + 1 }}</span
        >{{ stage.title }}
      </button>
    </nav>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <p v-if="restored" class="context-line" role="status">
      Offer draft restored.
    </p>
    <form
      v-if="step < 3"
      @submit.prevent="step < 2 ? changeStep(step + 1) : review()"
    >
      <div class="workbench-heading">
        <h2>{{ stages[step]!.title }}</h2>
        <span>{{ step + 1 }} of 4</span>
      </div>
      <p
        v-if="step === 0 && !creating && data?.campaign?.name"
        class="context-line"
      >
        Campaign: {{ data.campaign.name }}
      </p>
      <p
        v-if="step === 2 && !creating && data?.lineageTermsEditable === false"
        class="context-line"
      >
        Discovery, acceptance, private code and redemption limits are fixed for
        this offer lineage.
      </p>
      <fieldset class="form-grid workbench-fields" :disabled="busy">
        <FieldInput
          v-for="field in fields"
          :key="field.key"
          :field="field"
          :data="form"
        />
      </fieldset>
      <button
        v-if="step === 0 && creating && canCreateCampaign"
        type="button"
        class="text-link create-campaign"
        :disabled="busy"
        @click="createCampaign"
      >
        {{ busy ? "Opening campaign…" : "Create campaign" }}
      </button>
      <p v-if="step === 2 && !canReview" class="context-line">
        Definition review permission is required to save this offer.
      </p>
      <div class="workbench-footer">
        <button
          type="button"
          class="button"
          :disabled="busy"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <div>
          <button
            v-if="step > 0"
            type="button"
            class="button"
            :disabled="busy"
            @click="changeStep(step - 1)"
          >
            Back</button
          ><button
            class="button primary"
            :disabled="busy || (step === 2 && !canReview)"
          >
            {{ busy ? "Reviewing…" : step === 2 ? "Review offer" : "Continue" }}
          </button>
        </div>
      </div>
    </form>
    <div v-else-if="preview" class="offer-review">
      <div class="workbench-heading">
        <h2>{{ form.name }}</h2>
        <StatusBadge :status="preview.valid ? 'READY' : 'NEEDS_ATTENTION'" />
      </div>
      <div v-if="preview.issues.length" class="review-issues" role="alert">
        <h3>Resolve before saving</h3>
        <button
          v-for="(issue, index) in preview.issues"
          :key="index"
          type="button"
          @click="fixIssue(issue.fieldPath)"
        >
          {{ issue.message }} <span>Edit</span>
        </button>
      </div>
      <dl class="review-terms">
        <div>
          <dt>Campaign</dt>
          <dd>
            {{
              preview.campaign?.name || data?.campaign?.name || "Unavailable"
            }}
          </dd>
        </div>
        <div>
          <dt>Audience</dt>
          <dd>{{ label(preview.campaign?.audienceMode) }}</dd>
        </div>
        <div>
          <dt>Offer window</dt>
          <dd>
            {{ date(form.startsAt, true) }} — {{ date(form.endsAt, true) }}
          </dd>
        </div>
        <div>
          <dt>Campaign window</dt>
          <dd>
            {{ date(preview.campaign?.startsAt, true) }} —
            {{ date(preview.campaign?.endsAt, true) }}
          </dd>
        </div>
        <div>
          <dt>Applies</dt>
          <dd>{{ label(form.selection?.timing) }}</dd>
        </div>
        <div>
          <dt>Discovery / acceptance</dt>
          <dd>{{ label(form.discovery) }} / {{ label(form.acceptance) }}</dd>
        </div>
        <div>
          <dt>Private code</dt>
          <dd>
            {{
              creating
                ? form.customerCode
                  ? "Configured"
                  : "None"
                : form.customerCodeMode === "KEEP"
                  ? data?.customerCodeConfigured
                    ? "Keep existing code"
                    : "None"
                  : label(form.customerCodeMode)
            }}
          </dd>
        </div>
        <div>
          <dt>Redemption limits</dt>
          <dd>
            {{ reviewedLimits.total }} total · {{ reviewedLimits.account }} per
            customer
          </dd>
        </div>
      </dl>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Type</th>
              <th>Revision</th>
              <th>Quantity</th>
              <th>Terms</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="({ kind, product }, index) in products" :key="index">
              <td>
                {{ product.name }}<small>{{ product.code }}</small>
              </td>
              <td>{{ kind }}</td>
              <td>{{ product.revisionNumber }}</td>
              <td>{{ product.quantity ?? 1 }}</td>
              <td>{{ price(product) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <dl class="review-terms">
        <div>
          <dt>Discount</dt>
          <dd>{{ reviewedDiscount }}</dd>
        </div>
        <div
          v-for="(bonus, index) in form.effects?.finiteQuotaBonuses || []"
          :key="index"
        >
          <dt>{{ label(bonus.resource) }}</dt>
          <dd>+{{ bonus.quantity }} · {{ bonus.featureCode }}</dd>
        </div>
      </dl>
      <p class="context-line">
        Saving {{ creating ? "creates" : "updates" }} the draft. Publication is
        a separate action.
      </p>
      <div class="workbench-footer">
        <button
          type="button"
          class="button"
          :disabled="busy"
          @click="changeStep(2)"
        >
          Edit terms
        </button>
        <div>
          <button
            type="button"
            class="button"
            :disabled="busy"
            @click="emit('cancel')"
          >
            Cancel</button
          ><button
            type="button"
            class="button primary"
            :disabled="busy || !preview.valid"
            @click="save"
          >
            {{ busy ? "Saving…" : creating ? "Create offer" : "Save offer" }}
          </button>
        </div>
      </div>
    </div>
    <AppDialog
      :open="draft.open.value"
      title="Discard offer changes?"
      @close="draft.answer(false)"
      ><p>Your unsaved offer definition will be lost.</p>
      <div class="workbench-footer">
        <button class="button" @click="draft.answer(false)">Keep editing</button
        ><button class="button danger" @click="discard">Discard changes</button>
      </div></AppDialog
    >
  </section>
</template>

<style scoped>
.offer-workbench {
  max-width: 1100px;
}
.workbench-fields {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
.create-campaign {
  margin-top: 16px;
}
.workbench-steps {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--border);
  margin-bottom: 28px;
  overflow-x: auto;
}
.workbench-steps button {
  display: flex;
  align-items: center;
  gap: 9px;
  white-space: nowrap;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  padding: 15px 22px 15px 0;
  color: var(--muted);
}
.workbench-steps button.current {
  color: var(--text);
  border-bottom-color: var(--accent, #456c4a);
}
.workbench-steps span {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 12px;
}
.workbench-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
}
.workbench-heading h2 {
  margin: 0;
  font-size: 19px;
}
.workbench-heading > span,
.context-line {
  color: var(--muted);
  font-size: 13px;
}
.workbench-footer {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.workbench-footer > div {
  display: flex;
  gap: 10px;
}
.review-terms {
  margin: 18px 0 26px;
}
.review-terms > div {
  display: flex;
  gap: 20px;
  border-bottom: 1px solid var(--border);
  padding: 13px 0;
}
.review-terms dt {
  width: 190px;
  flex-shrink: 0;
  color: var(--muted);
}
.review-terms dd {
  margin: 0;
}
.review-issues {
  border-left: 3px solid #b57329;
  padding: 12px 16px;
  margin-bottom: 24px;
}
.review-issues h3 {
  font-size: 14px;
  margin: 0 0 8px;
}
.review-issues button {
  display: flex;
  justify-content: space-between;
  text-align: left;
  width: 100%;
  background: none;
  border: 0;
  padding: 8px 0;
  gap: 16px;
}
.review-issues span {
  text-decoration: underline;
}
td small {
  display: block;
  color: var(--muted);
  margin-top: 4px;
}
@media (max-width: 650px) {
  .review-terms > div {
    display: block;
  }
  .review-terms dt {
    margin-bottom: 6px;
  }
  .workbench-steps button {
    padding-right: 16px;
  }
  .workbench-footer {
    flex-wrap: wrap;
  }
}
</style>
