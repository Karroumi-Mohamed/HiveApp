<script setup lang="ts">
import { computed, reactive, ref, watch, onUnmounted } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import CustomerSubscriptionEditor from "@/components/CustomerSubscriptionEditor.vue";
import Icon from "@/components/Icon.vue";
import ResourceState from "@/components/ResourceState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import Pagination from "@/components/Pagination.vue";
import AppDialog from "@/components/AppDialog.vue";
import { gateway, read, write } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import { session, notify, can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { useResource } from "@/composables/useResource";
import { money, date, initials, errorMessage, label } from "@/lib/format";
import { safeReturnTo, contextualPath } from "@/lib/navigation";
import type {
  ClientPlanCatalog,
  SubscriptionChangeInput,
  SubscriptionChangePreview,
  SubscriptionChangeJobPreview,
  SubscriptionChangeJobItemStatus,
  SubscriptionChangeApplyResponse,
} from "@/api/contracts";
import type { RecordData } from "@/resources/types";
const route = useRoute(),
  router = useRouter();
const initialIds = String(route.query.accounts || "")
  .split(",")
  .filter(Boolean);
const fixedCustomer =
  initialIds.length === 1 && route.query.population !== "true";
const audienceScope = initialIds.length
  ? [...initialIds].sort().join(",")
  : "open";
const draftKey = `hive-v2-change-draft:${session.me?.id || "operator"}:${fixedCustomer ? "customer" : "population"}:${audienceScope}:plan:${String(route.query.plan || "current")}`;
const saved = (() => {
  try {
    const stored = JSON.parse(
      sessionStorage.getItem(draftKey) || "null",
    ) as RecordData | null;
    if (
      !stored ||
      stored.schemaVersion !== 1 ||
      !Array.isArray(stored.ids) ||
      !stored.ids.every((id: unknown) => typeof id === "string") ||
      !stored.selection ||
      typeof stored.selection.targetPlanCode !== "string" ||
      !Array.isArray(stored.selection.addOnCodes) ||
      !Array.isArray(stored.selection.quotaPackages) ||
      !["IMMEDIATE", "AT_RENEWAL"].includes(stored.selection.timing)
    )
      return null;
    if (initialIds.length && [...stored.ids].sort().join(",") !== audienceScope)
      return null;
    return stored;
  } catch {
    return null;
  }
})();
const ids = ref<string[]>(initialIds.length ? initialIds : saved?.ids || []);
const model = reactive<RecordData>(
  saved?.selection || {
    targetPlanCode: String(route.query.plan || ""),
    addOnCodes: [],
    quotaPackages: [],
    timing: "AT_RENEWAL",
    planPriceSelection: null,
  },
);
const reason = ref(saved?.reason || ""),
  executeAt = ref(saved?.execute || ""),
  schedule = ref(!!saved?.execute);
const firstStep = fixedCustomer ? 2 : 1;
const step = ref(firstStep),
  search = ref(""),
  currentPage = ref(0),
  busy = ref(false),
  error = ref("");
const singlePreview = ref<SubscriptionChangePreview>(),
  preview = ref<SubscriptionChangeJobPreview>();
const operationResult = ref<SubscriptionChangeApplyResponse>();
const confirmation = ref(false),
  confirmPartial = ref(false),
  replaceConfirmed = ref(false),
  completed = ref(false);
const catalogData = ref<ClientPlanCatalog>(),
  validProduct = ref(false),
  resultsPage = ref(0),
  resultsStatus = ref("");
const singleMode = computed(
  () =>
    ids.value.length === 1 &&
    route.query.population !== "true" &&
    can(p.subscriptionsPreviewChange),
);
const allowed = computed(
  () =>
    can(p.subscriptionsChooseChangeOptions) &&
    (singleMode.value ||
      can(p.subscriptionsPreviewChangeJob) ||
      (!fixedCustomer &&
        route.query.population !== "true" &&
        can(p.subscriptionsPreviewChange) &&
        can(p.subscriptionsChooseAccounts))),
);
const executionAllowed = computed(() =>
  singlePreview.value
    ? can(p.subscriptionsApplyChange)
    : can(p.subscriptionsConfirmChangeJob),
);
const returnPath = computed(
  () =>
    safeReturnTo(route.query.returnTo) ||
    (fixedCustomer ? `/customers/${initialIds[0]}` : "/customers"),
);
const now = ref(Date.now()),
  timer = setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => clearInterval(timer));
