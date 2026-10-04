<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import type { RecordData } from "@/resources/types";
import type { OfferClientProduct } from "@/api/offer-contracts";
import { date, label, money } from "@/lib/format";
import { contextualPath } from "@/lib/navigation";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const props = defineProps<{ data: RecordData }>();
const route = useRoute();
const products = computed(() =>
  props.data.resolvedSelection
    ? [
        {
          kind: "Plan",
          product: props.data.resolvedSelection.plan as OfferClientProduct,
        },
        ...(props.data.resolvedSelection.addOns as OfferClientProduct[]).map(
          (product) => ({ kind: "Add-on", product }),
        ),
        ...(
          props.data.resolvedSelection.quotaPackages as OfferClientProduct[]
        ).map((product) => ({ kind: "Capacity", product })),
      ]
    : [],
);
const currency = computed(
  () => props.data.resolvedSelection?.plan.currencyCode,
);
const discount = computed(() => {
  const effect = props.data.effects;
  if (!effect || effect.discountType === "NONE") return "None";
  if (effect.discountType === "FIXED")
    return money(effect.discountAmount, currency.value);
  return (
    effect.percentage + "% · cap " + money(effect.percentageCap, currency.value)
  );
});
</script>

<template>
  <section class="offer-definition-summary">
    <p v-if="data._operationsOnly || data._restrictedSummary" class="muted">
      The offer definition is restricted for your account.
    </p>
    <template v-else>
      <dl class="offer-definition-terms">
        <div v-if="data.campaign">
          <dt>Campaign</dt>
          <dd>
            <RouterLink
              v-if="
                can(
                  p.campaignsRead,
                  p.campaignsReadOperations,
                  p.campaignsReadEditableDefinition,
                )
              "
              class="text-link"
              :to="
                contextualPath(
                  '/commercial/campaigns/' + data.campaign.id + '?view=offers',
                  route,
                )
              "
              >{{ data.campaign.name }}</RouterLink
            ><span v-else>{{ data.campaign.name }}</span
            ><small
              >{{ label(data.campaign.audienceMode) }} ·
              {{ label(data.campaign.status) }}</small
            >
          </dd>
        </div>
        <div>
          <dt>Offer window</dt>
          <dd>
            {{ date(data.startsAt, true) }} — {{ date(data.endsAt, true) }}
          </dd>
        </div>
        <div>
          <dt>Discovery</dt>
          <dd>
            {{ label(data.discovery)
            }}<small
              >Private code
              {{
                data.customerCodeConfigured ? "configured" : "not configured"
              }}</small
            >
          </dd>
        </div>
        <div>
          <dt>Acceptance</dt>
          <dd>{{ label(data.acceptance) }}</dd>
        </div>
        <div>
          <dt>Redemptions</dt>
          <dd>
            {{ data.globalLimit ?? "Unlimited" }} total ·
            {{ data.perAccountLimit ?? "Unlimited" }} per customer
          </dd>
        </div>
        <div>
          <dt>Applies</dt>
          <dd>{{ label(data.selection?.timing) }}</dd>
        </div>
      </dl>
      <div v-if="products.length" class="table-scroll">
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
              <td>
                {{
                  product.pricingMode === "FREE"
                    ? "Complimentary"
                    : money(product.amount, product.currencyCode) +
                      " / " +
                      label(product.billingCycle).toLowerCase()
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <dl class="offer-definition-terms">
        <div>
          <dt>Discount</dt>
          <dd>{{ discount }}</dd>
        </div>
        <div
          v-for="(bonus, index) in data.effects?.finiteQuotaBonuses || []"
          :key="index"
        >
          <dt>{{ label(bonus.resource) }}</dt>
          <dd>
            +{{ bonus.quantity }}<small>{{ bonus.featureCode }}</small>
          </dd>
        </div>
      </dl>
      <p v-if="data.description" class="offer-description">
        {{ data.description }}
      </p>
    </template>
  </section>
</template>

<style scoped>
.offer-definition-terms {
  margin: 0 0 26px;
}
.offer-definition-terms > div {
  display: flex;
  gap: 24px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
}
.offer-definition-terms dt {
  width: 165px;
  flex-shrink: 0;
  color: var(--muted);
}
.offer-definition-terms dd {
  margin: 0;
}
.offer-definition-terms dd small,
td small {
  display: block;
  color: var(--muted);
  margin-top: 4px;
  font-size: 12px;
}
.table-scroll + .offer-definition-terms {
  margin-top: 20px;
}
.offer-description {
  white-space: pre-wrap;
  max-width: 800px;
  color: var(--muted);
}
.muted {
  color: var(--muted);
}
@media (max-width: 600px) {
  .offer-definition-terms > div {
    display: block;
  }
  .offer-definition-terms dt {
    margin-bottom: 5px;
  }
}
</style>
