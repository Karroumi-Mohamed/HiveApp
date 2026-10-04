<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import ActionDialog from "./ActionDialog.vue";
import Pagination from "./Pagination.vue";
import ResourceState from "./ResourceState.vue";
import StatusBadge from "./StatusBadge.vue";
import { useResource } from "@/composables/useResource";
import { repricingApi, type RepricingState } from "@/api/repricing-api";
import {
  planApplicationApi,
  type ContentItemStatus,
} from "@/api/plan-application-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import { date, label, money } from "@/lib/format";
import { withReturnTo } from "@/lib/navigation";
import type { Action, RecordData, Section } from "@/resources/types";
import type { PageResponse } from "@/api/contracts";
const props = defineProps<{
  id: string;
  kind: "repricing" | "rollouts";
  detail?: RecordData;
  actions?: Section["actions"];
}>();
const emit = defineEmits<{ changed: [] }>();
const route = useRoute(),
  router = useRouter();
const permission = computed(() =>
  props.kind === "repricing" ? p.repricingResults : p.plansApplicationResults,
);
const identityPermission = computed(() =>
  props.kind === "repricing"
    ? p.repricingIdentities
    : p.plansApplicationIdentities,
);
const statuses = computed(() =>
  props.kind === "repricing"
    ? [
        "READY",
        "PENDING",
        "AWAITING_PAYMENT",
        "APPLIED",
        "CONFLICT",
        "CANCELLED",
      ]
    : [
        "ASSESSING",
        "READY",
        "WAITING",
        "APPLIED",
        "UNCHANGED",
        "EXCLUDED",
        "CONFLICT",
        "FAILED",
        "CANCELLED",
      ],
);
const status = computed(() =>
  statuses.value.includes(String(route.query.resultStatus))
    ? String(route.query.resultStatus)
    : "",
);
const page = computed(() => Math.max(0, Number(route.query.resultPage) || 0));
function filter(values: Record<string, string | number | undefined>) {
  void router.replace({ query: { ...route.query, ...values } });
}
const result = useResource<RecordData | undefined>(async () => {
  if (!can(permission.value)) return undefined;
  const records = await read<PageResponse<RecordData>>(permission.value, () =>
    props.kind === "repricing"
      ? repricingApi.results(
          props.id,
          page.value,
          (status.value as RepricingState) || undefined,
        )
      : planApplicationApi.results(
          props.id,
          page.value,
          (status.value as ContentItemStatus) || undefined,
        ),
  );
  let identities: RecordData[] = [],
    identityWarning = "";
  if (records.content.length && can(identityPermission.value)) {
    try {
      identities = await read(identityPermission.value, () =>
        props.kind === "repricing"
          ? repricingApi.identities(
              props.id,
              records.content.map((row) => row.id),
            )
          : planApplicationApi.identities(
              props.id,
              records.content.map((row) => row.id),
            ),
      );
    } catch {
      identityWarning =
        "Customer names could not be loaded. Outcomes remain available. Refresh to retry.";
    }
  }
  return {
    ...records,
    identityWarning,
    content: records.content.map((row) => ({
      ...row,
      ...identities.find((identity) => identity.itemId === row.id),
    })),
  };
}, [
  () => props.id,
  () => props.kind,
  page,
  status,
  () =>
    JSON.stringify([
      props.detail?.status,
      props.detail?.counts,
      props.detail?.pendingCount,
      props.detail?.appliedCount,
      props.detail?.conflictCount,
      props.detail?.awaitingPaymentCount,
    ]),
]);
const data = result.data;
const action = ref<Action>();
function availableActions(row: RecordData) {
  return (
    props
      .actions?.(row, props.detail || {})
      .filter(
        (item) => can(item.permission) && (!item.visible || item.visible(row)),
      ) || []
  );
}
function selectAction(item: Action, row: RecordData) {
  action.value = {
    ...item,
    defaults: () => item.defaults?.(row) || {},
    execute: (_, input, preview) => item.execute(row, input, preview),
    preview: item.preview ? (_, input) => item.preview!(row, input) : undefined,
    destination: item.destination
      ? (value) => item.destination!(value, row)
      : undefined,
  };
}
function issue(row: RecordData) {
  return (
    row.blocker ||
    row.executionConflicts
      ?.map((conflict: RecordData) => conflict.message || label(conflict.code))
      .join("; ") ||
    row.impact?.conflicts
      ?.map((conflict: RecordData) => conflict.message || label(conflict.code))
      .join("; ") ||
    (row.outcomeCode ? label(row.outcomeCode) : "—")
  );
}
</script>
<template>
  <section v-if="can(permission)" class="report-section">
    <header class="section-heading">
      <h2>Customer outcomes</h2>
      <button type="button" class="button small" @click="result.refresh()">
        Refresh
      </button>
    </header>
    <div class="resource-toolbar">
      <label class="inline-field"
        >Outcome<select
          :value="status"
          @change="
            filter({
              resultStatus:
                ($event.target as HTMLSelectElement).value || undefined,
              resultPage: undefined,
            })
          "
        >
          <option value="">All outcomes</option>
          <option v-for="value in statuses" :key="value" :value="value">
            {{ label(value) }}
          </option>
        </select></label
      >
    </div>
    <p v-if="data?.identityWarning" class="notice" role="status">
      {{ data.identityWarning }}
    </p>
    <ResourceState
      :loading="result.loading.value"
      :error="result.error.value"
      :empty="!data?.content.length"
      title="No matching outcomes"
      @retry="result.refresh()"
    >
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Customer</th>
              <th>Outcome</th>
              <th>
                {{ kind === "repricing" ? "Price change" : "Revision change" }}
              </th>
              <th>Issue</th>
              <th>Effective / updated</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data?.content" :key="row.id">
              <td>
                <RouterLink
                  v-if="row.accountId && can(p.subscriptionsReadChanges)"
                  class="text-link"
                  :to="
                    withReturnTo(
                      '/customers/' +
                        row.accountId +
                        '?view=changes' +
                        (row.operationId ? '&change=' + row.operationId : ''),
                      route.fullPath,
                    )
                  "
                  >{{ row.accountName }}</RouterLink
                ><span v-else>{{
                  row.accountName || "Identity unavailable"
                }}</span
                ><small v-if="!row.accountName" class="muted">{{
                  row.id
                }}</small>
              </td>
              <td><StatusBadge :status="row.status" /></td>
              <td v-if="kind === 'repricing'">
                <span
                  >{{ money(row.oldUnitPrice, row.currencyCode) }} →
                  {{ money(row.newUnitPrice, row.currencyCode) }}</span
                ><small class="muted"
                  >{{ row.quantity }} units ·
                  {{ label(row.billingCycle) }}</small
                >
              </td>
              <td v-else-if="row.impact">
                <span
                  >{{ row.impact.sourceVersion }} →
                  {{ row.impact.targetVersion }}</span
                ><small class="muted"
                  >Retained
                  {{
                    money(row.impact.retainedTotal, row.impact.currency)
                  }}</small
                >
              </td>
              <td v-else>—</td>
              <td>{{ issue(row) }}</td>
              <td>
                {{
                  date(
                    row.effectiveAt || row.completedAt || row.nextAttemptAt,
                    true,
                  )
                }}
              </td>
              <td>
                <details>
                  <summary>Inspect</summary>
                  <dl class="detail-list">
                    <div>
                      <dt>Result ID</dt>
                      <dd class="operation-id">{{ row.id }}</dd>
                    </div>
                    <template v-if="kind === 'repricing'"
                      ><div>
                        <dt>Total</dt>
                        <dd>
                          {{ money(row.oldTotal, row.currencyCode) }} →
                          {{ money(row.newTotal, row.currencyCode) }}
                        </dd>
                      </div>
                      <div>
                        <dt>Email</dt>
                        <dd>{{ label(row.delivery) }}</dd>
                      </div>
                      <div>
                        <dt>Email attempts</dt>
                        <dd>{{ row.emailAttempts }}</dd>
                      </div></template
                    ><template v-else
                      ><div>
                        <dt>Attempts</dt>
                        <dd>{{ row.attempts }}</dd>
                      </div>
                      <div>
                        <dt>Email</dt>
                        <dd>
                          {{
                            label(row.notice?.emailDelivery || "NOT_REQUESTED")
                          }}
                        </dd>
                      </div>
                      <div v-if="row.impact?.removedFeatures?.length">
                        <dt>Removed features</dt>
                        <dd>{{ row.impact.removedFeatures.join(", ") }}</dd>
                      </div>
                      <div v-if="row.impact?.addedFeatures?.length">
                        <dt>Added features</dt>
                        <dd>{{ row.impact.addedFeatures.join(", ") }}</dd>
                      </div>
                      <div
                        v-for="limit in row.impact?.afterLimits || []"
                        :key="limit.featureCode"
                      >
                        <dt>{{ limit.featureCode }}</dt>
                        <dd>
                          {{
                            limit.mode === "UNLIMITED"
                              ? "Unlimited"
                              : limit.effectiveLimit
                          }}<span v-if="limit.currentUsage !== undefined">
                            · {{ limit.currentUsage }} used</span
                          >
                        </dd>
                      </div></template
                    >
                  </dl>
                </details>
                <button
                  v-for="item in availableActions(row)"
                  :key="item.key"
                  type="button"
                  class="text-link"
                  @click="selectAction(item, row)"
                >
                  {{ item.label }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="data?.page || 0"
        :size="data?.size || 20"
        :total="data?.totalElements || 0"
        @change="filter({ resultPage: $event || undefined })"
      />
    </ResourceState>
  </section>
  <ActionDialog
    :action="action"
    :data="detail || {}"
    @close="action = undefined"
    @done="
      result.refresh();
      emit('changed');
    "
  />
</template>
