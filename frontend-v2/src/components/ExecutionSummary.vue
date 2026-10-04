<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import type { RecordData } from "@/resources/types";
import { money, date, label } from "@/lib/format";
import { withReturnTo } from "@/lib/navigation";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const props = defineProps<{ data: RecordData; kind: string }>();
const route = useRoute();
const summary = computed(() => props.data.summary || props.data);
const request = computed(
  () => props.data.definition?.request || props.data.request || {},
);
const counts = computed(() => summary.value.counts || {});
const affected = computed(
  () =>
    summary.value.targetCount ??
    Object.values(counts.value).reduce<number>(
      (sum, count) => sum + Number(count),
      0,
    ),
);
const ready = computed(
  () => summary.value.readyCount ?? counts.value.READY ?? 0,
);
const applied = computed(
  () =>
    summary.value.appliedCount ??
    (counts.value.APPLIED || 0) + (counts.value.UNCHANGED || 0),
);
const issues = computed(
  () =>
    summary.value.conflictCount ??
    (counts.value.CONFLICT || 0) + (counts.value.FAILED || 0),
);
</script>
<template>
  <dl class="record-summary">
    <div>
      <dt>Customers</dt>
      <dd>{{ affected }}</dd>
    </div>
    <div>
      <dt>Ready</dt>
      <dd>{{ ready }}</dd>
    </div>
    <div>
      <dt>Applied / unchanged</dt>
      <dd>{{ applied }}</dd>
    </div>
    <div>
      <dt>Conflicts / failures</dt>
      <dd>{{ issues }}</dd>
    </div>
    <template v-if="kind === 'repricing'"
      ><div>
        <dt>Current price</dt>
        <dd>{{ money(summary.sourcePrice, summary.currencyCode) }}</dd>
      </div>
      <div>
        <dt>Target price</dt>
        <dd>{{ money(summary.targetPrice, summary.currencyCode) }}</dd>
      </div>
      <div>
        <dt>Billing cycle</dt>
        <dd>{{ label(summary.billingCycle) }}</dd>
      </div></template
    >
    <template v-else
      ><div>
        <dt>Timing</dt>
        <dd>{{ label(request.application?.timing) }}</dd>
      </div>
      <div>
        <dt>Scheduled start</dt>
        <dd>{{ date(summary.executeAt, true) }}</dd>
      </div></template
    >
  </dl>
  <div
    v-if="kind === 'rollouts' && can(p.plansReadDetail)"
    class="related-actions"
  >
    <RouterLink
      v-if="request.sourcePlanId"
      class="text-link"
      :to="
        withReturnTo('/catalog/plans/' + request.sourcePlanId, route.fullPath)
      "
      >Source revision</RouterLink
    >
    <RouterLink
      v-if="summary.targetPlanId"
      class="text-link"
      :to="
        withReturnTo('/catalog/plans/' + summary.targetPlanId, route.fullPath)
      "
      >Target revision</RouterLink
    >
    <RouterLink
      v-if="
        request.sourcePlanId &&
        summary.targetPlanId &&
        can(p.plansCompareVersions)
      "
      class="text-link"
      :to="
        withReturnTo(
          '/catalog/plans/' +
            request.sourcePlanId +
            '?view=revision-comparison&compare=' +
            summary.targetPlanId,
          route.fullPath,
        )
      "
      >Compare revisions</RouterLink
    >
  </div>
  <p v-if="summary.status === 'ASSESSING'" class="notice">
    Customer assessment is running. Changes require a separate review and
    confirmation.
  </p>
  <p v-if="summary.status === 'PREVIEWED'" class="notice">
    Assessment saved. Changes have not been confirmed.
  </p>
  <p v-if="data.reviewInvalidated" class="notice error">
    This review is no longer current. Refresh the assessment before
    confirmation.
  </p>
  <table v-if="data.conflicts?.length" class="data-table">
    <thead>
      <tr>
        <th>Issue</th>
        <th>Customers</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="conflict in data.conflicts" :key="conflict.primaryReason">
        <td>{{ label(conflict.primaryReason) }}</td>
        <td>{{ conflict.accounts }}</td>
      </tr>
    </tbody>
  </table>
</template>
