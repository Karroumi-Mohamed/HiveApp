<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted, onUnmounted } from "vue";
import {
  useRoute,
  useRouter,
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
} from "vue-router";
import OwnerLookup from "@/components/OwnerLookup.vue";
import AccessResult from "@/components/AccessResult.vue";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceState from "@/components/ResourceState.vue";
import Facts from "@/components/Facts.vue";
import FieldInput from "@/components/FieldInput.vue";
import ActionDialog from "@/components/ActionDialog.vue";
import AppDialog from "@/components/AppDialog.vue";
import CellValue from "@/components/CellValue.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import Pagination from "@/components/Pagination.vue";
import { resources } from "@/resources";
import { get, rows, normalizeInput, validateFields } from "@/resources/types";
import type { RecordData, Action } from "@/resources/types";
import { read, write } from "@/data/gateway";
import { can, notify } from "@/data/session";
import { useResource } from "@/composables/useResource";
import { destination } from "@/lib/destination";
import { safeReturnTo, withReturnTo, contextualPath } from "@/lib/navigation";
import OfferDefinitionSummary from "@/components/OfferDefinitionSummary.vue";
import OfferWorkbench from "@/components/OfferWorkbench.vue";
import ExecutionSummary from "@/components/ExecutionSummary.vue";
import ExecutionResults from "@/components/ExecutionResults.vue";
import PlanComparison from "@/components/PlanComparison.vue";
import PlanFeatureInspector from "@/components/PlanFeatureInspector.vue";
import SegmentActivations from "@/components/SegmentActivations.vue";
import OperatorCreateFlow from "@/components/settings/OperatorCreateFlow.vue";
import { date, money, errorMessage, label } from "@/lib/format";
const route = useRoute(),
  router = useRouter();
const key = computed(() => String(route.meta.resource)),
  resource = computed(() => resources[key.value]!);
const returnPath = computed(
  () => safeReturnTo(route.query.returnTo) || resource.value.base,
);
const id = computed(() => String(route.params.id || ""));
const creating = computed(() => id.value === "new"),
  editing = computed(() => creating.value || route.query.edit === "1");
const loaded = useResource(
  async () => {
    if (creating.value) return undefined;
    if (can(resource.value.readPermission))
      return read(resource.value.readPermission, () =>
        resource.value.detail(id.value),
      );
    const alternate = resource.value.alternateDetail?.find((x) =>
      can(x.permission),
    );
    if (alternate)
      return read(alternate.permission, () => alternate.load(id.value));
    if (resource.value.sections?.some((x) => can(x.permission)))
      return {
        id: id.value,
        name: resource.value.singular,
        _restrictedSummary: true,
      };
    throw Error("You do not have permission to view this record.");
  },
  [key, id],
  4000,
  (): boolean =>
    ["rollouts", "repricing"].includes(key.value) &&
    ["ASSESSING", "QUEUED", "SCHEDULED", "RUNNING", "CONFIRMED"].includes(
      (loaded.data.value as RecordData)?.summary?.status,
    ),
);
const data = computed<RecordData | undefined>(() => {
  const raw = loaded.data.value as RecordData | undefined;
  return raw
    ? {
        ...(resource.value.normalize?.(raw) || raw),
        _accountId: route.query.account,
      }
    : undefined;
});
const accessResult = ref<RecordData>();
let savedPath = "";
const form = reactive<RecordData>({}),
  busy = ref(false),
  error = ref(""),
  dirty = ref(false),
  saved = ref(false);
