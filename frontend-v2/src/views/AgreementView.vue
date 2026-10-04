<script setup lang="ts">
import { computed, ref, reactive, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import CustomerSubscriptionEditor from "@/components/CustomerSubscriptionEditor.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import AppDialog from "@/components/AppDialog.vue";
import { useDraftGuard } from "@/composables/useDraftGuard";
import PageHeading from "@/components/PageHeading.vue";
import ResourceState from "@/components/ResourceState.vue";
import FieldInput from "@/components/FieldInput.vue";
import Facts from "@/components/Facts.vue";
import ActionDialog from "@/components/ActionDialog.vue";
import type { Field, Action, RecordData } from "@/resources/types";
import { normalizeInput, validateFields } from "@/resources/types";
import { adminApi as api } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { read, write } from "@/data/gateway";
import { can } from "@/data/session";
import { useResource } from "@/composables/useResource";
import { agreementActions } from "@/resources/billing";
import {
  planField,
  addOnField,
  priceField,
  featureField,
  quotaResourceField,
  select,
  dt,
  decimal,
  num,
  text,
  currencies,
} from "@/resources/fields";
import { errorMessage, money, date, label } from "@/lib/format";
import { safeReturnTo, contextualPath } from "@/lib/navigation";
const route = useRoute(),
  router = useRouter(),
  accountId = computed(() => String(route.params.accountId)),
  id = computed(() => String(route.params.id)),
  creating = computed(() => id.value === "new");
function hasAmount(value: string) {
  return !/^0+(?:\.0+)?$/.test(value);
}
const loaded = useResource(
  () =>
    creating.value
      ? Promise.resolve(undefined)
      : read(p.subscriptionsReadSpecialAgreement, () =>
          api.specialAgreement(accountId.value, id.value),
        ),
  [accountId, id],
);
const data = computed(() =>
  loaded.data.value
    ? { ...loaded.data.value.summary, ...loaded.data.value }
    : undefined,
);
const action = ref<Action>();
const busy = ref(false),
  error = ref(""),
  preview = ref<RecordData>(),
  confirmed = ref(false);
const fields: Field[] = [
  dt("startsAt", "Starts", true),
  dt("endsAt", "Ends", true),
  select("pricingMode", "Pricing", [
    "CATALOGUE_TOTAL",
    "CUSTOM_TOTAL",
    "COMPLIMENTARY",
  ]),
  {
    ...decimal("customTotal", "Total", true),
    show: (d) => d.pricingMode === "CUSTOM_TOTAL",
  },
  select("currencyCode", "Currency", currencies),
  {
    ...select("settlementMode", "Planned settlement", ["MANUAL", "NONE"]),
    options: [
      { value: "MANUAL", label: "Record awaiting settlement" },
      { value: "NONE", label: "No amount due" },
    ],
  },
  select("endInstruction", "At end", [
    "CONTINUE_REVIEWED_TERMS",
    "RESTORE_PREVIOUS_TERMS",
    "END_ACCESS",
    "MANUAL_REVIEW",
  ]),
  {
    ...select(
      "followOnPricingMode",
      "Follow-on pricing",
      ["CATALOGUE_TOTAL", "CUSTOM_TOTAL", "COMPLIMENTARY"],
      true,
    ),
    show: (d) => d.endInstruction === "CONTINUE_REVIEWED_TERMS",
  },
  {
    ...decimal("followOnCustomAmount", "Follow-on amount", true),
    show: (d) =>
      d.endInstruction === "CONTINUE_REVIEWED_TERMS" &&
      d.followOnPricingMode === "CUSTOM_TOTAL",
  },
  {
    key: "quotaBonuses",
    label: "Quota bonuses",
    type: "array",
    fields: [
      featureField(),
      quotaResourceField(),
      num("quantity", "Quantity", true, 1),
    ],
    full: true,
  },
];
const form = reactive<RecordData>({
    selection: {
      targetPlanCode: "",
      addOnCodes: [],
      quotaPackages: [],
      timing: "IMMEDIATE",
      planPriceSelection: null,
    },
    startsAt: "",
    endsAt: "",
    pricingMode: "CATALOGUE_TOTAL",
    customTotal: null,
    currencyCode: "MAD",
    settlementMode: "MANUAL",
    endInstruction: "RESTORE_PREVIOUS_TERMS",
    followOnPricingMode: null,
    followOnCustomAmount: null,
    quotaBonuses: [],
  }),
  reason = ref("");
watch(
  () => form.selection.planPriceSelection?.currencyCode,
  (value) => {
    if (value) form.currencyCode = value;
  },
);
const step = ref(1),
  validSelection = ref(false);
const returnPath = computed(
  () =>
    safeReturnTo(route.query.returnTo) ||
    `/customers/${accountId.value}?view=agreements`,
);
const canCreate = computed(
  () =>
    can(p.subscriptionsPreviewSpecialAgreement) &&
    can(p.subscriptionsCreateSpecialAgreement) &&
    can(p.subscriptionsChooseChangeOptions),
);
const activeFields = computed(() =>
  fields.filter((field) => !field.show || field.show(form)),
);
const termFields = computed(() =>
  activeFields.value.filter(
    (field) => !["quotaBonuses", "currencyCode"].includes(field.key),
  ),
);
const instructionDescription = computed(
  () =>
    ({
      CONTINUE_REVIEWED_TERMS:
        "Continue the reviewed plan, add-ons and capacity at the follow-on recurring amount.",
      RESTORE_PREVIOUS_TERMS:
        "Restore the customer’s terms held before this agreement.",
      END_ACCESS: "End subscription access when this agreement expires.",
      MANUAL_REVIEW:
        "Hold the agreement for operator review at the end of the term.",
    })[form.endInstruction as string] || "",
);
watch(
  () => form.endInstruction,
  (value) => {
    if (value !== "CONTINUE_REVIEWED_TERMS") {
      form.followOnPricingMode = null;
      form.followOnCustomAmount = null;
    } else form.followOnPricingMode ||= "CATALOGUE_TOTAL";
  },
);
watch(
  () => form.pricingMode,
  (value) => {
    if (value !== "CUSTOM_TOTAL") form.customTotal = null;
    if (value === "COMPLIMENTARY") form.settlementMode = "NONE";
    else if (form.settlementMode === "NONE") form.settlementMode = "MANUAL";
  },
);
watch(
  () => form.followOnPricingMode,
  (value) => {
    if (value !== "CUSTOM_TOTAL") form.followOnCustomAmount = null;
  },
);
watch(
  form,
  () => {
    preview.value = undefined;
    confirmed.value = false;
  },
  { deep: true },
);
watch(reason, () => (confirmed.value = false));
const draft = useDraftGuard(() => ({ form, reason: reason.value }));
const now = ref(Date.now()),
  timer = setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => clearInterval(timer));
