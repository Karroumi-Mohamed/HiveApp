<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminApi as api } from "@/api/admin-api";
import type {
  CommercialSegmentActivation,
  CommercialSegmentActivationAudience,
  CommercialSegmentActivationIdentityAudience,
  CommercialCampaignSegmentChoice,
} from "@/api/contracts";
import { adminPermissions as p } from "@/auth/permissions";
import { can, revision } from "@/data/session";
import { read } from "@/data/gateway";
import { date, errorMessage } from "@/lib/format";
import { withReturnTo, contextualPath } from "@/lib/navigation";
import Pagination from "./Pagination.vue";
const props = defineProps<{ segmentId: string }>();
const route = useRoute(),
  router = useRouter();
type SelectedActivation = { id: string } & Partial<
  Omit<CommercialSegmentActivation, "id">
>;
const activations = ref<CommercialSegmentActivation[]>([]),
  selected = ref<SelectedActivation>();
const latestActivationId = ref<string>(),
  campaignChoice = ref<CommercialCampaignSegmentChoice>(),
  campaignError = ref("");
const historyPage = ref(0),
  historyTotal = ref(0),
  audiencePage = ref(0);
const audience = ref<
  | CommercialSegmentActivationAudience
  | CommercialSegmentActivationIdentityAudience
>();
const loading = ref(false),
  audienceLoading = ref(false),
  error = ref(""),
  audienceError = ref("");
let historyRequest = 0,
  audienceRequest = 0,
  campaignRequest = 0;