let pristine = "";
watch(
  [data, key, editing],
  () => {
    Object.keys(form).forEach((k) => delete form[k]);
    Object.assign(
      form,
      JSON.parse(JSON.stringify(resource.value.defaults || {})),
      data.value ? JSON.parse(JSON.stringify(data.value)) : {},
    );
    if (creating.value && key.value === "prices" && route.query.product) {
      form.ownerId = String(route.query.product);
      form.ownerType = String(route.query.type || "PLAN");
    }
    if (creating.value && key.value === "messages" && route.query.account)
      form.accountIds = [String(route.query.account)];
    if (
      creating.value &&
      key.value === "campaigns" &&
      typeof route.query.segment === "string" &&
      typeof route.query.activation === "string"
    ) {
      form.audience = { ...form.audience, mode: "SEGMENT" };
      form.segmentSelection =
        route.query.segment + ":" + route.query.activation;
    }
    pristine = JSON.stringify(form);
    dirty.value = false;
    saved.value = false;
  },
  { immediate: true },
);
watch(
  () => JSON.stringify(form),
  (next) => {
    dirty.value = editing.value && next !== pristine;
  },
);
function changed() {
  dirty.value = true;
  error.value = "";
}
async function save() {
  if (!resource.value.save) return;
  error.value = "";
  const fields = resource.value.fields || [];
  const issue = validateFields(form, fields);
  if (issue) {
    error.value = issue;
    return;
  }
  busy.value = true;
  try {
    const result = (await write(
      creating.value
        ? resource.value.creationPermission?.(form) ||
            resource.value.createPermission!
        : resource.value.editPermission!,
      () =>
        resource.value.save!(
          creating.value ? undefined : id.value,
          normalizeInput(form, fields),
          data.value,
        ),
    )) as RecordData;
    dirty.value = false;
    saved.value = true;
    notify("Saved.");
    const next =
      campaignReturn(result) ||
      resource.value.saveDestination?.(result) ||
      resource.value.base +
        "/" +
        (result.id ||
          result.summary?.id ||
          result.offerId ||
          result.campaignId ||
          id.value);
    if (result.temporaryPassword || result.emailDelivery) {
      savedPath = contextualPath(next, route);
      accessResult.value = result;
      return;
    }
    await router.push(
      campaignReturn(result) ? next : contextualPath(next, route),
    );
    await loaded.refresh();
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
function campaignReturn(result: RecordData): string | undefined {
  const source = safeReturnTo(route.query.returnTo);
  if (!creating.value || key.value !== "campaigns" || !source) return undefined;
  const url = new URL(source, window.location.origin);
  if (url.pathname !== "/commercial/offers/new") return undefined;
  url.searchParams.set("campaign", result.campaignId || result.id);
  return url.pathname + url.search;
}
async function finishCustomSave(result: RecordData) {
  dirty.value = false;
  saved.value = true;
  notify("Saved.");
  const next =
    key.value === "operators" && !can(resource.value.readPermission)
      ? safeReturnTo(route.query.returnTo) || "/settings/profile"
      : contextualPath(
          resource.value.saveDestination?.(result) ||
            resource.value.base +
              "/" +
              (result.id || result.operator?.id || result.offerId || id.value),
          route,
        );
  if (result.temporaryPassword || result.emailDelivery) {
    savedPath = next;
    accessResult.value = result;
    return;
  }
  await router.push(next);
  await loaded.refresh();
}
async function finishAccess() {
  accessResult.value = undefined;
  await router.push(savedPath);
  await loaded.refresh();
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
onMounted(() => window.addEventListener("beforeunload", beforeUnload));
onUnmounted(() => window.removeEventListener("beforeunload", beforeUnload));
const action = ref<Action>();
const sections = computed(
  () => resource.value.sections?.filter((s) => can(s.permission)) || [],
);
const view = computed(() => String(route.query.view || "details"));
const section = computed(() =>
  sections.value.find((s) => s.key === view.value),
);
const sectionTabs = computed(() => {
  const groups = new Map<string, { key: string; label: string }>();
  for (const item of sections.value) {
    const group = item.group?.key || item.key;
    if (!groups.has(group))
      groups.set(group, {
        key: item.key,
        label: item.group?.label || item.label,
      });
  }
  return [{ key: "details", label: "Details" }, ...groups.values()];
});
const sectionChoices = computed(() =>
  section.value?.group
    ? sections.value.filter((x) => x.group?.key === section.value?.group?.key)
    : [],
);
const selectedTab = computed(() => {
  const group = section.value?.group?.key;
  if (!group) return view.value;
  return sections.value.find((x) => x.group?.key === group)?.key || view.value;
});
const sectionPage = computed({
  get: () => Math.max(0, Number(route.query.sectionPage) || 0),
  set: (value) => {
    void router.replace({
      query: { ...route.query, sectionPage: value || undefined },
    });
  },
});
const executionResults = computed(
  () =>
    ["repricing", "rollouts"].includes(key.value) &&
    section.value?.key === "results",
);
const sectionData = useResource(
  () =>
    section.value && data.value && !executionResults.value
      ? read(section.value.permission, () =>
          section.value!.load(data.value!, sectionPage.value),
        )
      : Promise.resolve(undefined),
  [section, data, sectionPage],
);
const records = computed<any>(() => {
  const value = sectionData.data.value as RecordData | undefined;
  return value?.accounts || value?.versions || value;
});
const sectionRows = computed(() => rows(records.value));
const canEdit = computed(
  () =>
    !!resource.value.save &&
    (creating.value
      ? (
          resource.value.createPermissions || [
            resource.value.createPermission || "",
          ]
        ).some((permission) => can(permission))
      : can(resource.value.editPermission || "")) &&
    (!creating.value ||
      (resource.value.createRequirements || []).every((x) => can(x))) &&
    (creating.value ||
      (!data.value?._operationsOnly &&
        !data.value?._restrictedSummary &&
        (!resource.value.editable ||
          (!!data.value && resource.value.editable(data.value))))),
);
const availableActions = computed(
  () =>
    resource.value.actions?.filter(
      (a) =>
        data.value &&
        !data.value._restrictedSummary &&
        can(a.permission) &&
        (!a.visible || a.visible(data.value)),
    ) || [],
);
const primaryAction = computed(() =>
  availableActions.value.find((a) =>
    [
      "ACTIVATE",
      "SCHEDULE",
      "PUBLISH",
      "activate",
      "confirm",
      "apply",
    ].includes(a.key),
  ),
);
const moreActions = computed(() =>
  availableActions.value.filter((a) => a !== primaryAction.value),
);
const details = computed(() => {
  const d = data.value || {};
  return resource.value.detailKeys
    ? Object.fromEntries(resource.value.detailKeys.map((k) => [k, get(d, k)]))
    : Object.fromEntries(
        Object.entries(d).filter(
          ([k]) =>
            ![
              "summary",
              "availableActions",
              "blockedActions",
              "blockers",
              "version",
              "lineageId",
              "ownerIdentityRestricted",
              "audienceIdentityRestricted",
              "creationReason",
              "sourcePlanId",
              "sourceCampaignId",
              "sourcePolicyId",
              "sourceSegmentId",
              "_accountId",
              "_restrictedSummary",
              "_operationsOnly",
              "_editableDefinition",
            ].includes(k),
        ),
      );
});
function formatted(row: RecordData, column: any) {
  const value = get(row, column.key);
  if (column.format === "date") return date(value, true);
  if (column.format === "money")
    return value == null
      ? "—"
      : money(
          String(value),
          get(row, column.currencyKey || "currencyCode") || "MAD",
        );
  if (column.format === "boolean")
    return value == null ? "—" : value ? "Yes" : "No";
  return value == null
    ? "—"
    : typeof value === "object"
      ? Array.isArray(value)
        ? value.map((x) => x.name || x.code || x).join(", ")
        : label(String(value))
      : String(value);
}
const leaveOpen = ref(false);
let answer: ((allow: boolean) => void) | undefined;
function guard() {
  if (!dirty.value || saved.value) return true;
  leaveOpen.value = true;
  return new Promise<boolean>((resolve) => (answer = resolve));
}
onBeforeRouteLeave(guard);
onBeforeRouteUpdate(guard);
function leave(allow: boolean) {
  leaveOpen.value = false;
  if (allow) dirty.value = false;
  answer?.(allow);
  answer = undefined;
}
</script>
<template>
  <RouterLink class="back-link" :to="returnPath">{{
    route.query.returnTo ? "Back to previous view" : resource.title
  }}</RouterLink
  ><PageHeading
    :title="
      creating
        ? 'New ' + resource.singular.toLowerCase()
        : data?.name || data?.messageTitle || data?.email || resource.singular
    "
    ><template v-if="!editing && data"
      ><StatusBadge
        v-if="data.status || data.state"
        :status="data.status || data.state"
      /><RouterLink
        v-if="
          resource.editPermission &&
          can(resource.editPermission) &&
          !data._operationsOnly &&
          !data._restrictedSummary &&
          (!resource.editable || resource.editable(data))
        "
        class="button"
        :to="{ query: { ...route.query, edit: '1' } }"
        >Edit</RouterLink
      ><button
        v-if="primaryAction"
        class="button primary"
        @click="action = primaryAction"
      >
        {{ primaryAction.label }}
      </button>
      <details v-if="moreActions.length" class="more-actions">
        <summary class="button">Actions</summary>
        <div class="action-menu">
          <button
            v-for="a in moreActions"
            :key="a.key"
            @click="
              action = a;
              ($event.currentTarget as HTMLElement)
                .closest('details')
                ?.removeAttribute('open');
            "
          >
            {{ a.label }}
          </button>
        </div>
      </details></template
    ></PageHeading
  >
  <ResourceState
    :loading="loaded.loading.value"
    :error="loaded.error.value"
    @retry="loaded.refresh()"
    ><p v-if="editing && !canEdit" class="notice error">
      You cannot edit this record.
    </p>
    <OfferWorkbench
      v-else-if="editing && key === 'offers'"
      :key="id"
      :resource="resource"
      :id="creating ? undefined : id"
      :data="data"
      :creating="creating"
      @saved="finishCustomSave"
      @cancel="router.push(returnPath)" />
    <OperatorCreateFlow
      v-else-if="creating && key === 'operators'"
      @created="finishCustomSave"
      @dirty="dirty = $event" />
    <form
      v-else-if="editing"
      @submit.prevent="save"
      @input="changed"
      @change="changed"
    >
      <template v-if="resource.formGroups?.length"
        ><fieldset
          v-for="group in resource.formGroups"
          :key="group.label"
          class="form-section"
        >
          <legend>{{ group.label }}</legend>
          <div class="editor-grid">
            <FieldInput
              v-for="field in resource.fields?.filter((f) =>
                group.keys.includes(f.key),
              )"
              :key="field.key"
              :field="field"
              :data="form"
            />
          </div></fieldset
      ></template>
      <div v-else class="editor-grid">
        <FieldInput
          v-for="field in resource.fields"
          :key="field.key + field.label"
          :field="field"
          :data="form"
        />
      </div>
      <p v-if="error" class="notice error section-gap" role="alert">
        {{ error }}
      </p>
      <div class="dialog-actions">
        <RouterLink
          class="button"
          :to="
            creating
              ? returnPath
              : contextualPath(resource.base + '/' + id, route)
          "
          >Cancel</RouterLink
        ><button class="button primary" :disabled="busy">
          {{ busy ? "Saving…" : "Save" }}
        </button>
      </div>
    </form>
    <template v-else-if="data"
      ><div class="related-actions">
        <RouterLink
          v-if="
            ['plans', 'addons', 'capacity'].includes(key) &&
            can('platform.price_books.create')
          "
          class="text-link"
          :to="
            withReturnTo(
              '/catalog/prices/new?product=' +
                id +
                '&type=' +
                (key === 'plans'
                  ? 'PLAN'
                  : key === 'addons'
                    ? 'ADD_ON'
                    : 'QUOTA_PACKAGE'),
              route.fullPath,
            )
          "
          >Add price</RouterLink
        ><RouterLink
          v-if="key === 'plans' && can('platform.plans.create_version_rollout')"
          class="text-link"
          :to="
            withReturnTo(
              '/operations/rollouts/new?targetPlan=' + id,
              route.fullPath,
            )
          "
          >Apply revision</RouterLink
        ><RouterLink
          v-if="
            key === 'prices' && can('platform.subscriptions.preview_repricing')
          "
          class="text-link"
          :to="
            withReturnTo(
              '/operations/repricing/new?price=' + id,
              route.fullPath,
            )
          "
          >Reprice subscribers</RouterLink
        >
      </div>
      <div v-if="resource.links?.(data).length" class="related-actions">
        <RouterLink
          v-for="link in resource.links?.(data) || []"
          :key="link.to"
          class="text-link"
          :to="withReturnTo(link.to, route.fullPath)"
          >{{ link.label }}</RouterLink
        >
      </div>
      <ViewTabs :tabs="sectionTabs" :current="selectedTab" />
      <nav
        v-if="sectionChoices.length > 1"
        class="section-switches"
        aria-label="Related views"
      >
        <RouterLink
          v-for="s in sectionChoices"
          :key="s.key"
          :to="{
            query: { ...route.query, view: s.key, sectionPage: undefined },
          }"
          :class="{ selected: view === s.key }"
          :aria-current="view === s.key ? 'page' : undefined"
          >{{ s.label }}</RouterLink
        >
      </nav>
      <template v-if="!section"
        ><ExecutionSummary
          v-if="['repricing', 'rollouts'].includes(key)"
          :data="data"
          :kind="key" />
        <dl
          v-if="!['repricing', 'rollouts'].includes(key)"
          class="record-summary"
        >
          <div
            v-for="fact in resource.facts || resource.columns"
            :key="fact.key"
          >
            <dt>{{ fact.label }}</dt>
            <dd>{{ formatted(data, fact) }}</dd>
          </div>
        </dl>
        <OfferDefinitionSummary v-if="key === 'offers'" :data="data" />
        <details v-if="!data._restrictedSummary" class="evidence-disclosure">
          <summary>Record details</summary>
          <Facts :data="details" /></details
      ></template>
      <PlanComparison
        v-if="
          key === 'plans' &&
          ['comparison', 'revision-comparison'].includes(section?.key || '')
        "
        :plan-id="id"
        :mode="
          section?.key === 'revision-comparison' ? 'revisions' : 'catalog'
        " />
      <PlanFeatureInspector
        v-else-if="key === 'plans' && section?.key === 'feature-map'"
        :plan-id="id" />
      <SegmentActivations
        v-else-if="key === 'segments' && section?.key === 'activations'"
        :segment-id="id" />
      <ExecutionResults
        v-else-if="executionResults"
        :id="id"
        :kind="key as 'repricing' | 'rollouts'"
        :detail="data"
        :actions="section?.actions"
        @changed="loaded.refresh()" />
      <OwnerLookup
        v-if="section?.key === 'subscribers' && key === 'plans'"
        :plan-id="id" /><ResourceState
        v-if="
          section &&
          !executionResults &&
          !(
            key === 'plans' &&
            ['comparison', 'revision-comparison', 'feature-map'].includes(
              section.key,
            )
          ) &&
          !(key === 'segments' && section.key === 'activations')
        "
        :loading="sectionData.loading.value"
        :error="sectionData.error.value"
        :empty="
          Array.isArray(records) || (records as any)?.content
            ? !sectionRows.length
            : false
        "
        title="No records"
        @retry="sectionData.refresh()"
        ><div v-if="section.columns" class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th v-for="column in section.columns">{{ column.label }}</th>
                <th v-if="section.actions"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in sectionRows" :key="row.id">
                <td v-for="column in section.columns">
                  <StatusBadge
                    v-if="column.format === 'status'"
                    :status="String(get(row, column.key))"
                  /><RouterLink
                    v-else-if="column.link?.(row)"
                    class="text-link"
                    :to="withReturnTo(column.link!(row)!, route.fullPath)"
                    >{{ formatted(row, column) }}</RouterLink
                  ><CellValue
                    v-else
                    :value="
                      column.format
                        ? formatted(row, column)
                        : get(row, column.key)
                    "
                    :name="column.key"
                  />
                </td>
                <td v-if="section.actions">
                  <button
                    v-for="a in section
                      .actions(row, data)
                      .filter(
                        (a) =>
                          can(a.permission) && (!a.visible || a.visible(row)),
                      )"
                    class="text-link"
                    @click="
                      action = {
                        ...a,
                        defaults: () => a.defaults?.(row) || {},
                        destination: a.destination
                          ? (result) => a.destination!(result, row)
                          : undefined,
                        execute: (_, input, preview) =>
                          a.execute(row, input, preview),
                        preview: a.preview
                          ? (_, input) => a.preview!(row, input)
                          : undefined,
                      }
                    "
                  >
                    {{ a.label }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Facts v-else :data="records" /><Pagination
          v-if="(records as any)?.totalElements !== undefined"
          :page="(records as any).page"
          :size="(records as any).size"
          :total="(records as any).totalElements"
          @change="sectionPage = $event" /></ResourceState></template
  ></ResourceState>
  <ActionDialog
    :action="action"
    :data="data || {}"
    @close="action = undefined"
    @done="
      loaded.refresh();
      sectionData.refresh();
    "
  />
  <AccessResult :result="accessResult" @close="finishAccess" /><AppDialog
    :open="leaveOpen"
    title="Discard changes?"
    @close="leave(false)"
    ><div class="dialog-actions">
      <button class="button" @click="leave(false)">Keep editing</button
      ><button class="button danger" @click="leave(true)">Discard</button>
    </div></AppDialog
  >
</template>
