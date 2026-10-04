<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { adminApi as api } from "@/api/admin-api";
import type { PlanFeature, RegistryFeature } from "@/api/contracts";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import { errorMessage, label } from "@/lib/format";
const props = defineProps<{ planId: string }>();
const features = ref<PlanFeature[]>([]),
  registry = ref<RegistryFeature[]>([]);
const search = ref(""),
  mode = ref(""),
  loading = ref(false),
  error = ref("");
let version = 0;
async function load() {
  const request = ++version;
  loading.value = true;
  error.value = "";
  try {
    const [composition, definitions] = await Promise.allSettled([
      read(p.plansListFeatures, () => api.planFeatures(props.planId)),
      can(p.registryFeatureCatalog)
        ? read(p.registryFeatureCatalog, () =>
            api.featureCatalog("PLAN_ASSIGNABLE"),
          )
        : Promise.resolve([]),
    ]);
    if (request !== version) return;
    if (composition.status === "rejected") throw composition.reason;
    features.value = composition.value;
    registry.value =
      definitions.status === "fulfilled"
        ? definitions.value.flatMap((module) => module.features)
        : [];
  } catch (e) {
    if (request === version) error.value = errorMessage(e);
  } finally {
    if (request === version) loading.value = false;
  }
}
watch(() => props.planId, load, { immediate: true });
const counts = computed(() => ({
  included: features.value.filter((feature) => feature.mode === "INCLUDED")
    .length,
  optional: features.value.filter(
    (feature) => feature.mode === "OPTIONAL_ADD_ON",
  ).length,
  blocked: features.value.filter(
    (feature) => feature.mode === "BLOCKED_FOR_PLAN",
  ).length,
}));
const rows = computed(() =>
  features.value
    .map((feature) => {
      const definition = registry.value.find(
        (entry) => entry.code === feature.featureCode,
      );
      return {
        feature,
        definition,
        name: definition?.displayName || feature.featureCode,
      };
    })
    .filter(
      (row) =>
        (!mode.value || row.feature.mode === mode.value) &&
        (
          row.name +
          " " +
          row.feature.featureCode +
          " " +
          (row.definition?.moduleCode || "")
        )
          .toLowerCase()
          .includes(search.value.toLowerCase()),
    ),
);
function quotas(feature: PlanFeature, definition?: RegistryFeature) {
  return [
    ...new Set([
      ...(definition?.quotaSchema.map((quota) => quota.resource) || []),
      ...feature.quotaConfigs.map((quota) => quota.resource),
    ]),
  ].map((resource) => {
    const configured = feature.quotaConfigs.find(
      (quota) => quota.resource === resource,
    );
    const schema = definition?.quotaSchema.find(
      (quota) => quota.resource === resource,
    );
    return {
      resource,
      unit: schema?.unit,
      value:
        !configured ||
        (configured.mode === "FINITE" && configured.limit === null)
          ? "Not configured"
          : configured.mode === "UNLIMITED"
            ? "Unlimited"
            : configured.limit!.toLocaleString(),
    };
  });
}
</script>