const canReadAudience = computed(() =>
  can(p.segmentsReadActivationAudience, p.segmentsReadActivationIdentities),
);
const canCreateCampaign = computed(
  () =>
    can(p.campaignsCreate) &&
    can(p.campaignsChooseSegments) &&
    can(p.campaignsResolveSegmentChoices),
);
function campaignAvailable(activation: SelectedActivation) {
  return (
    canCreateCampaign.value &&
    activation.id === latestActivationId.value &&
    campaignChoice.value?.activationId === activation.id &&
    campaignChoice.value.state === "AVAILABLE"
  );
}
async function checkCampaignAvailability() {
  campaignChoice.value = undefined;
  campaignError.value = "";
  const activationId = latestActivationId.value,
    request = ++campaignRequest;
  if (!canCreateCampaign.value || !activationId) return;
  try {
    const choice = await read(p.campaignsResolveSegmentChoices, () =>
      api.resolveCommercialCampaignSegmentChoice(props.segmentId, activationId),
    );
    if (request === campaignRequest) campaignChoice.value = choice;
  } catch (e) {
    if (request === campaignRequest) campaignError.value = errorMessage(e);
  }
}
const campaignNote = computed(() => {
  if (
    !canCreateCampaign.value ||
    selected.value?.id !== latestActivationId.value
  )
    return "";
  if (campaignChoice.value?.state === "SEGMENT_INACTIVE")
    return "Activate this audience before creating a campaign.";
  if (campaignChoice.value?.state === "ACTIVATION_SUPERSEDED")
    return "A newer activation is available. Refresh activations.";
  if (campaignChoice.value?.state === "ACTIVATION_UNAVAILABLE")
    return "This activation is unavailable for campaign creation.";
  return "";
});
const canOpenCustomer = computed(() =>
  can(
    p.subscriptionsRead,
    p.subscriptionsReadChanges,
    p.subscriptionsReadSpecialAgreements,
    p.billingListInvoices,
    p.billingReadAccountProfile,
    p.activitiesRead,
  ),
);
function campaignPath(activation: SelectedActivation) {
  return withReturnTo(
    "/commercial/campaigns/new?segment=" +
      props.segmentId +
      "&activation=" +
      activation.id,
    route.fullPath,
  );
}
async function loadHistory(page = 0) {
  historyPage.value = page;
  const request = ++historyRequest;
  loading.value = true;
  error.value = "";
  try {
    const result = await read(p.segmentsReadActivations, () =>
      api.commercialSegmentActivations(props.segmentId, page),
    );
    if (request !== historyRequest) return;
    activations.value = result.content;
    historyTotal.value = result.totalElements;
    if (page === 0) {
      latestActivationId.value = result.content[0]?.id;
      void checkCampaignAvailability();
    }
    const knownSelection = result.content.find(
      (activation) => activation.id === selected.value?.id,
    );
    if (knownSelection) selected.value = knownSelection;
    if (!selected.value) {
      const activation =
        typeof route.query.activation === "string"
          ? result.content.find(
              (activation) => activation.id === route.query.activation,
            ) || { id: route.query.activation }
          : result.content[0];
      if (activation) choose(activation);
    }
  } catch (e) {
    if (request === historyRequest) error.value = errorMessage(e);
  } finally {
    if (request === historyRequest) loading.value = false;
  }
}
function choose(activation: SelectedActivation, syncRoute = true) {
  selected.value = activation;
  audiencePage.value = 0;
  audience.value = undefined;
  if (canReadAudience.value) void loadAudience(0);
  if (syncRoute && route.query.activation !== activation.id)
    void router.replace({
      query: { ...route.query, activation: activation.id },
    });
}
async function loadAudience(page: number) {
  if (!selected.value) return;
  audiencePage.value = page;
  const activationId = selected.value.id,
    request = ++audienceRequest;
  audienceLoading.value = true;
  audienceError.value = "";
  try {
    const result = can(p.segmentsReadActivationIdentities)
      ? await read(p.segmentsReadActivationIdentities, () =>
          api.commercialSegmentActivationIdentities(
            props.segmentId,
            activationId,
            page,
          ),
        )
      : await read(p.segmentsReadActivationAudience, () =>
          api.commercialSegmentActivationAudience(
            props.segmentId,
            activationId,
            page,
          ),
        );
    if (request === audienceRequest) {
      audience.value = result;
      selected.value = {
        ...selected.value!,
        activationNumber: result.activationNumber,
        affectedAccountCount: result.immutableAccountCount,
      };
    }
  } catch (e) {
    if (request === audienceRequest) audienceError.value = errorMessage(e);
  } finally {
    if (request === audienceRequest) audienceLoading.value = false;
  }
}
watch(
  () => props.segmentId,
  () => {
    audienceRequest++;
    campaignRequest++;
    selected.value = undefined;
    audience.value = undefined;
    latestActivationId.value = undefined;
    campaignChoice.value = undefined;
    void loadHistory();
  },
  { immediate: true },
);
watch(
  () => route.query.activation,
  (id) => {
    if (typeof id === "string" && id !== selected.value?.id)
      choose(
        activations.value.find((activation) => activation.id === id) || { id },
        false,
      );
  },
);
watch(revision, () => {
  void loadHistory(0);
});
const accountRows = computed(
  () =>
    audience.value?.accounts.content.map((account) => ({
      accountId: account.accountId,
      accountName: "accountName" in account ? account.accountName : null,
      ownerEmail: "ownerEmail" in account ? account.ownerEmail : null,
      accountSlug: "accountSlug" in account ? account.accountSlug : null,
    })) || [],
);
</script>

