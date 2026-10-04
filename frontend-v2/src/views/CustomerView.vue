<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import Icon from "@/components/Icon.vue";
import ResourceState from "@/components/ResourceState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import AppDialog from "@/components/AppDialog.vue";
import SubscriptionActions from "@/components/SubscriptionActions.vue";
import { gateway, read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import ActionDialog from "@/components/ActionDialog.vue";
import Facts from "@/components/Facts.vue";
import Pagination from "@/components/Pagination.vue";
import type { Action, RecordData } from "@/resources/types";
import { withReturnTo, safeReturnTo } from "@/lib/navigation";
import { useResource } from "@/composables/useResource";
import { date, money, label, initials, errorMessage } from "@/lib/format";
import { can, notify } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const route = useRoute(),
  router = useRouter();
const returnPath = computed(
  () => safeReturnTo(route.query.returnTo) || "/customers",
);
const canChange = computed(
  () =>
    can(p.subscriptionsChooseChangeOptions) &&
    can(p.subscriptionsPreviewChange, p.subscriptionsPreviewChangeJob),
);
const canExecuteChange = computed(() =>
  can(p.subscriptionsPreviewChange)
    ? can(p.subscriptionsApplyChange)
    : can(p.subscriptionsConfirmChangeJob),
);
const changePath = computed(() =>
  withReturnTo(`/changes/new?accounts=${id.value}`, route.fullPath),
);
const id = computed(() => String(route.params.id));
const invoicePage = ref(0),
  changePage = ref(0),
  agreementPage = ref(0),
  timelinePage = ref(0),
  action = ref<Action>(),
  actionData = ref<RecordData>({});
const tabs = computed(() =>
  [
    {
      key: "overview",
      label: "Subscription",
      allowed: can(p.subscriptionsRead),
    },
    {
      key: "billing",
      label: "Billing",
      allowed: can(
        p.billingListInvoices,
        p.billingReadAccountProfile,
        p.billingReadAccountTimeline,
      ),
    },
    {
      key: "changes",
      label: "Changes",
      allowed: can(p.subscriptionsReadChanges),
    },
    {
      key: "agreements",
      label: "Agreements",
      allowed: can(p.subscriptionsReadSpecialAgreements),
    },
    { key: "activity", label: "Activity", allowed: can(p.activitiesRead) },
  ].filter((x) => x.allowed),
);
const view = computed(
  () =>
    tabs.value.find((t) => t.key === route.query.view)?.key ||
    tabs.value[0]?.key ||
    "overview",
);
const subscription = useResource(
  () =>
    can(p.subscriptionsRead)
      ? gateway.subscription(id.value)
      : Promise.resolve(undefined),
  [id],
);
const s = subscription.data;
const identity = useResource(
  () =>
    can(p.subscriptionsResolveAccountChoices)
      ? gateway.resolveAccounts([id.value])
      : Promise.resolve([]),
  [id],
);
const accountName = computed(
  () =>
    s.value?.accountName ||
    identity.data.value?.[0]?.name ||
    "Customer workspace",
);
const profile = useResource(
  () =>
    view.value === "billing" && can(p.billingReadAccountProfile)
      ? gateway.profile(id.value)
      : Promise.resolve(undefined),
  [id, view],
);
const invoices = useResource(
  () =>
    view.value === "billing" && can(p.billingListInvoices)
      ? gateway.invoices({
          accountId: id.value,
          page: invoicePage.value,
          size: 20,
        })
      : Promise.resolve(undefined),
  [id, view, invoicePage],
);
const changes = useResource(
  () =>
    view.value === "changes"
      ? gateway.changes(id.value, changePage.value)
      : Promise.resolve(undefined),
  [id, view, changePage],
);
const agreements = useResource(
  () =>
    view.value === "agreements"
      ? gateway.agreements(id.value, agreementPage.value)
      : Promise.resolve(undefined),
  [id, view, agreementPage],
);
const activityPage = ref(0),
  lifecyclePage = ref(0);
const activity = useResource(
  () =>
    view.value === "activity"
      ? read(p.activitiesRead, () =>
          adminApi.activities({
            targetAccountId: id.value,
            page: activityPage.value,
            size: 20,
          }),
        )
      : Promise.resolve(undefined),
  [id, view, activityPage],
);
const timeline = useResource(
  () =>
    view.value === "billing" && can(p.billingReadAccountTimeline)
      ? gateway.timeline(id.value, timelinePage.value)
      : Promise.resolve(undefined),
  [id, view, timelinePage],
);
const lifecycle = useResource(
  () =>
    view.value === "changes" && can(p.subscriptionsReadLifecycleHistory)
      ? read(p.subscriptionsReadLifecycleHistory, () =>
          adminApi.subscriptionLifecycleHistory(id.value, {
            page: lifecyclePage.value,
            size: 20,
          }),
        )
      : Promise.resolve(undefined),
  [id, view, lifecyclePage],
);
function cancelChange(row: RecordData) {
  actionData.value = row;
  action.value = {
    key: "cancel",
    label: "Cancel change",
    permission: p.subscriptionsCancelChange,
    destructive: true,
    execute: (_, input) =>
      adminApi.cancelSubscriptionChange(id.value, row.id, input.reason),
  };
}
const profileData = profile.data;
const invoiceData = invoices.data;
const changeData = changes.data;
const agreementData = agreements.data;
const selectedChange = ref<RecordData>();
function openChange(change: RecordData) {
  selectedChange.value = change;
  void router.replace({ query: { ...route.query, change: change.id } });
}
function closeChange() {
  selectedChange.value = undefined;
  const query = { ...route.query };
  delete query.change;
  void router.replace({ query });
}
let lookupGeneration = 0;
watch(
  [id, () => route.query.change, view],
  async () => {
    const generation = ++lookupGeneration;
    const target = String(route.query.change || "");
    if (
      !target ||
      view.value !== "changes" ||
      !can(p.subscriptionsReadChanges)
    ) {
      selectedChange.value = undefined;
      return;
    }
    if (selectedChange.value?.id === target) return;
    try {
      let page = 0;
      do {
        const response = await gateway.changes(id.value, page);
        if (generation !== lookupGeneration) return;
        const match = response.content.find((change) => change.id === target);
        if (match) {
          selectedChange.value = match;
          changePage.value = page;
          return;
        }
        if ((page + 1) * response.size >= response.totalElements) break;
        page++;
      } while (true);
    } catch (e) {
      notify(errorMessage(e));
    }
  },
  { immediate: true },
);
const activityData = activity.data;
const edit = ref(false);
const busy = ref(false);
const formError = ref("");
const form = reactive({
  legalName: "",
  billingEmail: "",
  taxId: "",
  address: "",
  countryCode: "",
});
function editProfile() {
  if (!profileData.value) return;
  Object.assign(form, {
    legalName: profileData.value.legalName,
    billingEmail: profileData.value.billingEmail || "",
    taxId: profileData.value.taxId || "",
    address: profileData.value.address || "",
    countryCode: profileData.value.countryCode || "",
  });
  formError.value = "";
  edit.value = true;
}
async function saveProfile() {
  busy.value = true;
  formError.value = "";
  try {
    await gateway.saveProfile(id.value, form);
    edit.value = false;
    notify("Billing profile updated.");
    await profile.refresh();
  } catch (e) {
    formError.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <RouterLink class="back-link" :to="returnPath"
    ><Icon name="back" :size="14" />Back</RouterLink
  >
  <div class="customer-heading">
    <span class="avatar customer-mark">{{ initials(accountName) }}</span
    ><PageHeading :title="accountName"
      ><StatusBadge v-if="s" :status="s.status" /><RouterLink
        v-if="can(p.customerCommunicationsCreate)"
        class="button"
        :to="
          withReturnTo(
            '/customers/communications/new?account=' + id,
            route.fullPath,
          )
        "
        >Message</RouterLink
      ><RouterLink
        v-if="can(p.offersList)"
        class="button"
        :to="
          withReturnTo('/commercial?view=offers&account=' + id, route.fullPath)
        "
        >Offers</RouterLink
      ><SubscriptionActions :account-id="id" /><RouterLink
        v-if="canChange"
        class="button primary"
        :to="changePath"
        ><Icon name="plus" :size="15" />{{
          canExecuteChange ? "Change subscription" : "Review change"
        }}</RouterLink
      ></PageHeading
    >
  </div>
  <ViewTabs :tabs="tabs" :current="view" />
  <ResourceState
    v-if="view === 'overview'"
    :loading="subscription.loading.value"
    :error="subscription.error.value"
    :empty="!s"
    title="Current subscription unavailable"
    @retry="subscription.refresh()"
  >
    <div v-if="s?.status === 'PAST_DUE'" class="notice warning overdue-note">
      <Icon name="alert" />
      <div>
        <strong>This account has an outstanding balance.</strong>
        <p>
          Grace ends {{ date(s.graceEndsAt, true) }}. Payment handling is not
          available yet.
        </p>
        <RouterLink
          v-if="tabs.some((tab) => tab.key === 'billing')"
          class="text-link"
          :to="{ query: { ...route.query, view: 'billing' } }"
          >Open billing<Icon name="right" :size="13"
        /></RouterLink>
      </div>
    </div>
    <div class="customer-grid">
      <div class="stack">
        <section class="panel">
          <div class="panel-header">
            <div>
              <h2>Current subscription</h2>
            </div>
            <Icon name="catalog" />
          </div>
          <div v-if="s" class="panel-body">
            <div class="held-plan">
              <span class="product-symbol"
                ><Icon name="catalog" :size="24"
              /></span>
              <div>
                <h2>{{ s.planName }}</h2>
                <p>
                  {{ s.planCode
                  }}<span v-if="s.entitlementSnapshot">
                    · version
                    {{ s.entitlementSnapshot.planDefinitionVersion }}</span
                  >
                </p>
              </div>
              <div class="held-price">
                <strong>{{
                  money(s.currentPrice, s.currentPriceCurrencyCode)
                }}</strong
                ><small>{{
                  s.entitlementSnapshot
                    ? label(s.entitlementSnapshot.billingCycle)
                    : "Captured recurring price"
                }}</small>
              </div>
            </div>
            <dl class="detail-list">
              <div>
                <dt>Current period</dt>
                <dd>
                  {{ date(s.currentPeriodStart) }} –
                  {{ date(s.currentPeriodEnd) }}
                </dd>
              </div>
              <div>
                <dt>Renewal</dt>
                <dd>
                  {{
                    s.cancelAtPeriodEnd
                      ? "Cancels at period end"
                      : "Renews automatically"
                  }}
                </dd>
              </div>
              <div>
                <dt>Subscription ID</dt>
                <dd class="identifier">{{ s.id }}</dd>
              </div>
            </dl>
            <div class="divider" />
            <div class="row spread">
              <RouterLink v-if="canChange" class="text-link" :to="changePath"
                >Prepare a change<Icon name="right" :size="14"
              /></RouterLink>
            </div>
          </div>
        </section>
        <section class="panel">
          <div class="panel-header">
            <div>
              <h2>Extensions & capacity</h2>
            </div>
          </div>
          <div class="panel-body">
            <div
              v-if="
                !s?.customOverrides.addOnCodes.length &&
                !s?.customOverrides.quotaPackages.length
              "
              class="compact-empty"
            >
              <Icon name="box" />
              <div>
                <strong>Included plan capabilities</strong>
                <p>No additional extensions or capacity packages are held.</p>
              </div>
            </div>
            <div
              v-for="addon in s?.customOverrides.addOnCodes"
              :key="addon"
              class="entitlement-row"
            >
              <Icon name="plus" :size="15" /><strong>{{ label(addon) }}</strong
              ><span class="pill">Add-on</span>
            </div>
            <div
              v-for="q in s?.customOverrides.quotaPackages"
              :key="q.packageCode"
              class="entitlement-row"
            >
              <Icon name="box" :size="15" /><strong>{{
                label(q.packageCode)
              }}</strong
              ><span class="pill">{{ q.quantity }} units</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  </ResourceState>
  <div v-else-if="view === 'billing'" class="customer-grid">
    <section v-if="can(p.billingListInvoices)" class="panel">
      <div class="panel-header">
        <h2>Invoices</h2>
        <span class="muted small"
          >{{ invoiceData?.totalElements || 0 }} invoices</span
        >
      </div>
      <ResourceState
        :loading="invoices.loading.value"
        :error="invoices.error.value"
        :empty="!invoiceData?.content.length"
        title="No invoices for this customer"
        @retry="invoices.refresh()"
        ><div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Invoice</th>
                <th>Status</th>
                <th class="numeric">Amount</th>
                <th>Issued</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="i in invoiceData?.content" :key="i.id">
                <td>
                  <RouterLink
                    class="text-link"
                    :to="withReturnTo(`/billing/${i.id}`, route.fullPath)"
                    >{{ i.invoiceNumber }}</RouterLink
                  >
                </td>
                <td><StatusBadge :status="i.status" /></td>
                <td class="numeric">
                  {{ money(i.totalAmount, i.currencyCode) }}
                </td>
                <td>{{ date(i.issuedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination
          v-if="invoiceData"
          :page="invoiceData.page"
          :size="invoiceData.size"
          :total="invoiceData.totalElements"
          @change="invoicePage = $event"
      /></ResourceState>
    </section>
    <section v-if="can(p.billingReadAccountProfile)" class="panel">
      <div class="panel-header">
        <h2>Billing profile</h2>
        <button
          v-if="can(p.billingUpdateAccountProfile) && profileData"
          class="button small"
          @click="editProfile"
        >
          Edit
        </button>
      </div>
      <div class="panel-body">
        <ResourceState
          :loading="profile.loading.value"
          :error="profile.error.value"
          :empty="!profileData"
          title="No billing profile available"
          @retry="profile.refresh()"
          ><dl v-if="profileData" class="detail-list">
            <div>
              <dt>Legal name</dt>
              <dd>{{ profileData.legalName }}</dd>
            </div>
            <div>
              <dt>Billing email</dt>
              <dd>{{ profileData.billingEmail || "—" }}</dd>
            </div>
            <div>
              <dt>Tax ID</dt>
              <dd>{{ profileData.taxId || "—" }}</dd>
            </div>
            <div>
              <dt>Address</dt>
              <dd>{{ profileData.address || "—" }}</dd>
            </div>
            <div>
              <dt>Country</dt>
              <dd>{{ profileData.countryCode || "—" }}</dd>
            </div>
          </dl></ResourceState
        >
      </div>
    </section>
  </div>
  <section v-else-if="view === 'changes'" class="panel">
    <div class="panel-header">
      <div>
        <h2>Subscription changes</h2>
      </div>
    </div>
    <ResourceState
      :loading="changes.loading.value"
      :error="changes.error.value"
      :empty="!changeData?.content.length"
      title="No subscription changes"
      description="New changes will appear here with their timing, outcome and audited reason."
      @retry="changes.refresh()"
      ><div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Change</th>
              <th>Status</th>
              <th>Timing</th>
              <th>Effective</th>
              <th>Reason</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="change in changeData?.content"
              :key="change.id"
              :class="{ selected: selectedChange?.id === change.id }"
            >
              <td>{{ change.sourcePlanCode }} → {{ change.targetPlanCode }}</td>
              <td><StatusBadge :status="change.status" /></td>
              <td>{{ label(change.timing) }}</td>
              <td>{{ date(change.effectiveAt) }}</td>
              <td class="change-reason">{{ change.requestReason || "—" }}</td>
              <td>
                <button
                  v-if="
                    can(p.subscriptionsCancelChange) &&
                    ['PENDING', 'AWAITING_CONFIRMATION'].includes(change.status)
                  "
                  class="text-link"
                  @click="cancelChange(change)"
                >
                  Cancel
                </button>
                <button class="text-link" @click="openChange(change)">
                  Details
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        v-if="changeData"
        :page="changeData.page"
        :size="changeData.size"
        :total="changeData.totalElements"
        @change="changePage = $event"
    /></ResourceState>
  </section>
  <section v-else-if="view === 'agreements'" class="panel">
    <div class="panel-header">
      <h2>Special agreements</h2>
      <RouterLink
        v-if="
          can(p.subscriptionsPreviewSpecialAgreement) &&
          can(p.subscriptionsCreateSpecialAgreement)
        "
        class="button primary"
        :to="
          withReturnTo('/customers/' + id + '/agreements/new', route.fullPath)
        "
        >New agreement</RouterLink
      >
    </div>
    <ResourceState
      :loading="agreements.loading.value"
      :error="agreements.error.value"
      :empty="!agreementData?.content.length"
      title="No special agreements"
      description="Any customer-specific agreements will appear here, separate from catalog pricing."
      @retry="agreements.refresh()"
      ><div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Agreement</th>
              <th>Status</th>
              <th>Starts</th>
              <th>Ends</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in agreementData?.content" :key="a.id">
              <td>
                <RouterLink
                  class="text-link"
                  :to="
                    withReturnTo(
                      '/customers/' + id + '/agreements/' + a.id,
                      route.fullPath,
                    )
                  "
                  >{{ a.planName }}</RouterLink
                >
              </td>
              <td><StatusBadge :status="a.status" /></td>
              <td>{{ date(a.startsAt) }}</td>
              <td>{{ date(a.endsAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        v-if="agreementData"
        :page="agreementData.page"
        :size="agreementData.size"
        :total="agreementData.totalElements"
        @change="agreementPage = $event"
    /></ResourceState>
  </section>
  <section v-else class="panel">
    <div class="panel-header"><h2>Account activity</h2></div>
    <div class="panel-body">
      <ResourceState
        :loading="activity.loading.value"
        :error="activity.error.value"
        :empty="!activityData?.content.length"
        title="No recorded activity yet"
        @retry="activity.refresh()"
        ><div
          v-for="a in activityData?.content"
          :key="a.id"
          class="activity-row"
        >
          <span class="activity-symbol"
            ><Icon name="activity" :size="14"
          /></span>
          <div>
            <strong>{{ label(a.action.replaceAll(".", " ")) }}</strong>
            <p>{{ a.actorIdentity?.displayName || label(a.actorSurface) }}</p>
            <details>
              <summary class="text-link">Details</summary>
              <Facts :data="a" />
            </details>
          </div>
          <time>{{ date(a.occurredAt, true) }}</time
          ><StatusBadge :status="a.outcome" /></div></ResourceState
      ><Pagination
        v-if="activityData"
        :page="activityData.page"
        :size="activityData.size"
        :total="activityData.totalElements"
        @change="activityPage = $event"
      />
    </div>
  </section>
  <section
    v-if="view === 'billing' && can(p.billingReadAccountTimeline)"
    class="report-section"
  >
    <h2>Financial timeline</h2>
    <ResourceState
      :loading="timeline.loading.value"
      :error="timeline.error.value"
      @retry="timeline.refresh()"
      ><div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Event</th>
              <th>Invoice</th>
              <th>Status</th>
              <th>Amount</th>
              <th>Recorded</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="event in timeline.data.value?.content"
              :key="event.recordId"
            >
              <td>{{ label(event.type) }}</td>
              <td>
                <RouterLink
                  v-if="can(p.billingReadInvoice)"
                  class="text-link"
                  :to="
                    withReturnTo('/billing/' + event.invoiceId, route.fullPath)
                  "
                  >{{ event.invoiceNumber }}</RouterLink
                ><span v-else>{{ event.invoiceNumber }}</span>
              </td>
              <td><StatusBadge :status="event.status" /></td>
              <td>{{ money(event.amount, event.currencyCode) }}</td>
              <td>{{ date(event.occurredAt, true) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        v-if="timeline.data.value"
        :page="timeline.data.value.page"
        :size="timeline.data.value.size"
        :total="timeline.data.value.totalElements"
        @change="timelinePage = $event"
    /></ResourceState>
  </section>
  <section
    v-if="view === 'changes' && can(p.subscriptionsReadLifecycleHistory)"
    class="report-section"
  >
    <h2>Lifecycle history</h2>
    <ResourceState
      :loading="lifecycle.loading.value"
      :error="lifecycle.error.value"
      @retry="lifecycle.refresh()"
      ><div
        v-for="event in lifecycle.data.value?.content"
        :key="event.id"
        class="lifecycle-row"
      >
        <div class="row spread">
          <strong>{{ label(event.action) }}</strong
          ><time class="small muted">{{ date(event.effectiveAt, true) }}</time>
        </div>
        <p class="small section-gap">
          {{ label(event.beforeStatus) }} → {{ label(event.afterStatus) }} ·
          {{ event.actorEmail }}
        </p>
        <p class="small muted">{{ event.reason }}</p>
        <p v-if="event.nextGraceEndsAt" class="small muted">
          Grace ends {{ date(event.nextGraceEndsAt, true) }}
        </p>
      </div>
      <Pagination
        v-if="lifecycle.data.value"
        :page="lifecycle.data.value.page"
        :size="lifecycle.data.value.size"
        :total="lifecycle.data.value.totalElements"
        @change="lifecyclePage = $event"
    /></ResourceState>
  </section>
  <AppDialog
    :open="!!selectedChange"
    title="Subscription change"
    @close="closeChange"
  >
    <template v-if="selectedChange">
      <div class="row spread">
        <strong
          >{{ selectedChange.sourcePlanCode }} →
          {{ selectedChange.targetPlanCode }}</strong
        ><StatusBadge :status="selectedChange.status" />
      </div>
      <dl class="detail-list section-gap">
        <div>
          <dt>Effective</dt>
          <dd>
            {{
              selectedChange.effectiveAt
                ? date(selectedChange.effectiveAt, true)
                : label(selectedChange.timing)
            }}
          </dd>
        </div>
        <div>
          <dt>Requested</dt>
          <dd>
            {{ date(selectedChange.createdAt, true) }} ·
            {{ label(selectedChange.requestOrigin) }}
          </dd>
        </div>
        <div>
          <dt>Requester</dt>
          <dd class="identifier">
            {{ selectedChange.requestedByUserId || "System" }}
          </dd>
        </div>
        <div>
          <dt>Reason</dt>
          <dd>{{ selectedChange.requestReason || "—" }}</dd>
        </div>
        <div v-if="selectedChange.attentionReason">
          <dt>Needs attention</dt>
          <dd>{{ selectedChange.attentionReason }}</dd>
        </div>
        <div v-if="selectedChange.cancelledAt">
          <dt>Cancelled</dt>
          <dd>
            {{ date(selectedChange.cancelledAt, true) }} ·
            {{ label(selectedChange.cancellationOrigin) }}
          </dd>
        </div>
        <div v-if="selectedChange.cancelledByUserId">
          <dt>Cancelled by</dt>
          <dd class="identifier">{{ selectedChange.cancelledByUserId }}</dd>
        </div>
        <div v-if="selectedChange.cancellationReason">
          <dt>Cancellation reason</dt>
          <dd>{{ selectedChange.cancellationReason }}</dd>
        </div>
        <div>
          <dt>Operation ID</dt>
          <dd class="identifier">{{ selectedChange.id }}</dd>
        </div>
      </dl>
      <p
        v-if="selectedChange.status === 'AWAITING_CONFIRMATION'"
        class="notice warning section-gap"
      >
        This change is awaiting confirmation. Payment actions are not available
        yet.
      </p>
      <details
        v-if="selectedChange.commercialPolicyEvaluation"
        class="section-gap"
      >
        <summary>Accepted policy terms</summary>
        <Facts :data="selectedChange.commercialPolicyEvaluation" />
      </details>
      <details v-if="selectedChange.checkout" class="section-gap">
        <summary>Recorded checkout evidence</summary>
        <Facts :data="selectedChange.checkout" />
      </details>
      <div class="form-actions">
        <button class="button" @click="closeChange">Close</button
        ><button
          v-if="
            can(p.subscriptionsCancelChange) &&
            ['PENDING', 'AWAITING_CONFIRMATION'].includes(selectedChange.status)
          "
          class="button danger"
          @click="cancelChange(selectedChange)"
        >
          Cancel change
        </button>
      </div>
    </template>
  </AppDialog>
  <ActionDialog
    :action="action"
    :data="actionData"
    @close="action = undefined"
    @done="
      changes.refresh();
      subscription.refresh();
      closeChange();
    "
  /><AppDialog
    :open="edit"
    title="Edit billing profile"
    @close="!busy && (edit = false)"
    ><form @submit.prevent="saveProfile">
      <div class="form-grid two">
        <label class="field full"
          >Legal name<input
            v-model="form.legalName"
            required
            maxlength="255" /></label
        ><label class="field full"
          >Billing email<input
            v-model="form.billingEmail"
            type="email" /></label
        ><label class="field">Tax ID<input v-model="form.taxId" /></label
        ><label class="field"
          >Country code<input
            v-model="form.countryCode"
            maxlength="2"
            placeholder="MA" /></label
        ><label class="field full"
          >Billing address<textarea v-model="form.address" />
        </label>
      </div>
      <p v-if="formError" class="notice error section-gap" role="alert">
        {{ formError }}
      </p>
      <div class="form-actions">
        <button
          type="button"
          class="button"
          :disabled="busy"
          @click="edit = false"
        >
          Cancel</button
        ><button class="button primary" :disabled="busy">
          {{ busy ? "Saving…" : "Save profile" }}
        </button>
      </div>
    </form></AppDialog
  >
</template>
<style scoped>
.lifecycle-row {
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}
.data-table tr.selected {
  background: var(--accent-soft);
}
.customer-heading {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}
.customer-heading .page-heading {
  flex: 1;
}
.customer-mark {
  width: 55px;
  height: 55px;
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: 15px;
  font-size: 17px;
}
.customer-grid {
  display: block;
}
.held-plan {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 5px 0 24px;
}
.product-symbol {
  display: grid;
  place-items: center;
  width: 45px;
  height: 45px;
  background: var(--brand);
  border-radius: 12px;
}
.held-plan p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 3px;
}
.held-price {
  margin-left: auto;
  text-align: right;
}
.held-price strong {
  font-size: 20px;
  letter-spacing: -0.5px;
}
.held-price small {
  display: block;
  font-size: 12px;
  color: var(--muted);
}
.identifier {
  font-size: 12px !important;
  max-width: 170px;
}
.compact-empty {
  display: flex;
  gap: 13px;
  align-items: center;
  background: var(--surface);
  padding: 20px 15px;
  border-radius: 8px;
  color: var(--muted);
}
.compact-empty p {
  font-size: 11px;
  margin-top: 4px;
}
.compact-empty strong {
  font-size: 12px;
  color: var(--text);
}
.entitlement-row {
  display: flex;
  gap: 10px;
  padding: 12px 0;
}
.entitlement-row .pill {
  margin-left: auto;
}
.context-card {
  background: var(--stone-50);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 23px;
}
.context-card h2 {
  margin: 10px 0;
  font-size: 19px;
}
.context-card p {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 20px;
}
.context-card a {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11px;
  margin-top: 15px;
}
.context-card a svg:last-child {
  margin-left: auto;
}
.overdue-note {
  margin-bottom: 23px;
}
.overdue-note p {
  margin: 4px 0 9px;
}
.data-table .change-reason {
  max-width: 18rem;
  white-space: normal;
  overflow-wrap: anywhere;
}
@media (max-width: 1000px) {
  .customer-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 700px) {
  .customer-mark {
    display: none;
  }
  .held-plan {
    flex-wrap: wrap;
  }
  .held-price {
    margin-left: 0;
    width: 100%;
    text-align: left;
  }
}
</style>