const blocked = computed(
  () =>
    !!preview.value &&
    (!preview.value.confirmable ||
      Date.parse(preview.value.expiresAt) <= now.value),
);
async function submit() {
  if (reason.value.trim().length < 5) {
    error.value = "Enter an audit reason of at least five characters.";
    return;
  }
  if (!validSelection.value) {
    error.value = "Choose valid subscription terms.";
    return;
  }
  const issue = validateFields(form, fields);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (preview.value && step.value === 3) {
      if (!confirmed.value || blocked.value) return;
      const result = await write(p.subscriptionsCreateSpecialAgreement, () =>
        api.createSpecialAgreement(accountId.value, {
          definition: {
            ...normalizeInput(form, fields),
            selection: form.selection,
          } as any,
          previewToken: preview.value!.previewToken,
          reason: reason.value.trim(),
        }),
      );
      draft.saved();
      await router.push(
        contextualPath(
          `/customers/${accountId.value}/agreements/${result.agreement.summary.id}`,
          route,
        ),
      );
    } else {
      const definition = { ...form, ...normalizeInput(form, fields) };
      definition.selection = { ...form.selection, ...definition.selection };
      preview.value = await read(p.subscriptionsPreviewSpecialAgreement, () =>
        api.previewSpecialAgreement(accountId.value, definition as any),
      );
      step.value = 3;
    }
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <RouterLink class="back-link" :to="returnPath"
    >Customer agreements</RouterLink
  >
  <PageHeading
    :title="
      creating
        ? 'New agreement'
        : data?.accountName
          ? data.accountName + ' agreement'
          : 'Special agreement'
    "
  >
    <StatusBadge v-if="data" :status="data.status" />
    <button
      v-for="a in data
        ? agreementActions.filter(
            (a) =>
              a.key !== 'settle' && can(a.permission) && a.visible?.(data!),
          )
        : []"
      :key="a.key"
      class="button"
      @click="action = a"
    >
      {{ a.label }}
    </button>
  </PageHeading>
  <ResourceState
    :loading="loaded.loading.value"
    :error="loaded.error.value"
    @retry="loaded.refresh()"
  >
    <div v-if="creating && !canCreate" class="notice warning">
      Your role needs agreement preview, creation and subscription-options
      access.
    </div>
    <form v-else-if="creating" @submit.prevent="submit">
      <nav class="agreement-steps" aria-label="Agreement progress">
        <button
          v-for="(title, index) in [
            'Configuration',
            'Term and pricing',
            'Review',
          ]"
          :key="title"
          type="button"
          :disabled="index + 1 > step || busy"
          :aria-current="index + 1 === step ? 'step' : undefined"
          :class="{ current: index + 1 === step }"
          @click="step = index + 1"
        >
          {{ index + 1 }}. {{ title }}
        </button>
      </nav>
      <section class="panel">
        <div class="panel-header">
          <h2>
            {{
              step === 1
                ? "Subscription terms"
                : step === 2
                  ? "Term and pricing"
                  : "Review agreement"
            }}
          </h2>
        </div>
        <div class="panel-body">
          <div v-show="step === 1">
            <CustomerSubscriptionEditor
              :account-id="accountId"
              :model="form.selection"
              @ready="validSelection = $event"
            />
            <div class="section-gap">
              <FieldInput
                :field="fields.find((field) => field.key === 'quotaBonuses')!"
                :data="form"
              />
            </div>
          </div>
          <template v-if="step === 2"
            ><div class="editor-grid">
              <FieldInput
                v-for="field in termFields"
                :key="field.key"
                :field="field"
                :data="form"
              />
            </div>
            <p class="small muted section-gap">
              Currency: {{ form.currencyCode }} · {{ instructionDescription }}
            </p>
            <p class="notice section-gap">
              Paid agreements are recorded awaiting settlement. Payment
              collection and settlement actions are not available yet.
            </p>
            <label class="field section-gap"
              >Reason<textarea
                v-model="reason"
                required
                minlength="5"
                maxlength="2000"
              /></label
          ></template>
          <template v-if="step === 3 && preview">
            <p v-if="Date.parse(preview.expiresAt) <= now" class="notice error">
              Review expired. Return to the term and review again.
            </p>
            <p v-else class="small muted">
              Reviewed {{ date(preview.evaluatedAt, true) }} · expires
              {{ date(preview.expiresAt, true) }}
            </p>
            <table class="data-table section-gap">
              <tbody>
                <tr>
                  <th>Period</th>
                  <td>
                    {{ date(preview.startsAt, true) }} –
                    {{ date(preview.endsAt, true) }}
                  </td>
                </tr>
                <tr>
                  <th>Agreed total</th>
                  <td>
                    {{ money(preview.agreedTermAmount, preview.currencyCode) }}
                    · {{ label(preview.pricingMode) }}
                  </td>
                </tr>
                <tr>
                  <th>Catalog term value</th>
                  <td>
                    {{
                      preview.catalogueTermAmount === null
                        ? "Partial billing cycle"
                        : money(
                            preview.catalogueTermAmount,
                            preview.currencyCode,
                          )
                    }}
                  </td>
                </tr>
                <tr>
                  <th>After the agreement</th>
                  <td>
                    {{ instructionDescription
                    }}<span v-if="preview.followOnAmount !== null">
                      ·
                      {{ money(preview.followOnAmount, preview.currencyCode) }}
                      recurring</span
                    >
                  </td>
                </tr>
                <tr>
                  <th>Result on creation</th>
                  <td>
                    {{
                      hasAmount(preview.agreedTermAmount)
                        ? "Awaiting settlement; terms will not start before confirmation"
                        : Date.parse(preview.startsAt) > now
                          ? "Scheduled for the start date"
                          : "Active after creation"
                    }}
                  </td>
                </tr>
                <tr>
                  <th>Reason</th>
                  <td>{{ reason }}</td>
                </tr>
              </tbody>
            </table>
            <h3 class="section-gap">Entitlements</h3>
            <table class="data-table">
              <thead>
                <tr>
                  <th>Terms</th>
                  <th>Current</th>
                  <th>Agreement</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Plan</td>
                  <td>{{ preview.currentEntitlements.planCode }}</td>
                  <td>{{ preview.termEntitlements.planCode }}</td>
                </tr>
                <tr>
                  <td>Add-ons</td>
                  <td>
                    {{
                      preview.currentEntitlements.addOnCodes
                        .map(label)
                        .join(", ") || "None"
                    }}
                  </td>
                  <td>
                    {{
                      preview.termEntitlements.addOnCodes
                        .map(label)
                        .join(", ") || "None"
                    }}
                  </td>
                </tr>
                <tr>
                  <td>Capacity</td>
                  <td>
                    {{
                      preview.currentEntitlements.quotaPackages
                        .map(
                          (q: any) => label(q.packageCode) + " × " + q.quantity,
                        )
                        .join(", ") || "None"
                    }}
                  </td>
                  <td>
                    {{
                      preview.termEntitlements.quotaPackages
                        .map(
                          (q: any) => label(q.packageCode) + " × " + q.quantity,
                        )
                        .join(", ") || "None"
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
            <p
              v-for="conflict in preview.conflicts"
              :key="conflict.code"
              class="notice error section-gap"
            >
              {{ conflict.message }}
            </p>
            <label class="acknowledgement section-gap"
              ><input v-model="confirmed" type="checkbox" />I reviewed the term,
              amount, entitlements and end instruction.</label
            >
          </template>
          <p v-if="error" class="notice error section-gap" role="alert">
            {{ error }}
          </p>
        </div>
        <div class="dialog-actions agreement-footer">
          <button
            v-if="step > 1"
            type="button"
            class="button"
            :disabled="busy"
            @click="
              step--;
              confirmed = false;
            "
          >
            Back</button
          ><button
            v-if="step === 1"
            type="button"
            class="button primary"
            :disabled="!validSelection || busy"
            @click="step = 2"
          >
            Continue</button
          ><button
            v-else
            class="button primary"
            :disabled="busy || (step === 3 && (!confirmed || blocked))"
          >
            {{
              busy
                ? "Processing…"
                : step === 3
                  ? "Create agreement"
                  : "Review agreement"
            }}
          </button>
        </div>
      </section>
    </form>
    <template v-else-if="data">
      <section class="panel">
        <div class="panel-header">
          <h2>{{ data.planName }}</h2>
          <span class="small muted">{{ data.planCode }}</span>
        </div>
        <div class="panel-body">
          <dl class="detail-list">
            <div>
              <dt>Period</dt>
              <dd>
                {{ date(data.startsAt, true) }} – {{ date(data.endsAt, true) }}
              </dd>
            </div>
            <div>
              <dt>Agreed total</dt>
              <dd>
                {{ money(data.agreedTermAmount, data.currencyCode) }} ·
                {{ label(data.pricingMode) }}
              </dd>
            </div>
            <div>
              <dt>After agreement</dt>
              <dd>
                {{ label(data.endInstruction)
                }}<span v-if="data.followOnAmount !== null">
                  ·
                  {{ money(data.followOnAmount, data.currencyCode) }}
                  recurring</span
                >
              </dd>
            </div>
            <div>
              <dt>Add-ons</dt>
              <dd>
                {{ data.addOns?.map((a: any) => a.name).join(", ") || "None" }}
              </dd>
            </div>
            <div>
              <dt>Capacity</dt>
              <dd>
                {{
                  data.quotaPackages
                    ?.map((q: any) => q.name + " × " + q.quantity)
                    .join(", ") || "None"
                }}
              </dd>
            </div>
            <div>
              <dt>Reason</dt>
              <dd>{{ data.reason }}</dd>
            </div>
            <div>
              <dt>Created by</dt>
              <dd class="identifier">{{ data.createdByUserId }}</dd>
            </div>
            <div v-if="data.attentionReason">
              <dt>Needs attention</dt>
              <dd>{{ data.attentionReason }}</dd>
            </div>
            <div v-if="data.cancellationReason">
              <dt>Cancellation reason</dt>
              <dd>{{ data.cancellationReason }}</dd>
            </div>
          </dl>
          <p
            v-if="data.status === 'AWAITING_SETTLEMENT'"
            class="notice warning section-gap"
          >
            This agreement is awaiting settlement. Payment actions are not
            available yet.
          </p>
          <details class="section-gap">
            <summary>Recorded evidence</summary>
            <Facts :data="data" />
          </details>
        </div>
      </section>
    </template>
  </ResourceState>
  <ActionDialog
    :action="action"
    :data="data || {}"
    @close="action = undefined"
    @done="loaded.refresh()"
  />
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
<style scoped>
.agreement-steps {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 18px;
  margin-bottom: 24px;
}
.agreement-steps button {
  background: transparent;
  border: 0;
  color: var(--muted);
  padding: 6px 0;
}
.agreement-steps .current {
  color: var(--text);
  font-weight: 600;
}
.agreement-footer {
  padding: 18px 24px;
  border-top: 1px solid var(--border);
}
</style>