<template>
  <section class="segment-activations">
    <p v-if="error" role="alert" class="form-error">
      {{ error }}
      <button class="text-link" @click="loadHistory(historyPage)">Retry</button>
    </p>
    <p v-else-if="loading" class="muted">Loading activations…</p>
    <p v-else-if="!activations.length" class="muted">
      No activation has frozen this audience yet.
    </p>
    <template v-else
      ><div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Activation</th>
              <th>Criteria version</th>
              <th>Customers</th>
              <th>Evaluated</th>
              <th>Reason</th>
              <th><span class="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="activation in activations"
              :key="activation.id"
              :class="{ selected: selected?.id === activation.id }"
            >
              <td>
                <button class="text-link" @click="choose(activation)">
                  Activation {{ activation.activationNumber }}
                </button>
              </td>
              <td>{{ activation.criteriaVersion }}</td>
              <td>{{ activation.affectedAccountCount.toLocaleString() }}</td>
              <td>{{ date(activation.evaluatedAt, true) }}</td>
              <td>{{ activation.reason }}</td>
              <td>
                <RouterLink
                  v-if="campaignAvailable(activation)"
                  class="text-link"
                  :to="campaignPath(activation)"
                  >Create campaign</RouterLink
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="historyPage"
        :size="20"
        :total="historyTotal"
        @change="loadHistory"
    /></template>
    <template v-if="selected"
      ><div class="audience-heading">
        <div>
          <h3>
            {{
              selected.activationNumber == null
                ? "Selected activation"
                : "Activation " + selected.activationNumber
            }}<template v-if="selected.affectedAccountCount != null">
              ·
              {{ selected.affectedAccountCount.toLocaleString() }}
              customers</template
            >
          </h3>
          <p v-if="selected.recordedAt || selected.criteriaVersion != null">
            Frozen {{ date(selected.recordedAt, true) }} · criteria version
            {{ selected.criteriaVersion }}
          </p>
        </div>
        <RouterLink
          v-if="campaignAvailable(selected)"
          class="button"
          :to="campaignPath(selected)"
          >Create campaign for this audience</RouterLink
        >
      </div>
      <p v-if="campaignNote" class="muted">
        {{ campaignNote }}
        <button class="text-link" @click="loadHistory(0)">Refresh</button>
      </p>
      <p
        v-if="
          campaignError &&
          canCreateCampaign &&
          selected.id === latestActivationId
        "
        class="muted"
      >
        Campaign availability could not be checked.
        <button class="text-link" @click="checkCampaignAvailability">
          Retry
        </button>
      </p>
      <p v-if="!canReadAudience" class="muted">
        Customer records in this activation are restricted for your account.
      </p>
      <p v-else-if="audienceError" role="alert" class="form-error">
        {{ audienceError }}
        <button class="text-link" @click="loadAudience(audiencePage)">
          Retry
        </button>
      </p>
      <p v-else-if="audienceLoading" class="muted">Loading customers…</p>
      <template v-else-if="audience"
        ><div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Customer</th>
                <th v-if="can(p.segmentsReadActivationIdentities)">Account</th>
                <th v-if="can(p.segmentsReadActivationIdentities)">Owner</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="account in accountRows" :key="account.accountId">
                <td>
                  <RouterLink
                    v-if="canOpenCustomer"
                    class="text-link"
                    :to="
                      contextualPath('/customers/' + account.accountId, route)
                    "
                    >{{ account.accountName || "Customer record" }}</RouterLink
                  ><span v-else>{{
                    account.accountName || "Restricted customer"
                  }}</span>
                </td>
                <td v-if="can(p.segmentsReadActivationIdentities)">
                  {{ account.accountSlug || "—" }}
                </td>
                <td v-if="can(p.segmentsReadActivationIdentities)">
                  {{ account.ownerEmail || "—" }}
                </td>
              </tr>
              <tr v-if="!accountRows.length">
                <td :colspan="can(p.segmentsReadActivationIdentities) ? 3 : 1">
                  No customers in this activation.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination
          :page="audiencePage"
          :size="20"
          :total="audience.accounts.totalElements"
          @change="loadAudience"
      /></template>
    </template>
  </section>
</template>

<style scoped>
.audience-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  border-top: 1px solid var(--border);
  margin-top: 24px;
  padding-top: 22px;
  margin-bottom: 15px;
}
.audience-heading h3 {
  margin: 0;
  font-size: 16px;
}
.audience-heading p {
  margin: 5px 0 0;
  color: var(--muted);
}
.selected {
  background: var(--accent-soft);
}
.muted {
  color: var(--muted);
}
@media (max-width: 700px) {
  .audience-heading {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
