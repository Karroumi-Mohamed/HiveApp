<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import FieldInput from "./FieldInput.vue";
import StatusBadge from "./StatusBadge.vue";
import Pagination from "./Pagination.vue";
import { adminApi as api } from "@/api/admin-api";
import type {
  ComparedPlan,
  PlanFeature,
  RegistryFeature,
} from "@/api/contracts";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import { planField } from "@/resources/fields";
import type { Field, RecordData, Choice } from "@/resources/types";
import { date, money, label, errorMessage } from "@/lib/format";
import { contextualPath } from "@/lib/navigation";
const props = withDefaults(
  defineProps<{ planId: string; mode?: "catalog" | "revisions" }>(),
  { mode: "catalog" },
);
const route = useRoute();
const selection = reactive<RecordData>({ target: "", third: "" });
const plans = ref<ComparedPlan[]>([]),
  registry = ref<RegistryFeature[]>([]);
const busy = ref(false),
  error = ref(""),
  onlyDifferences = ref(false),
  query = ref("");
const pricesVisible = ref(false);
const revisionOptions = ref<Choice[]>([]),
  revisionPage = ref(0),
  revisionTotal = ref(0),
  revisionsLoading = ref(false);
const selectedRevision = ref<Choice>();
let revisionRequest = 0;
let request = 0;
const revisionChoice = computed<Field>(() => ({
  key: "target",
  label: "Revision in this family",
  type: "select",
  required: true,
  full: true,
  options: [
    ...revisionOptions.value,
    ...(selectedRevision.value &&
    !revisionOptions.value.some(
      (choice) => choice.value === selectedRevision.value?.value,
    )
      ? [selectedRevision.value]
      : []),
  ],
}));
const choice = computed(() =>
  props.mode === "revisions"
    ? revisionChoice.value
    : planField("target", "Compare with"),
);
const thirdChoice = {
  ...planField("third", "Third plan (optional)"),
  required: false,
};
const canChoose = computed(() =>
  can(props.mode === "revisions" ? p.plansListVersions : p.plansChoose),
);
async function loadRevisions(page = 0) {
  const current = ++revisionRequest;
  revisionPage.value = page;
  revisionsLoading.value = true;
  try {
    const result = await read(p.plansListVersions, () =>
      api.planVersions(props.planId, { page, size: 20 }),
    );
    if (current !== revisionRequest) return;
    revisionTotal.value = result.versions.totalElements;
    revisionOptions.value = result.versions.content
      .filter((plan) => plan.id !== props.planId)
      .map((plan) => ({
        value: plan.id,
        label: plan.name + " · revision " + plan.revisionNumber,
      }));
  } catch (e) {
    if (current === revisionRequest) error.value = errorMessage(e);
  } finally {
    if (current === revisionRequest) revisionsLoading.value = false;
  }
}
async function compare() {
  error.value = "";
  const ids = [
    props.planId,
    selection.target,
    ...(props.mode === "catalog" && selection.third ? [selection.third] : []),
  ];
  if (ids.some((id) => !id) || new Set(ids).size !== ids.length) {
    error.value = "Choose distinct plans to compare.";
    return;
  }
  const current = ++request;
  const matchesSelection = () =>
    ids.join(",") ===
    [
      props.planId,
      selection.target,
      ...(props.mode === "catalog" && selection.third ? [selection.third] : []),
    ].join(",");
  busy.value = true;
  try {
    if (props.mode === "revisions") {
      const result = await read(p.plansCompareVersions, () =>
        api.comparePlanVersions(props.planId, selection.target),
      );
      if (current !== request || !matchesSelection()) return;
      selectedRevision.value = {
        value: result.target.id,
        label:
          result.target.name +
          " · revision " +
          result.target.productVersionNumber,
      };
      plans.value = [
        {
          plan: result.source,
          features: result.features.flatMap((feature) =>
            feature.before ? [feature.before] : [],
          ),
          currentPrices: result.sourcePrices,
          scheduledPrices: [],
        },
        {
          plan: result.target,
          features: result.features.flatMap((feature) =>
            feature.after ? [feature.after] : [],
          ),
          currentPrices: result.targetPrices,
          scheduledPrices: [],
        },
      ];
      pricesVisible.value = result.pricesVisible;
    } else {
      const result = await read(p.plansCompare, () => api.comparePlans(ids));
      if (current !== request || !matchesSelection()) return;
      plans.value = result.plans;
      pricesVisible.value = result.pricesVisible;
    }
  } catch (e) {
    if (current === request && matchesSelection())
      error.value = errorMessage(e);
  } finally {
    if (current === request) busy.value = false;
  }
}
watch(
  () => [props.planId, props.mode, route.query.compare] as const,
  () => {
    request++;
    plans.value = [];
    busy.value = false;
    selection.target =
      typeof route.query.compare === "string" ? route.query.compare : "";
    selection.third = "";
    selectedRevision.value = undefined;
    revisionOptions.value = [];
    revisionRequest++;
    if (props.mode === "revisions" && canChoose.value) void loadRevisions();
    if (selection.target) void compare();
  },
  { immediate: true },
);
watch(
  () => [selection.target, selection.third],
  () => {
    const option = revisionOptions.value.find(
      (option) => option.value === selection.target,
    );
    if (option) selectedRevision.value = option;
    plans.value = [];
  },
);
watch(
  () => props.planId,
  async () => {
    registry.value = [];
    if (can(p.registryFeatureCatalog)) {
      try {
        registry.value = (
          await read(p.registryFeatureCatalog, () =>
            api.featureCatalog("PLAN_ASSIGNABLE"),
          )
        ).flatMap((module) => module.features);
      } catch {
        /* A registry label failure must not hide authorized plan comparisons. */
      }
    }
  },
  { immediate: true },
);
function signature(feature?: PlanFeature) {
  if (!feature) return "NOT_INCLUDED";
  return JSON.stringify([
    feature.mode,
    ...(feature.mode === "INCLUDED"
      ? [...feature.quotaConfigs]
          .sort((a, b) => a.resource.localeCompare(b.resource))
          .map((quota) => [quota.resource, quota.mode, quota.limit])
      : []),
  ]);
}
const featureRows = computed(() =>
  [
    ...new Set(
      plans.value.flatMap((plan) =>
        plan.features.map((feature) => feature.featureCode),
      ),
    ),
  ]
    .map((code) => {
      const definition = registry.value.find(
        (feature) => feature.code === code,
      );
      const cells = plans.value.map((plan) =>
        plan.features.find((feature) => feature.featureCode === code),
      );
      return {
        code,
        name: definition?.displayName || code,
        definition,
        cells,
        changed: new Set(cells.map(signature)).size > 1,
      };
    })
    .filter(
      (row) =>
        (!onlyDifferences.value || row.changed) &&
        (row.name + " " + row.code)
          .toLowerCase()
          .includes(query.value.toLowerCase()),
    ),
);
function quotas(feature: PlanFeature, definition?: RegistryFeature) {
  if (feature.mode !== "INCLUDED") return [];
  return [
    ...new Set([
      ...(definition?.quotaSchema.map((quota) => quota.resource) || []),
      ...feature.quotaConfigs.map((quota) => quota.resource),
    ]),
  ].map((resource) => {
    const quota = feature.quotaConfigs.find(
      (quota) => quota.resource === resource,
    );
    const unit = definition?.quotaSchema.find(
      (quota) => quota.resource === resource,
    )?.unit;
    return (
      label(resource) +
      ": " +
      (!quota
        ? "Not configured"
        : quota.mode === "UNLIMITED"
          ? "Unlimited"
          : quota.limit === null
            ? "Not configured"
            : quota.limit.toLocaleString() + (unit ? " " + unit : ""))
    );
  });
}
const differenceCount = computed(
  () =>
    [
      ...new Set(
        plans.value.flatMap((plan) =>
          plan.features.map((feature) => feature.featureCode),
        ),
      ),
    ].filter(
      (code) =>
        new Set(
          plans.value.map((plan) =>
            signature(
              plan.features.find((feature) => feature.featureCode === code),
            ),
          ),
        ).size > 1,
    ).length,
);
</script>