const reviewed = computed(() => singlePreview.value || preview.value);
const expired = computed(
  () => !!reviewed.value && now.value >= Date.parse(reviewed.value.expiresAt),
);
const steps = computed(() => [
  ...(!fixedCustomer ? [{ number: 1, name: "Customers" }] : []),
  { number: 2, name: "Configuration" },
  { number: 3, name: "Timing" },
  { number: 4, name: "Review" },
]);
const customers = useResource(
  () =>
    !fixedCustomer && can(p.subscriptionsChooseAccounts)
      ? gateway.chooseAccounts({
          query: search.value,
          page: currentPage.value,
          size: 8,
        })
      : Promise.resolve(undefined),
  [search, currentPage],
);
const resolved = useResource(
  () =>
    ids.value.length && can(p.subscriptionsResolveAccountChoices)
      ? gateway.resolveAccounts(ids.value)
      : Promise.resolve([]),
  [ids],
);
const customerData = customers.data,
  selectedCustomers = resolved.data;
const plan = computed(() =>
  catalogData.value?.plans.find((plan) => plan.code === model.targetPlanCode),
);
const selectedPrice = computed(() =>
  plan.value?.prices.find(
    (price) => price.priceEntryId === model.planPriceSelection?.priceEntryId,
  ),
);
const current = computed(() => catalogData.value?.currentSubscription);
const addedAddOns = computed(() =>
  (model.addOnCodes as string[]).filter(
    (code) => !current.value?.addOnCodes.includes(code),
  ),
);
const removedAddOns = computed(() =>
  (current.value?.addOnCodes || []).filter(
    (code) => !model.addOnCodes.includes(code),
  ),
);
const quotaDifferences = computed(() =>
  [
    ...new Set<string>([
      ...(current.value?.quotaPackages.map((q) => q.packageCode) || []),
      ...model.quotaPackages.map((q: any) => q.packageCode),
    ]),
  ]
    .map((code) => ({
      code,
      before:
        current.value?.quotaPackages.find((q) => q.packageCode === code)
          ?.quantity || 0,
      after:
        model.quotaPackages.find((q: any) => q.packageCode === code)
          ?.quantity || 0,
    }))
    .filter((q) => q.before !== q.after),
);
const noChanges = computed(
  () =>
    singleMode.value &&
    !!current.value?.planPriceEntryId &&
    model.targetPlanCode === current.value.planCode &&
    model.planPriceSelection?.priceEntryId === current.value.planPriceEntryId &&
    !addedAddOns.value.length &&
    !removedAddOns.value.length &&
    !quotaDifferences.value.length &&
    !catalogData.value?.commercialPolicyDecisions.some((decision) =>
      ["AVAILABLE", "APPLIED"].includes(decision.outcome),
    ),
);
const results = useResource(
  () =>
    preview.value && can(p.subscriptionsReadChangeJobResults)
      ? read(p.subscriptionsReadChangeJobResults, () =>
          adminApi.subscriptionChangeJobResults(preview.value!.jobId, {
            page: resultsPage.value,
            size: 20,
            status: resultsStatus.value
              ? (resultsStatus.value as SubscriptionChangeJobItemStatus)
              : undefined,
          }),
        )
      : Promise.resolve(undefined),
  [() => preview.value?.jobId, resultsPage, resultsStatus],
);
const resultRows = computed(
  () => results.data.value?.content || preview.value?.sample || [],
);
const identities = useResource(
  () =>
    preview.value &&
    resultRows.value.length &&
    can(p.subscriptionsReadChangeJobResultIdentities)
      ? gateway.jobIdentities(
          preview.value.jobId,
          resultRows.value.map((r) => r.id),
        )
      : Promise.resolve([]),
  [() => preview.value?.jobId, resultRows],
);
function resultName(id: string) {
  return (
    identities.data.value?.find((item) => item.itemId === id)?.accountName ||
    `Result ${id.slice(0, 8)}`
  );
}
watch(search, () => (currentPage.value = 0));
watch(resultsStatus, () => (resultsPage.value = 0));
watch(
  singleMode,
  (value) => {
    if (value) {
      schedule.value = false;
      executeAt.value = "";
    }
  },
  { immediate: true },
);
watch(
  [ids, model, reason, executeAt, schedule],
  () => {
    singlePreview.value = undefined;
    preview.value = undefined;
    confirmation.value = false;
    confirmPartial.value = false;
    sessionStorage.setItem(
      draftKey,
      JSON.stringify({
        schemaVersion: 1,
        ids: ids.value,
        selection: model,
        reason: reason.value,
        execute: schedule.value ? executeAt.value : "",
      }),
    );
  },
  { deep: true },
);
watch(
  ids,
  () => {
    replaceConfirmed.value = false;
  },
  { deep: true },
);
function toggle(id: string) {
  ids.value = !can(p.subscriptionsPreviewChangeJob)
    ? [id]
    : ids.value.includes(id)
      ? ids.value.filter((value) => value !== id)
      : [...ids.value, id];
}
function selectAll() {
  ids.value = [
    ...new Set([
      ...ids.value,
      ...(customerData.value?.content.map((c) => c.id) || []),
    ]),
  ];
}
function selection(): SubscriptionChangeInput {
  if (!validProduct.value || !model.planPriceSelection)
    throw new Error("Choose valid subscription terms.");
  return {
    targetPlanCode: model.targetPlanCode,
    addOnCodes: [...model.addOnCodes],
    quotaPackages: model.quotaPackages.map((q: any) => ({ ...q })),
    timing: model.timing,
    planPriceSelection: { ...model.planPriceSelection },
  };
}
async function review() {
  error.value = "";
  if (
    !allowed.value ||
    (!singleMode.value && !can(p.subscriptionsPreviewChangeJob))
  ) {
    error.value =
      "Your role can review one customer at a time. Return to Customers and select one account.";
    return;
  }
  if (noChanges.value) {
    error.value =
      "No change is selected. Update the subscription terms before reviewing.";
    return;
  }
  if (reason.value.trim().length < 5) {
    error.value = "Enter an audit reason of at least five characters.";
    return;
  }
  if (!singleMode.value && ids.value.length > 1 && !replaceConfirmed.value) {
    error.value =
      "Confirm that the complete selected configuration will replace each customer's holdings.";
    return;
  }
  let execute: string | null = null;
  if (schedule.value && !singleMode.value) {
    const time = Date.parse(executeAt.value);
    if (!Number.isFinite(time) || time <= Date.now()) {
      error.value = "Choose a future execution date and time.";
      return;
    }
    execute = new Date(time).toISOString();
  }
  busy.value = true;
  try {
    if (singleMode.value)
      singlePreview.value = await gateway.previewChange(
        ids.value[0],
        selection(),
      );
    else
      preview.value = await gateway.previewJob({
        accountIds: [...ids.value],
        selection: selection(),
        reason: reason.value.trim(),
        executeAt: execute,
      });
    resultsPage.value = 0;
    resultsStatus.value = "";
    step.value = 4;
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
const canConfirm = computed(
  () =>
    !!reviewed.value &&
    executionAllowed.value &&
    !expired.value &&
    confirmation.value &&
    (singlePreview.value
      ? !singlePreview.value.conflicts.length &&
        (model.timing !== "IMMEDIATE" || singlePreview.value.immediateAllowed)
      : !!preview.value?.readyCount &&
        (!preview.value.conflictCount || confirmPartial.value)),
);
async function confirm() {
  if (!canConfirm.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (singlePreview.value) {
      const result = await write(p.subscriptionsApplyChange, () =>
        adminApi.applySubscriptionChange(ids.value[0], {
          selection: selection(),
          previewToken: singlePreview.value!.previewToken,
          reason: reason.value.trim(),
        }),
      );
      completed.value = true;
      sessionStorage.removeItem(draftKey);
      notify(`Change ${label(result.operation.status).toLowerCase()}.`);
      if (can(p.subscriptionsReadChanges)) {
        await router.push(
          contextualPath(
            `/customers/${ids.value[0]}?view=changes&change=${result.operation.id}`,
            route,
          ),
        );
      } else operationResult.value = result;
    } else if (preview.value) {
      const result = await gateway.confirmJob(
        preview.value.jobId,
        preview.value.previewToken,
      );
      completed.value = true;
      sessionStorage.removeItem(draftKey);
      notify("Change operation confirmed.");
      await router.push(
        contextualPath(`/operations/${result.summary.id}`, route),
      );
    }
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
const nextAllowed = computed(() =>
  step.value === 1
    ? ids.value.length > 0 &&
      (singleMode.value || can(p.subscriptionsPreviewChangeJob))
    : step.value === 2
      ? validProduct.value &&
        !noChanges.value &&
        (singleMode.value || ids.value.length === 1 || replaceConfirmed.value)
      : true,
);
let leaveResolve: ((value: boolean) => void) | undefined;
const leaveOpen = ref(false);
onBeforeRouteLeave(() => {
  if (completed.value || (!ids.value.length && !reason.value)) return true;
  if (busy.value) return false;
  leaveOpen.value = true;
  return new Promise<boolean>((resolve) => (leaveResolve = resolve));
});
function leave(value: boolean) {
  leaveOpen.value = false;
  leaveResolve?.(value);
  leaveResolve = undefined;
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (!completed.value && ids.value.length) {
    e.preventDefault();
    e.returnValue = "";
  }
}
window.addEventListener("beforeunload", beforeUnload);
onUnmounted(() => window.removeEventListener("beforeunload", beforeUnload));
</script>
<template>
  <RouterLink class="back-link" :to="returnPath"
    ><Icon name="back" :size="14" />{{
      fixedCustomer ? "Customer" : "Customers"
    }}</RouterLink
  >
  <PageHeading
    :title="fixedCustomer ? 'Change subscription' : 'Change subscriptions'"
    ><span class="draft-status">{{
      saved
        ? "Draft restored · review required"
        : "Draft stored in this browser session"
    }}</span></PageHeading
  >
  <div v-if="!allowed" class="notice warning">
    <Icon name="shield" />
    <p>
      Your role needs change options and
      {{
        fixedCustomer
          ? "single-customer or bulk preview"
          : "subscription change preview"
      }}
      access.
    </p>
  </div>
  <section v-else-if="operationResult" class="panel">
    <div class="panel-header">
      <h2>Change recorded</h2>
      <StatusBadge :status="operationResult.operation.status" />
    </div>
    <div class="panel-body">
      <dl class="detail-list">
        <div>
          <dt>Plan</dt>
          <dd>
            {{ operationResult.operation.sourcePlanCode }} →
            {{ operationResult.operation.targetPlanCode }}
          </dd>
        </div>
        <div>
          <dt>Effective</dt>
          <dd>
            {{
              operationResult.operation.effectiveAt
                ? date(operationResult.operation.effectiveAt, true)
                : label(operationResult.operation.timing)
            }}
          </dd>
        </div>
        <div>
          <dt>Reviewed recurring amount</dt>
          <dd>
            {{
              money(
                operationResult.preview.previewPrice,
                operationResult.preview.currencyCode,
              )
            }}
          </dd>
        </div>
        <div>
          <dt>Reason</dt>
          <dd>{{ reason }}</dd>
        </div>
        <div>
          <dt>Operation ID</dt>
          <dd class="identifier">{{ operationResult.operation.id }}</dd>
        </div>
      </dl>
      <p
        v-if="operationResult.operation.status === 'AWAITING_CONFIRMATION'"
        class="notice warning section-gap"
      >
        The change is awaiting confirmation; the new terms have not been
        applied. Payment actions are not available yet.
      </p>
      <p
        v-else-if="operationResult.operation.status === 'PENDING'"
        class="notice section-gap"
      >
        The change is recorded for the effective date. Current terms remain in
        place until it is applied.
      </p>
      <p
        v-if="operationResult.operation.attentionReason"
        class="notice warning section-gap"
      >
        {{ operationResult.operation.attentionReason }}
      </p>
      <div class="form-actions">
        <RouterLink class="button primary" :to="returnPath"
          >Return to customer</RouterLink
        >
      </div>
    </div>
  </section>
  <template v-else>
    <nav class="stepper" aria-label="Change progress">
      <button
        v-for="(item, index) in steps"
        :key="item.number"
        :class="{ current: step === item.number, complete: step > item.number }"
        :disabled="item.number > step || busy"
        :aria-current="step === item.number ? 'step' : undefined"
        @click="step = item.number"
      >
        <span class="step-circle"
          ><Icon v-if="step > item.number" name="check" :size="14" /><template
            v-else
            >{{ index + 1 }}</template
          ></span
        ><strong>{{ item.name }}</strong>
      </button>
    </nav>
    <div class="wizard-grid">
      <section class="panel wizard-main">
        <div class="panel-header">
          <h2>{{ steps.find((item) => item.number === step)?.name }}</h2>
        </div>
        <div class="panel-body">
          <template v-if="step === 1">
            <div class="toolbar">
              <label class="search-field"
                ><Icon name="search" :size="16" /><input
                  v-model="search"
                  placeholder="Find customers…"
                  aria-label="Find customers for change" /></label
              ><button
                v-if="can(p.subscriptionsPreviewChangeJob)"
                class="button small"
                :disabled="!customerData?.content.length"
                @click="selectAll"
              >
                Select this page
              </button>
            </div>
            <ResourceState
              :loading="customers.loading.value"
              :error="customers.error.value"
              :empty="!customerData?.content.length"
              title="No customers found"
              @retry="customers.refresh()"
              ><div class="customer-choices">
                <label
                  v-for="customer in customerData?.content"
                  :key="customer.id"
                  class="customer-choice"
                  :class="{ chosen: ids.includes(customer.id) }"
                  ><input
                    :type="
                      can(p.subscriptionsPreviewChangeJob)
                        ? 'checkbox'
                        : 'radio'
                    "
                    name="change-account"
                    :checked="ids.includes(customer.id)"
                    @change="toggle(customer.id)"
                  /><span class="avatar company-avatar">{{
                    initials(customer.name)
                  }}</span
                  ><span
                    ><strong>{{ customer.name }}</strong
                    ><small>{{ customer.slug }}</small></span
                  ><span class="choice-status">{{
                    customer.active ? "Active account" : "Inactive account"
                  }}</span></label
                >
              </div>
              <Pagination
                v-if="customerData"
                :page="currentPage"
                :size="8"
                :total="customerData.totalElements"
                @change="currentPage = $event"
            /></ResourceState>
          </template>
          <div v-show="step === 2">
            <CustomerSubscriptionEditor
              v-if="ids.length"
              :key="ids[0] + ':' + singleMode"
              :account-id="ids[0]"
              :model="model"
              :initialize="!saved"
              :population="!singleMode && ids.length > 1"
              @ready="validProduct = $event"
              @catalog="catalogData = $event"
            />
            <p v-if="noChanges" class="small muted section-gap">
              No change selected. Update the plan, price, add-ons or capacity to
              continue.
            </p>
            <div
              v-if="!singleMode && ids.length > 1"
              class="notice warning section-gap"
            >
              <Icon name="alert" />
              <p>
                One complete configuration will replace every selected
                customer's add-ons and capacity. Options use the first selected
                account; preview checks all accounts. Per-customer preservation
                is not supported by this command.
              </p>
            </div>
            <label
              v-if="!singleMode && ids.length > 1"
              class="check-label section-gap"
              ><input v-model="replaceConfirmed" type="checkbox" /><span
                >Replace each customer's holdings with the configuration
                above.</span
              ></label
            >
          </div>
          <template v-if="step === 3">
            <fieldset class="timing-options">
              <legend class="field-label">Effective timing</legend>
              <label
                class="timing-option"
                :class="{ chosen: model.timing === 'AT_RENEWAL' }"
                ><input
                  v-model="model.timing"
                  type="radio"
                  value="AT_RENEWAL"
                /><Icon name="calendar" /><span
                  ><strong>At renewal</strong
                  ><small
                    >Current terms remain until the next renewal.</small
                  ></span
                ></label
              ><label
                class="timing-option"
                :class="{ chosen: model.timing === 'IMMEDIATE' }"
                ><input
                  v-model="model.timing"
                  type="radio"
                  value="IMMEDIATE"
                /><Icon name="zap" /><span
                  ><strong>Immediately</strong
                  ><small
                    >A paid upgrade may remain awaiting confirmation. Payment
                    actions are not available yet.</small
                  ></span
                ></label
              >
            </fieldset>
            <label v-if="!singleMode" class="check-label section-gap"
              ><input v-model="schedule" type="checkbox" /><span
                >Schedule the operation start</span
              ></label
            ><label v-if="schedule && !singleMode" class="field section-gap"
              >Execution date and time<input
                v-model="executeAt"
                type="datetime-local"
                required
              /><small
                >Local time; subscription timing remains
                {{
                  model.timing === "AT_RENEWAL" ? "at renewal" : "immediate"
                }}.</small
              ></label
            ><label class="field section-gap"
              >Reason<textarea
                v-model="reason"
                required
                minlength="5"
                maxlength="2000"
              /><small
                >Stored with the change and its audit history.</small
              ></label
            >
          </template>
          <template v-if="step === 4 && reviewed">
            <div v-if="expired" class="notice error">
              <Icon name="clock" />
              <p>Review expired. Review again before confirming.</p>
            </div>
            <p v-else class="small muted">
              Reviewed {{ date(reviewed.evaluatedAt, true) }} · expires
              {{ date(reviewed.expiresAt, true) }}
            </p>
            <template v-if="singlePreview">
              <table class="data-table section-gap">
                <thead>
                  <tr>
                    <th>Terms</th>
                    <th>Current</th>
                    <th>Reviewed target</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>Plan</td>
                    <td>{{ singlePreview.currentPlanCode }}</td>
                    <td>{{ singlePreview.targetPlanCode }}</td>
                  </tr>
                  <tr>
                    <td>Recurring amount</td>
                    <td>
                      {{
                        money(
                          singlePreview.currentPrice,
                          current?.currentPriceCurrencyCode ||
                            singlePreview.currencyCode,
                        )
                      }}
                    </td>
                    <td>
                      {{
                        money(
                          singlePreview.previewPrice,
                          singlePreview.currencyCode,
                        )
                      }}
                    </td>
                  </tr>
                  <tr>
                    <td>Add-ons</td>
                    <td>
                      {{ current?.addOnCodes.map(label).join(", ") || "None" }}
                    </td>
                    <td>
                      {{
                        singlePreview.addOnCodes.map(label).join(", ") || "None"
                      }}
                    </td>
                  </tr>
                  <tr v-for="q in quotaDifferences" :key="q.code">
                    <td>{{ label(q.code) }}</td>
                    <td>{{ q.before }} units</td>
                    <td>{{ q.after }} units</td>
                  </tr>
                  <tr>
                    <td>Effective</td>
                    <td>—</td>
                    <td>
                      {{
                        model.timing === "AT_RENEWAL"
                          ? date(current?.currentPeriodEnd)
                          : "Immediately after application"
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="removedAddOns.length" class="conflict-text section-gap">
                Remove: {{ removedAddOns.map(label).join(", ") }}
              </p>
              <p v-if="addedAddOns.length" class="small section-gap">
                Add: {{ addedAddOns.map(label).join(", ") }}
              </p>
              <p
                v-for="conflict in singlePreview.conflicts"
                :key="conflict.code"
                class="notice error section-gap"
              >
                {{ conflict.message
                }}<span v-if="conflict.currentUsage !== null">
                  · usage {{ conflict.currentUsage }}, requested
                  {{ conflict.requestedLimit ?? "unlimited" }}</span
                >
              </p>
              <p
                v-if="
                  model.timing === 'IMMEDIATE' &&
                  !singlePreview.immediateAllowed
                "
                class="notice warning section-gap"
              >
                This change cannot be applied immediately. Return to Timing and
                choose renewal.
              </p>
              <details class="section-gap">
                <summary>Reviewed capabilities</summary>
                <p class="small section-gap">
                  {{
                    singlePreview.effectiveFeatureCodes.map(label).join(", ")
                  }}
                </p>
                <table
                  v-if="singlePreview.effectiveQuotaLimits.length"
                  class="data-table"
                >
                  <thead>
                    <tr>
                      <th>Resource</th>
                      <th>Included</th>
                      <th>Purchased</th>
                      <th>Effective</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="quota in singlePreview.effectiveQuotaLimits"
                      :key="quota.featureCode + quota.resource"
                    >
                      <td>
                        {{ label(quota.featureCode) }} ·
                        {{ label(quota.resource) }}
                      </td>
                      <td>{{ quota.includedLimit ?? "Unlimited" }}</td>
                      <td>{{ quota.purchasedCapacity }}</td>
                      <td>{{ quota.effectiveLimit ?? "Unlimited" }}</td>
                    </tr>
                  </tbody>
                </table>
              </details>
            </template>
            <template v-else-if="preview">
              <div class="review-metrics section-gap">
                <div>
                  <strong>{{ preview.targetCount }}</strong
                  ><span>assessed</span>
                </div>
                <div>
                  <strong class="positive">{{ preview.readyCount }}</strong
                  ><span>ready</span>
                </div>
                <div>
                  <strong :class="{ warning: preview.conflictCount }">{{
                    preview.conflictCount
                  }}</strong
                  ><span>blocked</span>
                </div>
              </div>
              <label
                v-if="can(p.subscriptionsReadChangeJobResults)"
                class="field section-gap"
                >Results<select v-model="resultsStatus">
                  <option value="">All results</option>
                  <option value="READY">Ready</option>
                  <option value="CONFLICT">Blocked</option>
                </select></label
              >
              <ResourceState
                :loading="results.loading.value"
                :error="results.error.value"
                :empty="!resultRows.length"
                title="No matching results"
                @retry="results.refresh()"
                ><div
                  v-for="result in resultRows"
                  :key="result.id"
                  class="review-result"
                >
                  <div class="row spread">
                    <strong>{{ resultName(result.id) }}</strong
                    ><StatusBadge :status="result.status" />
                  </div>
                  <p>
                    {{ result.assessment.currentPlanCode || "No current plan" }}
                    → {{ result.assessment.targetPlanCode }} ·
                    {{
                      money(
                        result.assessment.currentPrice || "0",
                        result.assessment.currencyCode || "MAD",
                      )
                    }}
                    →
                    {{
                      money(
                        result.assessment.targetPrice || "0",
                        result.assessment.currencyCode || "MAD",
                      )
                    }}
                  </p>
                  <p
                    v-for="conflict in result.assessment.conflicts"
                    :key="conflict.code"
                    class="conflict-text"
                  >
                    {{ conflict.message }}
                  </p>
                </div>
                <Pagination
                  v-if="results.data.value"
                  :page="resultsPage"
                  :size="20"
                  :total="results.data.value.totalElements"
                  @change="resultsPage = $event"
              /></ResourceState>
              <p
                v-if="!can(p.subscriptionsReadChangeJobResults)"
                class="small muted section-gap"
              >
                Only the preview sample is available with your access. Reading
                every result requires result-read permission.
              </p>
              <p
                v-if="!can(p.subscriptionsReadChangeJobResultIdentities)"
                class="small muted section-gap"
              >
                Customer names require independent result-identity access.
              </p>
              <label
                v-if="preview.conflictCount"
                class="check-label section-gap"
                ><input
                  v-model="confirmPartial"
                  type="checkbox"
                  :disabled="!executionAllowed"
                /><span
                  >Proceed with {{ preview.readyCount }} ready customers;
                  {{ preview.conflictCount }} blocked customers remain
                  unchanged.</span
                ></label
              >
            </template>
            <p class="small section-gap">Reason: {{ reason }}</p>
            <p v-if="!executionAllowed" class="notice section-gap">
              You can review; confirmation requires additional access.
            </p>
            <label class="check-label section-gap"
              ><input
                v-model="confirmation"
                type="checkbox"
                :disabled="!executionAllowed"
              /><span
                >I reviewed the configuration, timing and impact.</span
              ></label
            >
          </template>
          <div v-if="error" class="notice error section-gap" role="alert">
            <Icon name="alert" />
            <p>{{ error }}</p>
          </div>
        </div>
        <div class="wizard-footer">
          <button
            class="button"
            :disabled="step === firstStep || busy"
            @click="
              step--;
              error = '';
            "
          >
            <Icon name="back" :size="14" />Back</button
          ><span>{{ step - firstStep + 1 }} of {{ steps.length }}</span
          ><button
            v-if="step < 3"
            class="button primary"
            :disabled="!nextAllowed || busy"
            @click="step++"
          >
            Continue<Icon name="right" :size="14" /></button
          ><button
            v-else-if="step === 3 || expired || !reviewed"
            class="button primary"
            :disabled="busy || !validProduct || !ids.length"
            @click="review"
          >
            {{
              busy ? "Reviewing…" : expired ? "Review again" : "Review impact"
            }}</button
          ><button
            v-else
            class="button primary"
            :disabled="busy || !canConfirm"
            @click="confirm"
          >
            {{
              busy
                ? "Confirming…"
                : schedule && !singleMode
                  ? "Confirm scheduled change"
                  : "Confirm change"
            }}
          </button>
        </div>
      </section>
      <aside class="change-summary">
        <h2>Change summary</h2>
        <div class="summary-line">
          <Icon name="customers" />
          <div>
            <small>Customers</small><strong>{{ ids.length }} selected</strong>
            <p>
              {{
                selectedCustomers
                  ?.map((customer) => customer.name)
                  .slice(0, 3)
                  .join(", ")
              }}
            </p>
          </div>
        </div>
        <div class="summary-line">
          <Icon name="catalog" />
          <div>
            <small>Target terms</small
            ><strong>{{ plan?.name || "Choose a plan" }}</strong>
            <p v-if="selectedPrice">
              Base plan price:
              {{ money(selectedPrice.amount, selectedPrice.currencyCode) }} ·
              {{ label(selectedPrice.billingCycle) }}
            </p>
            <p>
              {{ model.addOnCodes.length }} add-ons ·
              {{ model.quotaPackages.length }} capacity packages
            </p>
          </div>
        </div>
        <div class="summary-line">
          <Icon name="clock" />
          <div>
            <small>Effective</small
            ><strong>{{
              model.timing === "AT_RENEWAL" ? "At renewal" : "Immediately"
            }}</strong>
            <p v-if="schedule && executeAt && !singleMode">
              Operation starts {{ date(executeAt, true) }}
            </p>
          </div>
        </div>
      </aside>
    </div>
  </template>
  <AppDialog
    :open="leaveOpen"
    title="Leave this change draft?"
    @close="leave(false)"
    ><p class="muted">
      The configuration stays in this browser session. A new review is required
      when you return.
    </p>
    <div class="form-actions">
      <button class="button" @click="leave(false)">Keep working</button
      ><button class="button primary" @click="leave(true)">Leave draft</button>
    </div></AppDialog
  >
</template>
<style scoped>
.draft-status {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 10px;
  color: var(--muted);
}
.stepper {
  display: flex;
  margin: 0 0 30px;
  border-top: 1px solid var(--border);
  padding-top: 25px;
}
.stepper button {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--muted);
}
.stepper button:disabled {
  opacity: 0.6;
}
.step-circle {
  width: 29px;
  height: 29px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  border: 1px solid var(--border);
  font-size: 11px;
  flex-shrink: 0;
}
.stepper strong {
  font-size: 11px;
  display: block;
}
.stepper small {
  font-size: 9px;
  display: block;
  margin-top: 3px;
}
.stepper .current {
  color: var(--text);
}
.current .step-circle {
  background: var(--text);
  color: var(--white);
  border-color: var(--text);
}
.complete .step-circle {
  background: var(--brand);
  color: var(--text);
  border-color: var(--brand);
}
.step-chevron {
  margin: 0 20px 0 auto;
}
.wizard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 275px;
  gap: 24px;
  align-items: start;
}
.wizard-main .panel-body {
  min-height: 420px;
}
.change-summary {
  background: var(--stone-50);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 24px;
  position: sticky;
  top: 20px;
}
.change-summary h2 {
  margin: 10px 0 24px;
  font-size: 19px;
  font-weight: 540;
}
.summary-line {
  display: flex;
  gap: 10px;
  border-top: 1px solid var(--border);
  padding: 18px 0;
}
.summary-line > svg {
  margin-top: 3px;
  color: var(--muted);
}
.summary-line small {
  display: block;
  color: var(--muted);
  font-size: 9px;
}
.summary-line strong {
  display: block;
  font-size: 12px;
  margin-top: 4px;
}
.summary-line p {
  color: var(--muted);
  font-size: 10px;
  margin-top: 4px;
}
.summary-note {
  display: flex;
  gap: 8px;
  color: var(--muted);
  font-size: 10px;
  margin-top: 20px;
}
.wizard-footer {
  padding: 16px 22px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface);
}
.wizard-footer > span {
  font-size: 10px;
  color: var(--muted);
}
.customer-choice {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 10px;
  border-bottom: 1px solid var(--stone-100);
  cursor: pointer;
  border-radius: 6px;
}
.customer-choice:hover {
  background: var(--surface);
}
.customer-choice.chosen {
  background: var(--accent-soft);
}
.customer-choice strong {
  display: block;
  font-size: 11px;
}
.customer-choice small {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 2px;
}
.choice-status {
  font-size: 9px;
  color: var(--muted);
  margin-left: auto;
}
.plan-choices {
  display: grid;
  gap: 12px;
}
.plan-choice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 9px;
  cursor: pointer;
}
.plan-choice.chosen {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.plan-choice > span:first-of-type {
  flex: 1;
}
.plan-choice strong {
  font-size: 13px;
}
.plan-choice small {
  display: block;
  font-size: 10px;
  color: var(--muted);
  margin-top: 4px;
}
.plan-choice-price {
  text-align: right;
  font-size: 13px;
  font-weight: 550;
  white-space: nowrap;
}
.plan-choice.unavailable {
  opacity: 0.55;
}
.addon-choice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 0;
  font-size: 12px;
}
.addon-choice strong {
  margin-left: auto;
  font-size: 11px;
}
.quantity-choice {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  font-size: 12px;
}
.quantity-choice small {
  display: block;
  color: var(--muted);
  font-size: 10px;
}
.quantity-choice input {
  width: 75px;
}
.timing-options {
  border: 0;
  padding: 0;
  margin: 0;
}
.field-label {
  font-size: 11px;
  font-weight: 550;
  padding: 0 0 12px;
}
.timing-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 19px 15px;
  border: 1px solid var(--border);
  border-radius: 9px;
  margin-bottom: 12px;
  cursor: pointer;
}
.timing-option.chosen {
  background: var(--accent-soft);
  border-color: var(--accent);
}
.timing-option strong {
  font-size: 11px;
  display: block;
}
.timing-option small {
  font-size: 10px;
  color: var(--muted);
  display: block;
  margin-top: 4px;
}
.timing-option .pill {
  margin-left: auto;
}
.block {
  display: block;
  font-size: 10px;
  margin-top: 4px;
}
.review-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  border-bottom: 1px solid var(--border);
  padding: 10px 0 23px;
}
.review-metrics strong {
  display: block;
  font-size: 27px;
  font-weight: 550;
}
.review-metrics span {
  display: block;
  color: var(--muted);
  font-size: 10px;
}
.positive {
  color: var(--accent);
}
.warning {
  color: var(--warning);
}
.review-result {
  padding: 15px 0;
  border-top: 1px solid var(--border);
  font-size: 11px;
}
.review-result:first-child {
  border: 0;
}
.review-result p {
  color: var(--muted);
  font-size: 10px;
  margin-top: 5px;
}
.review-result .conflict-text {
  color: var(--danger);
}
@media (max-width: 1100px) {
  .wizard-grid {
    grid-template-columns: 1fr;
  }
  .change-summary {
    display: none;
  }
  .step-chevron {
    margin-right: 10px;
  }
}
@media (max-width: 700px) {
  .stepper small {
    display: none;
  }
  .stepper button {
    flex-direction: column;
    gap: 5px;
    text-align: center;
  }
  .stepper .step-chevron {
    display: none;
  }
  .stepper strong {
    font-size: 9px;
  }
  .wizard-main .panel-body {
    min-height: 350px;
  }
  .timing-option {
    flex-wrap: wrap;
  }
  .timing-option .pill {
    margin-left: 39px;
  }
  .choice-status {
    display: none;
  }
  .plan-choice {
    flex-wrap: wrap;
  }
  .plan-choice-price {
    margin-left: 28px;
    text-align: left;
  }
  .review-metrics span {
    font-size: 9px;
  }
}
</style>
