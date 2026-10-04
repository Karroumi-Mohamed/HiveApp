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
import { date, money, errorMessage, label } from "@/lib/format";
const route = useRoute(),
  router = useRouter();
const key = computed(() => String(route.meta.resource)),
  resource = computed(() => resources[key.value]!);
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
        ? resource.value.createPermission!
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
      resource.value.saveDestination?.(result) ||
      resource.value.base +
        "/" +
        (result.id ||
          result.summary?.id ||
          result.offerId ||
          result.campaignId ||
          id.value);
    if (result.temporaryPassword || result.emailDelivery) {
      savedPath = next;
      accessResult.value = result;
      return;
    }
    await router.push(next);
    await loaded.refresh();
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
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
const sectionPage = ref(0);
watch(view, () => (sectionPage.value = 0));
const sectionData = useResource(
  () =>
    section.value && data.value
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
    can(
      creating.value
        ? resource.value.createPermission || ""
        : resource.value.editPermission || "",
    ) &&
    (!creating.value ||
      (resource.value.createRequirements || []).every((x) => can(x))) &&
    (creating.value ||
      !resource.value.editable ||
      (!!data.value && resource.value.editable(data.value))),
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
  <RouterLink class="back-link" :to="resource.base">{{
    resource.title
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
    <form
      v-else-if="editing"
      @submit.prevent="save"
      @input="changed"
      @change="changed"
    >
      <div class="editor-grid">
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
          :to="creating ? resource.base : resource.base + '/' + id"
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
          :to="{
            path: '/catalog/prices/new',
            query: {
              product: id,
              type:
                key === 'plans'
                  ? 'PLAN'
                  : key === 'addons'
                    ? 'ADD_ON'
                    : 'QUOTA_PACKAGE',
            },
          }"
          >Add price</RouterLink
        ><RouterLink
          v-if="key === 'plans' && can('platform.plans.create_version_rollout')"
          class="text-link"
          :to="{ path: '/operations/rollouts/new', query: { plan: id } }"
          >Apply revision</RouterLink
        ><RouterLink
          v-if="
            key === 'prices' && can('platform.subscriptions.preview_repricing')
          "
          class="text-link"
          :to="{ path: '/operations/repricing/new', query: { price: id } }"
          >Reprice subscribers</RouterLink
        >
      </div>
      <ViewTabs :tabs="sectionTabs" :current="selectedTab" />
      <div v-if="sectionChoices.length > 1" class="resource-toolbar">
        <select
          :value="view"
          aria-label="Record view"
          @change="
            router.replace({
              query: {
                ...route.query,
                view: ($event.target as HTMLSelectElement).value,
              },
            })
          "
        >
          <option v-for="s in sectionChoices" :key="s.key" :value="s.key">
            {{ s.label }}
          </option>
        </select>
      </div>
      <template v-if="!section"
        ><dl v-if="resource.facts" class="record-summary">
          <div v-for="fact in resource.facts" :key="fact.key">
            <dt>{{ fact.label }}</dt>
            <dd>{{ formatted(data, fact) }}</dd>
          </div>
        </dl>
        <Facts v-if="!data._restrictedSummary" :data="details"
      /></template>
      <OwnerLookup
        v-if="section?.key === 'subscribers' && key === 'plans'"
        :plan-id="id" /><ResourceState
        v-if="section"
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
                    :to="column.link!(row)!"
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
