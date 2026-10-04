<script setup lang="ts">
import { computed, watch } from "vue";
import { gateway } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import ResourceState from "./ResourceState.vue";
import { money, label } from "@/lib/format";
import type { RecordData } from "@/resources/types";
const props = defineProps<{ accountId: string; model: RecordData }>();
const catalog = useResource(
  () => gateway.catalog(props.accountId),
  [() => props.accountId],
);
const plan = computed(() =>
  catalog.data.value?.plans.find((p) => p.code === props.model.targetPlanCode),
);
watch(
  () => props.model.targetPlanCode,
  () => {
    props.model.addOnCodes = [];
    props.model.quotaPackages = [];
    props.model.planPriceSelection = null;
  },
);
function price(id: string) {
  const item = plan.value?.prices.find((p) => p.priceEntryId === id);
  props.model.planPriceSelection = item
    ? {
        priceEntryId: item.priceEntryId,
        currencyCode: item.currencyCode,
        billingCycle: item.billingCycle,
      }
    : null;
}
function quota(code: string, quantity: number) {
  props.model.quotaPackages = [
    ...(props.model.quotaPackages || []).filter(
      (q: any) => q.packageCode !== code,
    ),
    ...(quantity > 0 ? [{ packageCode: code, quantity }] : []),
  ];
}
</script>
<template>
  <ResourceState
    :loading="catalog.loading.value"
    :error="catalog.error.value"
    @retry="catalog.refresh()"
    ><div class="editor-grid">
      <label class="field"
        >Plan<select v-model="model.targetPlanCode" required>
          <option value="">Select plan</option>
          <option
            v-for="p in catalog.data.value?.plans"
            :key="p.code"
            :value="p.code"
            :disabled="!p.selectable"
          >
            {{ p.name }}
          </option>
        </select></label
      ><label v-if="plan" class="field"
        >Price<select
          :value="model.planPriceSelection?.priceEntryId || ''"
          required
          @change="price(($event.target as HTMLSelectElement).value)"
        >
          <option value="">Select price</option>
          <option v-for="p in plan.prices" :value="p.priceEntryId">
            {{ money(p.amount, p.currencyCode) }} · {{ label(p.billingCycle) }}
          </option>
        </select></label
      >
      <fieldset v-if="plan?.addOns.length" class="field full-width">
        <legend>Add-ons</legend>
        <label v-for="a in plan.addOns" class="check-label"
          ><input
            v-model="model.addOnCodes"
            type="checkbox"
            :value="a.code"
            :disabled="!a.selectable"
          />{{ a.name }} · {{ money(a.price, a.currencyCode) }}</label
        >
      </fieldset>
      <fieldset v-if="plan?.quotaPackages.length" class="field full-width">
        <legend>Capacity packages</legend>
        <label v-for="q in plan.quotaPackages" class="quantity-choice"
          ><span>{{ q.name }} · {{ q.capacityPerUnit }} {{ q.resource }}</span
          ><input
            type="number"
            min="0"
            :max="q.maximumQuantity"
            :disabled="!q.selectable"
            :value="
              model.quotaPackages?.find((x: any) => x.packageCode === q.code)
                ?.quantity || 0
            "
            :aria-label="q.name + ' quantity'"
            @input="
              quota(q.code, Number(($event.target as HTMLInputElement).value))
            "
        /></label>
      </fieldset>
      <label class="field"
        >Timing<select v-model="model.timing">
          <option value="IMMEDIATE">Immediately</option>
          <option value="AT_RENEWAL">At renewal</option>
        </select></label
      >
    </div></ResourceState
  >
</template>
