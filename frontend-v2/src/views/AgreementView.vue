<script setup lang="ts">
import { computed, ref, reactive, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import SubscriptionSelection from "@/components/SubscriptionSelection.vue";
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
import { errorMessage } from "@/lib/format";
const route = useRoute(),
  router = useRouter(),
  accountId = computed(() => String(route.params.accountId)),
  id = computed(() => String(route.params.id)),
  creating = computed(() => id.value === "new");
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
    ...decimal("customTotal", "Total"),
    show: (d) => d.pricingMode === "CUSTOM_TOTAL",
  },
  select("currencyCode", "Currency", currencies),
  select("settlementMode", "Settlement", ["PROVIDER", "MANUAL", "NONE"]),
  select("endInstruction", "At end", [
    "CONTINUE_REVIEWED_TERMS",
    "RESTORE_PREVIOUS_TERMS",
    "END_ACCESS",
    "MANUAL_REVIEW",
  ]),
  select(
    "followOnPricingMode",
    "Follow-on pricing",
    ["CATALOGUE_TOTAL", "CUSTOM_TOTAL", "COMPLIMENTARY"],
    false,
  ),
  {
    ...decimal("followOnCustomAmount", "Follow-on amount"),
    show: (d) => d.followOnPricingMode === "CUSTOM_TOTAL",
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
  const issue = validateFields(form, fields);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    if (preview.value) {
      if (!confirmed.value || blocked.value) return;
      const result = await write(p.subscriptionsCreateSpecialAgreement, () =>
        api.createSpecialAgreement(accountId.value, {
          definition: {
            ...normalizeInput(form, fields),
            selection: form.selection,
          } as any,
          previewToken: preview.value!.previewToken,
          reason: reason.value,
        }),
      );
      draft.saved();
      await router.push(
        "/customers/" +
          accountId.value +
          "/agreements/" +
          result.agreement.summary.id,
      );
    } else {
      const definition = { ...form, ...normalizeInput(form, fields) };
      definition.selection = { ...form.selection, ...definition.selection };
      preview.value = await read(p.subscriptionsPreviewSpecialAgreement, () =>
        api.previewSpecialAgreement(accountId.value, definition as any),
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
    :to="'/customers/' + accountId + '?view=agreements'"
    >Customer agreements</RouterLink
  ><PageHeading :title="creating ? 'New agreement' : 'Special agreement'"
    ><button
      v-for="a in data
        ? agreementActions.filter(
            (a) => can(a.permission) && a.visible?.(data!),
          )
        : []"
      class="button"
      @click="action = a"
    >
      {{ a.label }}
    </button></PageHeading
  ><ResourceState
    :loading="loaded.loading.value"
    :error="loaded.error.value"
    @retry="loaded.refresh()"
    ><form v-if="creating" @submit.prevent="submit">
      <SubscriptionSelection
        v-if="!preview"
        :account-id="accountId"
        :model="form.selection"
      />
      <div v-if="!preview" class="editor-grid section-gap">
        <FieldInput
          v-for="field in fields"
          :key="field.key + field.label"
          :field="field"
          :data="form"
        />
      </div>
      <Facts v-else :data="preview" />
      <p
        v-if="preview && Date.parse(preview.expiresAt) <= now"
        class="notice error"
      >
        Review expired. Edit and review again.
      </p>
      <label class="field section-gap"
        >Reason<textarea v-model="reason" required minlength="5" /></label
      ><label v-if="preview" class="acknowledgement"
        ><input v-model="confirmed" type="checkbox" />Confirm agreement</label
      >
      <p v-if="error" class="notice error">{{ error }}</p>
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
          :disabled="busy || (!!preview && (!confirmed || blocked))"
        >
          {{ busy ? "Processing…" : preview ? "Create agreement" : "Review" }}
        </button>
      </div>
    </form>
    <Facts v-else :data="data" /></ResourceState
  ><ActionDialog
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
