<script setup lang="ts">
import { computed, ref, watch } from "vue";
import AppDialog from "./AppDialog.vue";
import Icon from "./Icon.vue";
import StatusBadge from "./StatusBadge.vue";
import ResourceState from "./ResourceState.vue";
import { gateway } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import { can, notify, session } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { date, label, errorMessage } from "@/lib/format";
import type {
  SubscriptionLifecycleAction,
  SubscriptionLifecyclePreview,
} from "@/api/contracts";
const props = defineProps<{ accountId: string }>();
const open = ref(false);
const selected = ref<SubscriptionLifecycleAction>();
const grace = ref("");
const reason = ref("");
const preview = ref<SubscriptionLifecyclePreview>();
const busy = ref(false);
const error = ref("");
const permissions: Record<SubscriptionLifecycleAction, string> = {
  CANCEL_AT_PERIOD_END: p.subscriptionsCancelAtPeriodEnd,
  KEEP_RENEWING: p.subscriptionsKeepRenewing,
  CANCEL_IMMEDIATELY: p.subscriptionsCancelImmediately,
  SUSPEND: p.subscriptionsSuspend,
  RESTORE: p.subscriptionsRestore,
  EXTEND_GRACE: p.subscriptionsExtendGrace,
};
const actions = useResource(
  () =>
    open.value
      ? gateway.lifecycleActions(props.accountId)
      : Promise.resolve(undefined),
  [() => props.accountId, open],
);
const choices = computed(
  () =>
    actions.data.value?.availableActions.filter((a) => can(permissions[a])) ||
    [],
);
watch([selected, grace], () => (preview.value = undefined));
function start() {
  selected.value = undefined;
  preview.value = undefined;
  error.value = "";
  reason.value = "";
  open.value = true;
}
async function review() {
  if (!selected.value) return;
  busy.value = true;
  error.value = "";
  try {
    preview.value = await gateway.previewLifecycle(
      props.accountId,
      selected.value,
      selected.value === "EXTEND_GRACE"
        ? new Date(grace.value).toISOString()
        : undefined,
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function apply() {
  if (!preview.value || !selected.value) return;
  error.value = "";
  if (Date.parse(preview.value.expiresAt) <= Date.now()) {
    error.value = "This preview has expired. Review the action again.";
    preview.value = undefined;
    return;
  }
  busy.value = true;
  try {
    await gateway.applyLifecycle(props.accountId, selected.value, {
      previewToken: preview.value.previewToken,
      reason: reason.value.trim(),
      graceEndsAt: preview.value.nextGraceEndsAt,
    });
    open.value = false;
    notify("Subscription lifecycle updated.");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <button
    v-if="
      can(p.subscriptionsReadLifecycleActions) &&
      can(p.subscriptionsPreviewLifecycle)
    "
    class="button"
    @click="start"
  >
    <Icon name="settings" :size="15" />Manage lifecycle</button
  ><AppDialog
    :open="open"
    title="Manage subscription lifecycle"
    @close="!busy && (open = false)"
    ><ResourceState
      :loading="actions.loading.value"
      :error="actions.error.value"
      :empty="!choices.length"
      title="No lifecycle actions available"
      description="Actions depend on the current subscription state and your permissions."
      @retry="actions.refresh()"
      ><form @submit.prevent="preview ? apply() : review()">
        <p class="muted small">
          {{ "Select an action." }}
        </p>
        <fieldset v-if="!preview" class="lifecycle-options">
          <legend class="sr-only">Lifecycle action</legend>
          <label v-for="a in choices" :key="a"
            ><input
              type="radio"
              v-model="selected"
              :value="a"
              required
            /><span>{{ label(a) }}</span></label
          >
        </fieldset>
        <label
          v-if="selected === 'EXTEND_GRACE' && !preview"
          class="field section-gap"
          >New grace deadline<input
            v-model="grace"
            type="datetime-local"
            required /></label
        ><template v-if="preview"
          ><div class="review-statuses">
            <StatusBadge :status="preview.beforeStatus" /><Icon
              name="right"
              :size="16"
            /><StatusBadge :status="preview.afterStatus" />
          </div>
          <dl class="detail-list">
            <div>
              <dt>Action</dt>
              <dd>{{ label(preview.action) }}</dd>
            </div>
            <div>
              <dt>Effective</dt>
              <dd>{{ date(preview.effectiveAt, true) }}</dd>
            </div>
            <div v-if="preview.nextGraceEndsAt">
              <dt>Grace deadline</dt>
              <dd>{{ date(preview.nextGraceEndsAt, true) }}</dd>
            </div>
            <div>
              <dt>Preview expires</dt>
              <dd>{{ date(preview.expiresAt, true) }}</dd>
            </div>
          </dl>
          <p
            v-for="blocker in preview.blockers"
            :key="blocker"
            class="notice error section-gap"
          >
            {{ label(blocker) }}
          </p>
          <label class="field section-gap"
            >Audit reason<textarea
              v-model="reason"
              required
              minlength="5"
              maxlength="2000"
              placeholder="Explain why this action is needed…"
            /></label
        ></template>
        <p v-if="error" class="notice error section-gap" role="alert">
          {{ error }}
        </p>
        <div class="form-actions">
          <button
            class="button"
            type="button"
            :disabled="busy"
            @click="preview ? (preview = undefined) : (open = false)"
          >
            {{ preview ? "Back" : "Cancel" }}</button
          ><button
            class="button primary"
            :disabled="busy || !selected || !!preview?.blockers.length"
          >
            {{
              busy
                ? "Working…"
                : preview
                  ? "Confirm lifecycle action"
                  : "Preview action"
            }}
          </button>
        </div>
      </form></ResourceState
    ></AppDialog
  >
</template>
<style scoped>
.lifecycle-options {
  border: 0;
  padding: 0;
  margin: 18px 0 0;
  display: grid;
  gap: 10px;
}
.lifecycle-options label {
  display: flex;
  align-items: center;
  gap: 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px;
  font-size: 12px;
  cursor: pointer;
}
.lifecycle-options label:has(:checked) {
  background: var(--accent-soft);
  border-color: var(--accent);
}
.review-statuses {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0;
}
</style>