<template>
  <section class="plan-comparison">
    <form class="comparison-selection" @submit.prevent="compare">
      <div v-if="canChoose" class="comparison-choices">
        <div class="revision-selector">
          <FieldInput :field="choice" :data="selection" /><Pagination
            v-if="mode === 'revisions' && revisionTotal > 20"
            :page="revisionPage"
            :size="20"
            :total="revisionTotal"
            @change="loadRevisions"
          /><small v-if="revisionsLoading">Loading revisions…</small
          ><small v-else-if="mode === 'revisions' && revisionTotal <= 1"
            >No other revisions exist in this family.</small
          >
        </div>
        <FieldInput
          v-if="mode === 'catalog'"
          :field="thirdChoice"
          :data="selection"
        />
      </div>
      <p v-else class="muted">
        {{ mode === "revisions" ? "Family revision listing" : "Plan chooser" }}
        permission is required to select a comparison.
      </p>
      <button class="button primary" :disabled="busy || !selection.target">
        {{
          busy
            ? "Comparing…"
            : mode === "revisions"
              ? "Compare revisions"
              : "Compare plans"
        }}
      </button>
    </form>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <template v-if="plans.length">
      <div class="comparison-toolbar">
        <span>{{ differenceCount }} feature differences</span>
        <div>
          <input
            v-model="query"
            type="search"
            aria-label="Find a feature"
            placeholder="Find feature"
          /><label
            ><input v-model="onlyDifferences" type="checkbox" /> Differences
            only</label
          >
        </div>
      </div>
      <div class="table-scroll">
        <table class="data-table comparison-table">
          <thead>
            <tr>
              <th>Terms</th>
              <th v-for="({ plan }, index) in plans" :key="plan.id">
                <RouterLink
                  :to="contextualPath('/catalog/plans/' + plan.id, route)"
                  >{{ plan.name }}</RouterLink
                ><small
                  >{{ index === 0 ? "Current selection · " : "" }}Revision
                  {{ plan.productVersionNumber }} · {{ plan.code }}</small
                ><StatusBadge :status="plan.status" />
              </th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>Sales visibility</th>
              <td v-for="{ plan } in plans" :key="plan.id">
                {{ label(plan.salesVisibility) }}
              </td>
            </tr>
            <tr>
              <th>Extensions</th>
              <td v-for="{ plan } in plans" :key="plan.id">
                {{ label(plan.extensionPolicy) }}
              </td>
            </tr>
            <tr v-if="pricesVisible">
              <th>{{ mode === "revisions" ? "Prices" : "Current prices" }}</th>
              <td v-for="plan in plans" :key="plan.plan.id">
                <div
                  v-for="price in plan.currentPrices"
                  :key="price.id"
                  class="price-line"
                >
                  {{ money(price.amount, price.currencyCode) }} /
                  {{ label(price.billingCycle).toLowerCase()
                  }}<small
                    >{{ date(price.effectiveFrom) }} —
                    {{
                      date(price.effectiveUntil) === "—"
                        ? "Ongoing"
                        : date(price.effectiveUntil)
                    }}</small
                  >
                </div>
                <span v-if="!plan.currentPrices.length">No prices</span>
              </td>
            </tr>
            <tr
              v-if="
                pricesVisible &&
                mode === 'catalog' &&
                plans.some((plan) => plan.scheduledPrices.length)
              "
            >
              <th>Scheduled prices</th>
              <td v-for="plan in plans" :key="plan.plan.id">
                <div
                  v-for="price in plan.scheduledPrices"
                  :key="price.id"
                  class="price-line"
                >
                  {{ money(price.amount, price.currencyCode) }} /
                  {{ label(price.billingCycle).toLowerCase()
                  }}<small>From {{ date(price.effectiveFrom) }}</small>
                </div>
                <span v-if="!plan.scheduledPrices.length">None</span>
              </td>
            </tr>
            <tr
              v-for="row in featureRows"
              :key="row.code"
              :class="{ changed: row.changed }"
            >
              <th>
                {{ row.name }}<small>{{ row.code }}</small
                ><span v-if="row.changed" class="difference-label"
                  >Changed</span
                >
              </th>
              <td v-for="(feature, index) in row.cells" :key="index">
                <strong>{{
                  feature ? label(feature.mode) : "Not included"
                }}</strong
                ><small
                  v-for="quota in feature
                    ? quotas(feature, row.definition)
                    : []"
                  :key="quota"
                  >{{ quota }}</small
                >
              </td>
            </tr>
            <tr v-if="!featureRows.length">
              <td :colspan="plans.length + 1">
                {{
                  onlyDifferences
                    ? "No matching feature differences."
                    : "No matching features."
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!pricesVisible" class="muted">
        Prices are restricted for your account.
      </p>
    </template>
  </section>
</template>

<style scoped>
.comparison-selection {
  display: flex;
  align-items: end;
  gap: 18px;
  margin-bottom: 25px;
}
.comparison-choices {
  display: flex;
  gap: 18px;
  flex: 1;
}
.comparison-choices > * {
  min-width: 0;
  flex: 1;
}
.comparison-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.comparison-toolbar > div {
  display: flex;
  align-items: center;
  gap: 18px;
}
.comparison-toolbar input[type="search"] {
  max-width: 220px;
}
.comparison-toolbar label {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.comparison-table {
  table-layout: fixed;
  min-width: 650px;
  width: 100%;
  white-space: normal;
}
.comparison-table th {
  text-transform: none;
  vertical-align: top;
}
.comparison-table th:first-child {
  width: 25%;
}
.comparison-table td {
  vertical-align: top;
}
.comparison-table small {
  display: block;
  color: var(--muted);
  font-weight: 400;
  margin-top: 6px;
}
.comparison-table thead th {
  font-size: 14px;
  background: var(--surface);
}
.comparison-table thead a {
  text-decoration: underline;
}
.comparison-table thead small {
  margin-bottom: 10px;
}
.comparison-table td strong {
  font-weight: 500;
}
.changed > th,
.changed > td {
  background: var(--accent-soft);
}
.difference-label {
  display: inline-block;
  font-size: 10px;
  color: var(--accent);
  font-weight: 500;
  margin-top: 7px;
}
.price-line + .price-line {
  margin-top: 13px;
}
.muted {
  color: var(--muted);
}
@media (max-width: 750px) {
  .comparison-selection,
  .comparison-choices,
  .comparison-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .comparison-toolbar > div {
    flex-wrap: wrap;
  }
}
</style>
