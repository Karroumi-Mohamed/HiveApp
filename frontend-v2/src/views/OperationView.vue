<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import Icon from "@/components/Icon.vue";
import ResourceState from "@/components/ResourceState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import AppDialog from "@/components/AppDialog.vue";
import Pagination from "@/components/Pagination.vue";
import { gateway } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import { date, money, label, errorMessage } from "@/lib/format";
import { can, session, notify } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const route = useRoute();
const id = computed(() => String(route.params.id));
const job = useResource(
  () => gateway.job(id.value),
  [id],
  3000,
  () => active.value,
);
const data = job.data;
const active = computed<boolean>((): boolean =>
  ["QUEUED", "RUNNING", "SCHEDULED"].includes(data.value?.summary.status || ""),
);
const resultPage = ref(0);
const results = useResource(
  () => gateway.jobResults(id.value, resultPage.value),
  [id, resultPage],
  3000,
  () => active.value,
);
const rows = results.data;
const identities = useResource(
  () =>
    can(p.subscriptionsReadChangeJobResultIdentities) &&
    rows.value?.content.length
      ? gateway.jobIdentities(
          id.value,
          rows.value.content.map((r) => r.id),
        )
      : Promise.resolve([]),
  [id, () => rows.value?.content.map((r) => r.id).join(",")],
);
const identityMap = computed(
  () => new Map(identities.data.value?.map((i) => [i.itemId, i])),
);
watch(
  () => data.value?.summary.status,
  () => void results.refresh(true),
);
const progress = computed(() =>
  data.value
    ? Math.round(
        Math.min(
          1,
          (data.value.summary.appliedCount +
            data.value.summary.pendingCount +
            data.value.summary.awaitingPaymentCount +
            data.value.summary.failedCount +
            data.value.summary.conflictCount +
            data.value.summary.cancelledCount) /
            Math.max(1, data.value.summary.targetCount),
        ) * 100,
      )
    : 0,
);
const action = ref<"cancel" | "retry" | null>(null);
const reason = ref("");
const error = ref("");
const busy = ref(false);
async function execute() {
  if (!action.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (action.value === "cancel")
      await gateway.cancelJob(id.value, reason.value.trim());
    else await gateway.retryJob(id.value, reason.value.trim());
    notify(
      action.value === "cancel" ? "Operation cancelled." : "Retry requested.",
    );
    action.value = null;
    reason.value = "";
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <RouterLink class="back-link" to="/operations"
    ><Icon name="back" :size="14" />All executions</RouterLink
  ><PageHeading :title="'Subscription change'"
    ><StatusBadge v-if="data" :status="data.summary.status" /><button
      v-if="active && can(p.subscriptionsCancelChangeJob)"
      class="button"
      @click="
        action = 'cancel';
        error = '';
      "
    >
      Cancel execution</button
    ><button
      v-if="
        data?.summary.status === 'COMPLETED_WITH_ERRORS' &&
        can(p.subscriptionsRetryChangeJob)
      "
      class="button primary"
      @click="
        action = 'retry';
        error = '';
      "
    >
      Retry recoverable items
    </button></PageHeading
  ><ResourceState
    :loading="job.loading.value"
    :error="job.error.value"
    @retry="job.refresh()"
    ><template v-if="data"
      ><div class="execution-progress panel">
        <div class="row spread">
          <div>
            <h2>
              {{
                active ? "Execution in progress" : label(data.summary.status)
              }}
            </h2>
            <p>
              {{ active ? "Auto refresh" : "Completed" }}
            </p>
          </div>
          <strong class="progress-number">{{ progress }}%</strong>
        </div>
        <div class="progress-track">
          <span :style="{ width: `${progress}%` }" />
        </div>
      </div>
      <div class="metrics">
        <div class="metric">
          <p class="metric-label">Assessed customers</p>
          <p class="metric-value">{{ data.summary.targetCount }}</p>
          <p class="metric-foot">
            {{ data.summary.readyCount }} initially ready
          </p>
        </div>
        <div class="metric">
          <p class="metric-label">Applied</p>
          <p class="metric-value">{{ data.summary.appliedCount }}</p>
          <p class="metric-foot">Changes successfully executed</p>
        </div>
        <div class="metric">
          <p class="metric-label">Waiting</p>
          <p class="metric-value">
            {{ data.summary.pendingCount + data.summary.awaitingPaymentCount }}
          </p>
          <p class="metric-foot">Renewal or payment confirmation</p>
        </div>
        <div class="metric">
          <p class="metric-label">Exceptions</p>
          <p class="metric-value">
            {{ data.summary.conflictCount + data.summary.failedCount }}
          </p>
          <p class="metric-foot">Conflicts and failures</p>
        </div>
      </div>
      <div class="equal-columns">
        <section class="panel">
          <div class="panel-header"><h2>Target configuration</h2></div>
          <div class="panel-body">
            <dl class="detail-list">
              <div>
                <dt>Plan</dt>
                <dd>{{ data.selection.targetPlanCode }}</dd>
              </div>
              <div>
                <dt>Timing</dt>
                <dd>{{ label(data.selection.timing) }}</dd>
              </div>
              <div>
                <dt>Price currency / cycle</dt>
                <dd>
                  {{ data.selection.planPriceSelection.currencyCode }} ·
                  {{ label(data.selection.planPriceSelection.billingCycle) }}
                </dd>
              </div>
              <div>
                <dt>Add-ons</dt>
                <dd>{{ data.selection.addOnCodes.join(", ") || "None" }}</dd>
              </div>
              <div>
                <dt>Capacity packages</dt>
                <dd>
                  {{
                    data.selection.quotaPackages
                      .map((q) => `${q.packageCode} × ${q.quantity}`)
                      .join(", ") || "None"
                  }}
                </dd>
              </div>
            </dl>
          </div>
        </section>
        <section class="panel">
          <div class="panel-header"><h2>Execution trail</h2></div>
          <div class="panel-body">
            <dl class="detail-list">
              <div>
                <dt>Created</dt>
                <dd>{{ date(data.summary.createdAt, true) }}</dd>
              </div>
              <div>
                <dt>Scheduled start</dt>
                <dd>{{ date(data.summary.executeAt, true) }}</dd>
              </div>
              <div>
                <dt>Completed</dt>
                <dd>{{ date(data.summary.completedAt, true) }}</dd>
              </div>
              <div>
                <dt>Retries</dt>
                <dd>{{ data.summary.retryCount }}</dd>
              </div>
              <div>
                <dt>Operation ID</dt>
                <dd class="operation-id">{{ data.summary.id }}</dd>
              </div>
            </dl>
          </div>
        </section>
      </div></template
    ></ResourceState
  >
  <section class="panel section-gap">
    <div class="panel-header">
      <div>
        <h2>Individual outcomes</h2>
      </div>
      <button
        class="icon-button"
        aria-label="Refresh outcomes"
        @click="results.refresh()"
      >
        <Icon name="refresh" />
      </button>
    </div>
    <ResourceState
      :loading="results.loading.value"
      :error="results.error.value"
      :empty="!rows?.content.length"
      title="No results available yet"
      description="The processor will add per-customer outcomes as the execution proceeds."
      @retry="results.refresh()"
      ><div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Result</th>
              <th>Status</th>
              <th>Change</th>
              <th>Target price</th>
              <th>Evidence</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows?.content" :key="row.id">
              <td>
                <RouterLink
                  v-if="identityMap.get(row.id)"
                  class="text-link"
                  :to="`/customers/${identityMap.get(row.id)!.accountId}`"
                  >{{ identityMap.get(row.id)!.accountName }}</RouterLink
                ><span v-else>{{ row.id.slice(0, 8) }}</span>
              </td>
              <td><StatusBadge :status="row.status" /></td>
              <td>
                {{ row.assessment.currentPlanCode }} →
                {{ row.assessment.targetPlanCode }}
              </td>
              <td>
                {{
                  row.assessment.targetPrice
                    ? money(
                        row.assessment.targetPrice,
                        row.assessment.currencyCode || "MAD",
                      )
                    : "—"
                }}
              </td>
              <td>
                <span v-if="!row.assessment.conflicts.length">{{
                  row.outcomeCode || "No conflicts"
                }}</span>
                <p
                  v-for="c in row.assessment.conflicts"
                  :key="c.code"
                  class="conflict-evidence"
                >
                  {{ c.message }}
                </p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        v-if="rows"
        :page="resultPage"
        :size="20"
        :total="rows.totalElements"
        @change="resultPage = $event"
      />
      <p v-if="identities.error.value" class="result-note">
        Customer identities could not be resolved. Outcomes are still available.
      </p></ResourceState
    >
  </section>
  <AppDialog
    :open="!!action"
    :title="
      action === 'cancel'
        ? 'Cancel this execution?'
        : 'Retry recoverable items?'
    "
    @close="!busy && (action = null)"
    ><form @submit.prevent="execute">
      <p class="muted small">
        {{
          action === "cancel"
            ? "Already applied changes remain applied. Pending items are cancelled according to the backend’s current execution state."
            : "The backend decides which outcomes can safely be retried. It preserves the original execution history."
        }}
      </p>
      <label class="field section-gap"
        >Reason<textarea
          v-model="reason"
          required
          minlength="5"
          maxlength="2000"
          placeholder="Explain this operator action…"
        />
      </label>
      <p v-if="error" class="notice error section-gap" role="alert">
        {{ error }}
      </p>
      <div class="form-actions">
        <button
          type="button"
          class="button"
          :disabled="busy"
          @click="action = null"
        >
          Keep current state</button
        ><button class="button primary" :disabled="busy">
          {{
            busy
              ? "Submitting…"
              : action === "cancel"
                ? "Cancel execution"
                : "Request retry"
          }}
        </button>
      </div>
    </form></AppDialog
  >
</template>
<style scoped>
.execution-progress {
  padding: 24px;
  margin-bottom: 23px;
  background: var(--surface);
}
.execution-progress p {
  color: var(--muted);
  font-size: 11px;
  margin-top: 5px;
}
.execution-progress .progress-track {
  margin-top: 20px;
}
.progress-number {
  font-size: 30px;
  color: var(--accent);
}
.operation-id {
  font-size: 9px !important;
  overflow-wrap: anywhere;
}
.conflict-evidence {
  color: var(--danger);
  white-space: normal;
  max-width: 350px;
  font-size: 10px;
}
.result-note {
  padding: 15px 20px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 10px;
}
</style>