<template>
  <section class="capability-inspector">
    <div class="capability-toolbar">
      <div class="capability-counts">
        <span
          ><strong>{{ counts.included }}</strong> Included</span
        ><span
          ><strong>{{ counts.optional }}</strong> Optional</span
        ><span
          ><strong>{{ counts.blocked }}</strong> Blocked</span
        >
      </div>
      <div class="capability-filters">
        <input
          v-model="search"
          type="search"
          aria-label="Find capability"
          placeholder="Find capability"
        /><select v-model="mode" aria-label="Feature mode">
          <option value="">All modes</option>
          <option value="INCLUDED">Included</option>
          <option value="OPTIONAL_ADD_ON">Optional add-on</option>
          <option value="BLOCKED_FOR_PLAN">Blocked</option>
        </select>
      </div>
    </div>
    <p v-if="error" role="alert" class="form-error">
      {{ error }} <button class="text-link" @click="load">Retry</button>
    </p>
    <p v-else-if="loading" class="muted">Loading capabilities…</p>
    <p v-else-if="!rows.length" class="muted">
      {{
        features.length
          ? "No matching capabilities."
          : "No features are assigned to this revision."
      }}
    </p>
    <div v-else class="capability-list">
      <details
        v-for="{ feature, definition, name } in rows"
        :key="feature.id"
        class="capability-row"
      >
        <summary>
          <div class="capability-name">
            <strong>{{ name }}</strong
            ><small
              >{{
                definition?.moduleCode
                  ? label(definition.moduleCode) + " · "
                  : ""
              }}{{ feature.featureCode }}</small
            >
          </div>
          <div class="capability-mode">
            <span>{{ label(feature.mode) }}</span
            ><small
              v-if="definition && !definition.runtimeEnabled"
              class="runtime-paused"
              >Runtime paused</small
            ><small v-if="definition && !definition.newSalesEnabled"
              >Unavailable for new sales</small
            >
          </div>
          <div class="capability-quota">
            <template v-if="feature.mode === 'INCLUDED'"
              ><span
                v-for="quota in quotas(feature, definition)"
                :key="quota.resource"
                >{{ label(quota.resource) }}: <strong>{{ quota.value }}</strong
                >{{
                  quota.value !== "Unlimited" &&
                  quota.value !== "Not configured" &&
                  quota.unit
                    ? " " + quota.unit
                    : ""
                }}</span
              ><span v-if="!quotas(feature, definition).length">{{
                definition
                  ? "No capacity limits"
                  : "No configured capacity limits"
              }}</span></template
            ><span v-else>{{
              feature.mode === "OPTIONAL_ADD_ON"
                ? "Requires a compatible add-on"
                : "Unavailable on this plan"
            }}</span>
          </div>
        </summary>
        <div class="capability-detail">
          <p v-if="definition?.description">{{ definition.description }}</p>
          <div
            v-if="feature.mode === 'INCLUDED' && definition?.permissions.length"
            class="table-scroll"
          >
            <table class="data-table">
              <caption>
                Permissions associated with this feature
              </caption>
              <thead>
                <tr>
                  <th>Permission</th>
                  <th>Resource</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="permission in definition.permissions"
                  :key="permission.code"
                >
                  <td>
                    {{ permission.name }}<small>{{ permission.code }}</small>
                  </td>
                  <td>{{ label(permission.resource) }}</td>
                  <td>{{ label(permission.action) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else-if="!definition" class="muted">
            Registry descriptions and permission details are unavailable for
            this account.
          </p>
          <p v-else-if="feature.mode !== 'INCLUDED'" class="muted">
            {{
              feature.mode === "OPTIONAL_ADD_ON"
                ? "The plan allows this feature through a compatible add-on. It is not included in the base plan."
                : "This plan blocks the feature and its extensions."
            }}
          </p>
          <p v-else class="muted">
            No permissions are associated with this feature.
          </p>
        </div>
      </details>
    </div>
  </section>
</template>

<style scoped>
.capability-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}
.capability-counts {
  display: flex;
  gap: 22px;
  color: var(--muted);
  white-space: nowrap;
}
.capability-counts strong {
  color: var(--text);
  margin-right: 5px;
}
.capability-filters {
  display: flex;
  gap: 12px;
}
.capability-filters input {
  max-width: 230px;
}
.capability-filters select {
  width: auto;
}
.capability-list {
  border-top: 1px solid var(--border);
}
.capability-row {
  border-bottom: 1px solid var(--border);
}
.capability-row summary {
  display: grid;
  grid-template-columns: minmax(230px, 1fr) minmax(180px, 0.7fr) minmax(
      220px,
      1fr
    );
  gap: 24px;
  padding: 20px 6px;
  cursor: pointer;
  align-items: start;
}
.capability-name {
  position: relative;
  padding-left: 20px;
}
.capability-name:before {
  content: "›";
  position: absolute;
  left: 0;
  top: -1px;
  color: var(--muted);
}
.capability-row[open] .capability-name:before {
  content: "⌄";
}
.capability-name strong {
  font-weight: 550;
}
.capability-name small,
.capability-mode small {
  display: block;
  color: var(--muted);
  font-size: 11px;
  margin-top: 4px;
}
.capability-quota {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}
.capability-quota strong {
  color: var(--text);
  font-weight: 500;
}
.capability-detail {
  padding: 0 24px 20px;
}
.capability-detail > p {
  margin: 0 0 14px;
  max-width: 750px;
}
.capability-detail td small {
  display: block;
  color: var(--muted);
  font-size: 11px;
}
.capability-detail caption {
  text-align: left;
  font-weight: 500;
  padding: 10px 0;
}
.muted {
  color: var(--muted);
}
.capability-mode .runtime-paused {
  color: var(--warning);
}
@media (max-width: 850px) {
  .capability-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .capability-row summary {
    grid-template-columns: minmax(190px, 1fr) 1fr;
  }
  .capability-quota {
    grid-column: 1/-1;
    padding-left: 20px;
  }
}
@media (max-width: 550px) {
  .capability-counts {
    gap: 15px;
  }
  .capability-filters {
    flex-wrap: wrap;
  }
  .capability-row summary {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .capability-mode {
    padding-left: 20px;
  }
  .capability-detail {
    padding-left: 20px;
    padding-right: 0;
  }
}
</style>
