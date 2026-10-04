<script setup lang="ts">
import { computed, reactive, ref, watch, onUnmounted } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import Icon from "@/components/Icon.vue";
import ResourceState from "@/components/ResourceState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import Pagination from "@/components/Pagination.vue";
import AppDialog from "@/components/AppDialog.vue";
import { gateway } from "@/data/gateway";
import { session, notify, can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { useResource } from "@/composables/useResource";
import { money, date, initials, errorMessage, label } from "@/lib/format";
import type {
  SubscriptionChangeInput,
  SubscriptionChangeJobPreview,
} from "@/api/contracts";
const route = useRoute();
const router = useRouter();
const draftKey = `hive-v2-change-draft:${session.me?.id || "operator"}`;
const saved = (() => {
  try {
    return JSON.parse(sessionStorage.getItem(draftKey) || "null") as {
      ids: string[];
      plan: string;
      price: string;
      addons: string[];
      quotas: Record<string, number>;
      timing: "IMMEDIATE" | "AT_RENEWAL";
      reason: string;
      execute: string;
    } | null;
  } catch {
    return null;
  }
})();
const ids = ref<string[]>(
  route.query.accounts
    ? String(route.query.accounts).split(",").filter(Boolean)
    : saved?.ids || [],
);
const targetPlan = ref(String(route.query.plan || saved?.plan || ""));
const priceId = ref(saved?.price || "");
const addons = ref<string[]>(saved?.addons || []);
const quantities = reactive<Record<string, number>>(saved?.quotas || {});
const timing = ref<"IMMEDIATE" | "AT_RENEWAL">(saved?.timing || "AT_RENEWAL");
const reason = ref(saved?.reason || "");
const executeAt = ref(saved?.execute || "");
const schedule = ref(!!executeAt.value);
const step = ref(1);
const search = ref("");
const currentPage = ref(0);
const busy = ref(false);
const error = ref("");
const preview = ref<SubscriptionChangeJobPreview>();
const confirmPartial = ref(false);
const confirmation = ref(false);
const completed = ref(false);
const now = ref(Date.now());
const interval = setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => clearInterval(interval));
const expired = computed(
  () => !!preview.value && now.value >= Date.parse(preview.value.expiresAt),
);
const customers = useResource(
  () =>
    gateway.chooseAccounts({
      query: search.value,
      page: currentPage.value,
      size: 8,
    }),
  [search, currentPage],
);
const customerData = customers.data;
const resolved = useResource(
  () =>
    ids.value.length ? gateway.resolveAccounts(ids.value) : Promise.resolve([]),
  [ids],
);
const selectedCustomers = resolved.data;
const catalog = useResource(
  () =>
    ids.value[0] ? gateway.catalog(ids.value[0]) : Promise.resolve(undefined),
  [() => ids.value[0]],
);
const catalogData = catalog.data;
const plan = computed(() =>
  catalogData.value?.plans.find((p) => p.code === targetPlan.value),
);
const selectedPrice = computed(() =>
  plan.value?.prices.find((p) => p.priceEntryId === priceId.value),
);
watch(plan, (p) => {
  if (p && !p.prices.some((x) => x.priceEntryId === priceId.value))
    priceId.value = p.prices[0]?.priceEntryId || "";
});
watch(search, () => (currentPage.value = 0));
watch(
  [
    ids,
    targetPlan,
    priceId,
    addons,
    quantities,
    timing,
    reason,
    executeAt,
    schedule,
  ],
  () => {
    preview.value = undefined;
    confirmPartial.value = false;
    confirmation.value = false;
    sessionStorage.setItem(
      draftKey,
      JSON.stringify({
        ids: ids.value,
        plan: targetPlan.value,
        price: priceId.value,
        addons: addons.value,
        quotas: quantities,
        timing: timing.value,
        reason: reason.value,
        execute: schedule.value ? executeAt.value : "",
      }),
    );
  },
  { deep: true },
);
watch(targetPlan, () => {
  addons.value = [];
  Object.keys(quantities).forEach((k) => delete quantities[k]);
});
const validProduct = computed(
  () => !!plan.value?.selectable && !!selectedPrice.value,
);
const steps = [
  { name: "Customers", description: "Define the audience" },
  { name: "Configuration", description: "Choose product & capacity" },
  { name: "Timing", description: "Set when and why" },
  { name: "Review", description: "Check impact & confirm" },
];
function toggle(id: string) {
  ids.value = ids.value.includes(id)
    ? ids.value.filter((x) => x !== id)
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
function choosePlan(code: string) {
  targetPlan.value = code;
}
function selection(): SubscriptionChangeInput {
  if (!selectedPrice.value) throw new Error("Choose a valid price entry.");
  return {
    targetPlanCode: targetPlan.value,
    addOnCodes: [...addons.value],
    quotaPackages: Object.entries(quantities)
      .filter(([, q]) => q > 0)
      .map(([packageCode, quantity]) => ({ packageCode, quantity })),
    timing: timing.value,
    planPriceSelection: {
      priceEntryId: selectedPrice.value.priceEntryId,
      currencyCode: selectedPrice.value.currencyCode,
      billingCycle: selectedPrice.value.billingCycle,
    },
  };
}
async function review() {
  error.value = "";
  if (reason.value.trim().length < 5) {
    error.value =
      "Add a clear reason of at least five characters for the audit trail.";
    return;
  }
  let execute: string | null = null;
  if (schedule.value) {
    const time = Date.parse(executeAt.value);
    if (!Number.isFinite(time) || time <= Date.now()) {
      error.value = "Choose a future execution date and time.";
      return;
    }
    execute = new Date(time).toISOString();
  }
  busy.value = true;
  try {
    preview.value = await gateway.previewJob({
      accountIds: [...ids.value],
      selection: selection(),
      reason: reason.value.trim(),
      executeAt: execute,
    });
    step.value = 4;
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  if (
    !preview.value ||
    expired.value ||
    !confirmation.value ||
    !preview.value.readyCount
  )
    return;
  busy.value = true;
  error.value = "";
  try {
    const job = await gateway.confirmJob(
      preview.value.jobId,
      preview.value.previewToken,
    );
    completed.value = true;
    sessionStorage.removeItem(draftKey);
    notify("Change confirmed.");
    await router.push(`/operations/${job.summary.id}`);
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
const nextAllowed = computed(() =>
  step.value === 1
    ? ids.value.length > 0 && !resolved.loading.value
    : step.value === 2
      ? validProduct.value
      : true,
);
let leaveResolve: ((value: boolean) => void) | undefined;
const leaveOpen = ref(false);
onBeforeRouteLeave(() => {
  if (completed.value) return true;
  if (!ids.value.length || busy.value) return !busy.value;
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
  <RouterLink class="back-link" to="/customers"
    ><Icon name="back" :size="14" />Customers</RouterLink
  ><PageHeading title="Change subscriptions"
    ><span class="draft-status"
      ><span class="status-dot" />Draft saved in this browser session</span
    ></PageHeading
  >
  <div
    v-if="
      !can(p.subscriptionsPreviewChangeJob) ||
      !can(p.subscriptionsConfirmChangeJob)
    "
    class="notice warning"
  >
    <Icon name="shield" />
    <p>
      Your role needs bulk-change preview and confirmation permissions to use
      this flow.
    </p>
  </div>
  <template v-else
    ><nav class="stepper" aria-label="Change progress">
      <button
        v-for="(s, i) in steps"
        :key="s.name"
        :class="{ current: step === i + 1, complete: step > i + 1 }"
        :disabled="i + 1 > step || busy"
        :aria-current="step === i + 1 ? 'step' : undefined"
        @click="step = i + 1"
      >
        <span class="step-circle"
          ><Icon v-if="step > i + 1" name="check" :size="14" /><template
            v-else
            >{{ i + 1 }}</template
          ></span
        ><span
          ><strong>{{ s.name }}</strong
          ><small>{{ s.description }}</small></span
        ><Icon v-if="i < 3" class="step-chevron" name="next" :size="14" />
      </button>
    </nav>
    <div class="wizard-grid">
      <section class="panel wizard-main">
        <div class="panel-header">
          <div>
            <h2>
              {{
                step === 1
                  ? "Customers"
                  : step === 2
                    ? "Subscription"
                    : step === 3
                      ? "Timing"
                      : "Review"
              }}
            </h2>
          </div>
        </div>
        <div class="panel-body">
          <template v-if="step === 1"
            ><div class="toolbar">
              <label class="search-field"
                ><Icon name="search" :size="16" /><input
                  v-model="search"
                  placeholder="Find customers…"
                  aria-label="Find customers for change" /></label
              ><button
                class="button small"
                @click="selectAll"
                :disabled="!customerData?.content.length"
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
                  v-for="c in customerData?.content"
                  :key="c.id"
                  class="customer-choice"
                  :class="{ chosen: ids.includes(c.id) }"
                  ><input
                    type="checkbox"
                    :checked="ids.includes(c.id)"
                    @change="toggle(c.id)"
                  /><span class="avatar company-avatar">{{
                    initials(c.name)
                  }}</span
                  ><span
                    ><strong>{{ c.name }}</strong
                    ><small>{{ c.slug }}</small></span
                  ><span class="choice-status">{{
                    c.active ? "Active account" : "Inactive account"
                  }}</span></label
                >
              </div>
              <Pagination
                v-if="customerData"
                :page="currentPage"
                :size="8"
                :total="customerData.totalElements"
                @change="currentPage = $event" /></ResourceState
          ></template>
          <template v-else-if="step === 2"
            ><ResourceState
              :loading="catalog.loading.value"
              :error="catalog.error.value"
              :empty="!catalogData?.plans.length"
              title="No available plans for this account"
              @retry="catalog.refresh()"
              ><div class="plan-choices">
                <label
                  v-for="p in catalogData?.plans"
                  :key="p.code"
                  class="plan-choice"
                  :class="{
                    chosen: targetPlan === p.code,
                    unavailable: !p.selectable,
                  }"
                  ><input
                    type="radio"
                    name="target-plan"
                    :value="p.code"
                    :checked="targetPlan === p.code"
                    :disabled="!p.selectable"
                    @change="choosePlan(p.code)"
                  /><span
                    ><strong>{{ p.name }}</strong
                    ><small>{{ p.description }}</small></span
                  ><span class="plan-choice-price"
                    >{{ money(p.basePrice, p.currencyCode)
                    }}<small>{{ label(p.billingCycle) }}</small></span
                  ></label
                >
              </div>
              <label v-if="plan" class="field section-gap"
                >Price entry<select v-model="priceId" required>
                  <option
                    v-for="price in plan.prices"
                    :key="price.priceEntryId"
                    :value="price.priceEntryId"
                  >
                    {{ money(price.amount, price.currencyCode) }} ·
                    {{ label(price.billingCycle) }} · from
                    {{ date(price.effectiveFrom) }}
                  </option>
                </select></label
              >
              <div v-if="plan?.addOns.length" class="section-gap">
                <h3>Compatible extensions</h3>
                <label
                  v-for="addon in plan.addOns"
                  :key="addon.code"
                  class="addon-choice"
                  ><input
                    type="checkbox"
                    v-model="addons"
                    :value="addon.code"
                    :disabled="!addon.selectable"
                  /><span>{{ addon.name }}</span
                  ><strong>{{
                    money(addon.price, addon.currencyCode)
                  }}</strong></label
                >
              </div>
              <div v-if="plan?.quotaPackages.length" class="section-gap">
                <h3>Additional capacity</h3>
                <label
                  v-for="q in plan.quotaPackages"
                  :key="q.code"
                  class="quantity-choice"
                  ><span
                    >{{ q.name
                    }}<small
                      >{{ q.capacityPerUnit }} {{ q.resource }} per unit</small
                    ></span
                  ><input
                    v-model.number="quantities[q.code]"
                    type="number"
                    min="0"
                    :max="q.maximumQuantity"
                    :aria-label="`${q.name} quantity`"
                    :disabled="!q.selectable"
                /></label>
              </div>
              <div class="notice section-gap">
                <Icon name="alert" />
                <p>
                  This is the complete target configuration. Unselected existing
                  add-ons and capacity packages will be removed. Options shown
                  are from the first selected customer; the review checks every
                  selected account.
                </p>
              </div></ResourceState
            ></template
          >
          <template v-else-if="step === 3"
            ><fieldset class="timing-options">
              <legend class="field-label">Subscription effective time</legend>
              <label
                class="timing-option"
                :class="{ chosen: timing === 'AT_RENEWAL' }"
                ><input type="radio" v-model="timing" value="AT_RENEWAL" /><Icon
                  name="calendar"
                /><span
                  ><strong>At each customer’s renewal</strong
                  ><small
                    >Keep the current period intact. The new terms begin at
                    renewal.</small
                  ></span
                ><span class="pill">Recommended</span></label
              ><label
                class="timing-option"
                :class="{ chosen: timing === 'IMMEDIATE' }"
                ><input type="radio" v-model="timing" value="IMMEDIATE" /><Icon
                  name="zap"
                /><span
                  ><strong>Immediately</strong
                  ><small
                    >Apply now. Some changes may require payment
                    confirmation.</small
                  ></span
                ></label
              >
            </fieldset>
            <div class="divider" />
            <label class="check-label"
              ><input type="checkbox" v-model="schedule" /><span
                >Schedule when the operation starts<small class="block muted"
                  >Effective subscription timing still follows the choice
                  above.</small
                ></span
              ></label
            ><label v-if="schedule" class="field section-gap"
              >Execution date & time<input
                v-model="executeAt"
                type="datetime-local"
                required
              /><small
                >Your browser’s local time. Sent to the backend as UTC.</small
              ></label
            ><label class="field section-gap"
              >Why are you making this change?<textarea
                v-model="reason"
                required
                minlength="5"
                maxlength="2000"
                placeholder="e.g. Move the October cohort to the new Growth plan at renewal."
              /><small
                >This reason stays with the operation and its audit
                history.</small
              ></label
            ></template
          >
          <template v-else-if="preview"
            ><div class="review-metrics">
              <div>
                <strong>{{ preview.targetCount }}</strong
                ><span>customers assessed</span>
              </div>
              <div>
                <strong class="positive">{{ preview.readyCount }}</strong
                ><span>ready to proceed</span>
              </div>
              <div>
                <strong :class="{ warning: preview.conflictCount }">{{
                  preview.conflictCount
                }}</strong
                ><span>blocked by conflicts</span>
              </div>
            </div>
            <div v-if="expired" class="notice error section-gap">
              <Icon name="clock" />
              <p>
                The preview has expired. Review again to get a current
                assessment.
              </p>
            </div>
            <div v-else class="notice success section-gap">
              <Icon name="shield" />
              <p>
                Reviewed {{ date(preview.evaluatedAt, true) }}. Valid until
                {{ date(preview.expiresAt, true) }}. The backend rechecks the
                selected accounts when it executes.
              </p>
            </div>
            <div class="review-results section-gap">
              <div
                v-for="(result, i) in preview.sample"
                :key="result.id"
                class="review-result"
              >
                <div class="row spread">
                  <strong>{{ `Result ${result.id.slice(0, 8)}` }}</strong
                  ><StatusBadge :status="result.status" />
                </div>
                <p>
                  {{ result.assessment.currentPlanCode || "No current plan" }} →
                  {{ result.assessment.targetPlanCode
                  }}<span v-if="result.assessment.targetPrice">
                    ·
                    {{
                      money(
                        result.assessment.targetPrice,
                        result.assessment.currencyCode || "MAD",
                      )
                    }}</span
                  >
                </p>
                <p
                  v-for="conflict in result.assessment.conflicts"
                  :key="conflict.code"
                  class="conflict-text"
                >
                  {{ conflict.message }}
                </p>
              </div>
            </div>
            <p class="small muted section-gap">
              Review includes selected results. All customer results are
              available in the operation after confirmation. Customer identities
              are independently permission protected.
            </p>
            <label v-if="preview.conflictCount" class="check-label section-gap"
              ><input type="checkbox" v-model="confirmPartial" /><span
                >I understand that {{ preview.conflictCount }} blocked customers
                will not be changed. Continue with eligible customers.</span
              ></label
            ><label class="check-label section-gap"
              ><input type="checkbox" v-model="confirmation" /><span
                >I reviewed the target configuration, effective timing and
                reason for this change.</span
              ></label
            ></template
          >
          <div v-if="error" class="notice error section-gap" role="alert">
            <Icon name="alert" />
            <p>{{ error }}</p>
          </div>
        </div>
        <div class="wizard-footer">
          <button
            class="button"
            :disabled="step === 1 || busy"
            @click="
              step--;
              error = '';
            "
          >
            <Icon name="back" :size="14" />Back</button
          ><span>{{ step }} of 4</span
          ><button
            v-if="step < 3"
            class="button primary"
            :disabled="!nextAllowed || busy"
            @click="step++"
          >
            Continue<Icon name="right" :size="14" /></button
          ><button
            v-else-if="step === 3 || expired"
            class="button primary"
            :disabled="busy || !validProduct || !ids.length"
            @click="review"
          >
            {{
              busy ? "Assessing…" : expired ? "Review again" : "Preview impact"
            }}<Icon name="shield" :size="14" /></button
          ><button
            v-else
            class="button primary"
            :disabled="
              busy ||
              !preview?.readyCount ||
              !confirmation ||
              !!(preview?.conflictCount && !confirmPartial)
            "
            @click="confirm"
          >
            {{
              busy
                ? "Confirming…"
                : schedule
                  ? "Confirm scheduled change"
                  : "Confirm change"
            }}<Icon name="check" :size="14" />
          </button>
        </div>
      </section>
      <aside class="change-summary">
        <span class="eyebrow">YOUR CHANGE</span>
        <h2>A clear plan of action.</h2>
        <div class="summary-line">
          <Icon name="customers" />
          <div>
            <small>Customers</small><strong>{{ ids.length }} selected</strong>
            <p v-if="selectedCustomers?.length">
              {{
                selectedCustomers
                  .slice(0, 3)
                  .map((c) => c.name)
                  .join(", ")
              }}{{ ids.length > 3 ? ` +${ids.length - 3} more` : "" }}
            </p>
          </div>
        </div>
        <div class="summary-line">
          <Icon name="catalog" />
          <div>
            <small>Target configuration</small
            ><strong>{{ plan?.name || "Choose a plan" }}</strong>
            <p v-if="selectedPrice">
              {{ money(selectedPrice.amount, selectedPrice.currencyCode) }} ·
              {{ label(selectedPrice.billingCycle) }}
            </p>
            <p>
              {{ addons.length }} add-ons ·
              {{ Object.values(quantities).filter((q) => q > 0).length }}
              capacity packages
            </p>
          </div>
        </div>
        <div class="summary-line">
          <Icon name="clock" />
          <div>
            <small>Effective timing</small
            ><strong>{{
              timing === "AT_RENEWAL" ? "At renewal" : "Immediately"
            }}</strong>
            <p>
              {{
                schedule && executeAt
                  ? `Operation starts ${date(new Date(executeAt).toISOString(), true)}`
                  : "Operation starts after confirmation"
              }}
            </p>
          </div>
        </div>
        <div class="summary-note">
          <Icon name="shield" :size="17" />
          <p>
            {{
              "Review expires automatically. Confirm before the expiry time."
            }}
          </p>
        </div>
      </aside>
    </div></template
  >
  <AppDialog
    :open="leaveOpen"
    title="Leave this change draft?"
    @close="leave(false)"
    ><p class="muted">
      Your configuration is saved in this browser session. You can return to the
      new change flow to continue.
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
