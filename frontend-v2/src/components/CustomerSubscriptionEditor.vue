<script setup lang="ts">
import { computed, watch } from "vue";
import type {
  ClientPlanCatalog,
  SubscriptionChangeInput,
} from "@/api/contracts";
import type { RecordData } from "@/resources/types";
import { gateway } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import { money, label } from "@/lib/format";
import ResourceState from "./ResourceState.vue";

const props = withDefaults(
  defineProps<{
    accountId: string;
    model: RecordData;
    initialize?: boolean;
    population?: boolean;
  }>(),
  { initialize: true, population: false },
);
const emit = defineEmits<{
  ready: [valid: boolean];
  catalog: [catalog: ClientPlanCatalog];
}>();
const catalog = useResource(
  () =>
    props.accountId
      ? gateway.catalog(props.accountId)
      : Promise.resolve(undefined),
  [() => props.accountId],
);
const current = computed(() => catalog.data.value?.currentSubscription);
const plan = computed(() =>
  catalog.data.value?.plans.find((p) => p.code === props.model.targetPlanCode),
);
const selectedPrice = computed(() =>
  plan.value?.prices.find(
    (p) => p.priceEntryId === props.model.planPriceSelection?.priceEntryId,
  ),
);
let initializedAccount = "";
watch(
  catalog.data,
  (value) => {
    if (!value) return;
    emit("catalog", value);
    if (initializedAccount === props.accountId) return;
    initializedAccount = props.accountId;
    if (!props.initialize || props.population || !value.currentSubscription)
      return;
    const held = value.currentSubscription;
    if (!props.model.targetPlanCode) props.model.targetPlanCode = held.planCode;
    props.model.addOnCodes = [...held.addOnCodes];
    props.model.quotaPackages = held.quotaPackages.map((q) => ({ ...q }));
    const target = value.plans.find(
      (p) => p.code === props.model.targetPlanCode,
    );
    const price =
      target?.prices.find((p) => p.priceEntryId === held.planPriceEntryId) ||
      target?.prices.find(
        (p) =>
          p.currencyCode === held.currentPriceCurrencyCode &&
          p.billingCycle === held.billingCycle,
      );
    if (price) setPrice(price.priceEntryId);
  },
  { immediate: true },
);
watch(plan, (value) => {
  if (!value) return;
  if (
    !value.prices.some(
      (p) => p.priceEntryId === props.model.planPriceSelection?.priceEntryId,
    )
  ) {
    const matching = value.prices.find(
      (p) =>
        p.currencyCode === current.value?.currentPriceCurrencyCode &&
        p.billingCycle === current.value?.billingCycle,
    );
    setPrice((matching || value.prices[0])?.priceEntryId || "");
  }
});
function setPrice(id: string) {
  const price = plan.value?.prices.find((p) => p.priceEntryId === id);
  props.model.planPriceSelection = price
    ? {
        priceEntryId: price.priceEntryId,
        currencyCode: price.currencyCode,
        billingCycle: price.billingCycle,
      }
    : null;
}
function matching(
  prices: Array<{ currencyCode: string; billingCycle: string }>,
) {
  return (
    !!selectedPrice.value &&
    prices.some(
      (p) =>
        p.currencyCode === selectedPrice.value!.currencyCode &&
        p.billingCycle === selectedPrice.value!.billingCycle,
    )
  );
}
const addOnChoices = computed(
  () => plan.value?.addOns.filter((a) => matching(a.prices)) || [],
);
const quotaChoices = computed(
  () =>
    plan.value?.quotaPackages.filter(
      (q) =>
        matching(q.prices) &&
        (q.directlyAvailable ||
          q.requiresAddOnCodes.some((code) =>
            props.model.addOnCodes.includes(code),
          )),
    ) || [],
);
const retainedAddOns = computed(() =>
  (current.value?.retainedAddOns || []).filter(
    (a) => !addOnChoices.value.some((choice) => choice.code === a.code),
  ),
);
const retainedQuotas = computed(() =>
  (current.value?.retainedQuotaPackages || []).filter(
    (q) => !quotaChoices.value.some((choice) => choice.code === q.code),
  ),
);
const unavailableAddOns = computed(() =>
  (props.model.addOnCodes as string[]).filter(
    (code) =>
      !addOnChoices.value.some((a) => a.code === code) &&
      !retainedAddOns.value.some((a) => a.code === code),
  ),
);
const unavailableQuotas = computed(() =>
  (
    props.model.quotaPackages as SubscriptionChangeInput["quotaPackages"]
  ).filter(
    (q) =>
      !quotaChoices.value.some((a) => a.code === q.packageCode) &&
      !retainedQuotas.value.some((a) => a.code === q.packageCode),
  ),
);
function quantity(code: string) {
  return (
    props.model.quotaPackages.find((q: any) => q.packageCode === code)
      ?.quantity || 0
  );
}
function setQuantity(code: string, value: number) {
  props.model.quotaPackages = [
    ...props.model.quotaPackages.filter((q: any) => q.packageCode !== code),
    ...(value > 0 ? [{ packageCode: code, quantity: value }] : []),
  ];
}
function removeAddOn(code: string) {
  props.model.addOnCodes = props.model.addOnCodes.filter(
    (item: string) => item !== code,
  );
}
function heldAddOn(code: string) {
  return plan.value?.current && !props.population
    ? current.value?.retainedAddOns.find((a) => a.code === code)
    : undefined;
}
function heldQuota(code: string) {
  return plan.value?.current && !props.population
    ? current.value?.retainedQuotaPackages.find((q) => q.code === code)
    : undefined;
}
const valid = computed(
  () =>
    !!selectedPrice.value &&
    !!plan.value &&
    (plan.value.selectable || (!props.population && plan.value.current)) &&
    props.model.quotaPackages.every(
      (q: any) => Number.isInteger(q.quantity) && q.quantity > 0,
    ),
);
watch(valid, (value) => emit("ready", value), { immediate: true });
</script>
<template>
  <ResourceState
    :loading="catalog.loading.value"
    :error="catalog.error.value"
    :empty="!catalog.data.value?.plans.length"
    title="No change options available"
    @retry="catalog.refresh()"
  >
    <div v-if="current && !population" class="current-terms">
      <span class="muted small">Current terms</span>
      <strong
        >{{ current.planCode }} ·
        {{ money(current.currentPrice, current.currentPriceCurrencyCode) }} ·
        {{ label(current.billingCycle || "") }}</strong
      >
      <span class="small muted"
        >{{ current.addOnCodes.length }} add-ons ·
        {{ current.quotaPackages.length }} capacity packages</span
      >
    </div>
    <div class="editor-grid">
      <label class="field"
        >Plan<select v-model="model.targetPlanCode" required>
          <option value="">Choose a plan</option>
          <option
            v-for="p in catalog.data.value?.plans"
            :key="p.code"
            :value="p.code"
            :disabled="
              (!p.selectable && !(p.current && !population)) || !p.prices.length
            "
          >
            {{ p.name }}{{ p.current && !population ? " · current" : "" }}
          </option>
        </select></label
      >
      <label class="field"
        >Price<select
          :value="model.planPriceSelection?.priceEntryId || ''"
          required
          @change="setPrice(($event.target as HTMLSelectElement).value)"
        >
          <option value="">Choose a price</option>
          <option
            v-for="p in plan?.prices"
            :key="p.priceEntryId"
            :value="p.priceEntryId"
          >
            {{ money(p.amount, p.currencyCode) }} · {{ label(p.billingCycle) }}
          </option>
        </select></label
      >
    </div>
    <div
      v-if="
        addOnChoices.length || retainedAddOns.length || unavailableAddOns.length
      "
      class="section-gap"
    >
      <h3>Add-ons</h3>
      <label v-for="a in addOnChoices" :key="a.code" class="holding-row">
        <input
          v-model="model.addOnCodes"
          type="checkbox"
          :value="a.code"
          :disabled="
            heldAddOn(a.code) ? !heldAddOn(a.code)!.removable : !a.selectable
          "
        />
        <span
          ><strong>{{ a.name }}</strong
          ><small v-if="a.dependencyCodes.length"
            >Requires {{ a.dependencyCodes.join(", ") }}</small
          ><small v-if="a.exclusionCodes.length"
            >Cannot combine with {{ a.exclusionCodes.join(", ") }}</small
          ><small v-if="heldAddOn(a.code)"
            >Held version {{ heldAddOn(a.code)!.definitionVersion }}</small
          ></span
        >
        <span class="muted small">{{
          money(
            heldAddOn(a.code)?.unitPrice ||
              a.prices.find((p) => matching([p]))?.amount ||
              a.price,
            heldAddOn(a.code)?.currencyCode ||
              model.planPriceSelection?.currencyCode ||
              a.currencyCode,
          )
        }}</span>
      </label>
      <label v-for="a in retainedAddOns" :key="a.code" class="holding-row">
        <input
          v-model="model.addOnCodes"
          type="checkbox"
          :value="a.code"
          :disabled="plan?.current && !a.removable"
        />
        <span
          ><strong>{{ a.name }}</strong
          ><small
            >{{ label(a.state) }} · held version {{ a.definitionVersion
            }}{{ !a.removable ? " · removal restricted" : "" }}</small
          ></span
        >
        <span class="pill">Retained</span>
      </label>
      <div v-for="code in unavailableAddOns" :key="code" class="holding-row">
        <span
          ><strong>{{ label(code) }}</strong
          ><small
            >Selected previously; unavailable in this plan's options. Review
            checks compatibility.</small
          ></span
        >
        <button type="button" class="text-link" @click="removeAddOn(code)">
          Remove
        </button>
      </div>
    </div>
    <div
      v-if="
        quotaChoices.length || retainedQuotas.length || unavailableQuotas.length
      "
      class="section-gap"
    >
      <h3>Capacity packages</h3>
      <label v-for="q in quotaChoices" :key="q.code" class="holding-row">
        <span
          ><strong>{{ q.name }}</strong
          ><small
            >{{ q.capacityPerUnit }} {{ q.resource }} per unit</small
          ></span
        >
        <input
          type="number"
          :min="heldQuota(q.code) && !heldQuota(q.code)!.removable ? 1 : 0"
          :max="
            heldQuota(q.code)
              ? (heldQuota(q.code)!.maximumSelectableQuantity ??
                heldQuota(q.code)!.quantity)
              : q.maximumQuantity
          "
          :disabled="
            heldQuota(q.code)
              ? !heldQuota(q.code)!.quantityEditable
              : !q.selectable
          "
          :value="quantity(q.code)"
          :aria-label="q.name + ' quantity'"
          @input="
            setQuantity(
              q.code,
              Number(($event.target as HTMLInputElement).value),
            )
          "
        />
        <button
          v-if="
            heldQuota(q.code)?.removable && !heldQuota(q.code)?.quantityEditable
          "
          type="button"
          class="text-link"
          @click="
            setQuantity(
              q.code,
              quantity(q.code) ? 0 : heldQuota(q.code)!.quantity,
            )
          "
        >
          {{ quantity(q.code) ? "Remove" : "Keep" }}
        </button>
      </label>
      <label v-for="q in retainedQuotas" :key="q.code" class="holding-row">
        <span
          ><strong>{{ q.name }}</strong
          ><small
            >{{ label(q.state) }} · {{ q.capacityPerUnit }} {{ q.resource }} per
            unit{{ !q.quantityEditable ? " · quantity fixed" : "" }}</small
          ></span
        >
        <input
          type="number"
          :min="plan?.current && !q.removable ? 1 : 0"
          :max="q.maximumSelectableQuantity ?? q.quantity"
          :disabled="plan?.current && !q.quantityEditable"
          :value="quantity(q.code)"
          :aria-label="q.name + ' retained quantity'"
          @input="
            setQuantity(
              q.code,
              Number(($event.target as HTMLInputElement).value),
            )
          "
        />
        <button
          v-if="q.removable"
          type="button"
          class="text-link"
          @click="setQuantity(q.code, quantity(q.code) ? 0 : q.quantity)"
        >
          {{ quantity(q.code) ? "Remove" : "Keep" }}
        </button>
      </label>
      <div
        v-for="q in unavailableQuotas"
        :key="q.packageCode"
        class="holding-row"
      >
        <span
          ><strong>{{ label(q.packageCode) }} · {{ q.quantity }} units</strong
          ><small
            >Unavailable in this plan's options. Review checks
            compatibility.</small
          ></span
        >
        <button
          type="button"
          class="text-link"
          @click="setQuantity(q.packageCode, 0)"
        >
          Remove
        </button>
      </div>
    </div>
    <p class="small muted section-gap">
      Changing the plan preserves your selections. Remove unwanted holdings
      explicitly; the review checks eligibility, usage and retained terms.
    </p>
  </ResourceState>
</template>
<style scoped>
.current-terms {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--border);
}
.current-terms strong {
  font-size: 12px;
}
.holding-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
}
.holding-row > span:first-of-type {
  flex: 1;
}
.holding-row strong {
  display: block;
  font-size: 12px;
}
.holding-row small {
  display: block;
  color: var(--muted);
  margin-top: 4px;
  font-size: 10px;
}
.holding-row input[type="number"] {
  width: 85px;
}
</style>
