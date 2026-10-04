<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { adminApi } from "@/api/admin-api";
import type {
  AdminRole,
  AdminUserCreation,
  CreateAdminUserInput,
} from "@/api/contracts";
import { adminPermissions as p } from "@/auth/permissions";
import { can, session, invalidate } from "@/data/session";
import { read } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import ResourceState from "@/components/ResourceState.vue";
import Pagination from "@/components/Pagination.vue";
import { errorMessage } from "@/lib/format";
const emit = defineEmits<{
  created: [result: AdminUserCreation];
  dirty: [value: boolean];
}>();
const formElement = ref<HTMLFormElement>();
const step = ref(0),
  visited = ref(0),
  busy = ref(false),
  error = ref("");
const stages = ["Identity", "Roles", "Access", "Review"];
const draft = reactive<CreateAdminUserInput>({
  firstName: "",
  lastName: "",
  email: "",
  isSuperAdmin: false,
  initialAccessMethod: "EMAIL_LINK",
  roleIds: [],
});
const roleNames = new Map<string, string>();
const roleSearch = ref(""),
  rolePage = ref(0),
  noPermissionsConfirmed = ref(false);
const canAssignRoles = computed(
  () => can(p.usersAssignRole) && can(p.rolesRead),
);
const available = useResource(async () => {
  if (step.value < 1 || draft.isSuperAdmin || !canAssignRoles.value)
    return undefined;
  return read(p.rolesRead, () =>
    adminApi.roles({
      active: true,
      search: roleSearch.value.trim() || undefined,
      page: rolePage.value,
      size: 20,
      sort: "name",
      direction: "asc",
    }),
  );
}, [step, roleSearch, rolePage, () => draft.isSuperAdmin, canAssignRoles]);
const choices = computed(
  () =>
    available.data.value?.content.filter((role) =>
      role.availableActions.includes("ASSIGN_TO_OPERATOR"),
    ) || [],
);
watch(choices, (roles) =>
  roles.forEach((role) => roleNames.set(role.id, role.name)),
);
watch(roleSearch, () => (rolePage.value = 0));
watch(
  () => draft.isSuperAdmin,
  () => {
    draft.roleIds = [];
    noPermissionsConfirmed.value = false;
  },
);
watch(
  () => JSON.stringify([draft, noPermissionsConfirmed.value]),
  () => {
    error.value = "";
    emit("dirty", true);
  },
);
function toggleRole(role: AdminRole) {
  const ids = draft.roleIds || [];
  draft.roleIds = ids.includes(role.id)
    ? ids.filter((id) => id !== role.id)
    : [...ids, role.id];
  noPermissionsConfirmed.value = false;
}
function next() {
  error.value = "";
  if (!formElement.value?.reportValidity()) return;
  if (
    step.value === 1 &&
    !draft.isSuperAdmin &&
    !draft.roleIds?.length &&
    !noPermissionsConfirmed.value
  ) {
    error.value =
      "Choose an access role or confirm that access will be assigned later.";
    return;
  }
  if (step.value < stages.length - 1) {
    step.value++;
    visited.value = Math.max(visited.value, step.value);
  }
}
async function create() {
  if (!can(p.usersCreate) || !formElement.value?.reportValidity()) return;
  const emailInput = document.createElement("input");
  emailInput.type = "email";
  emailInput.required = true;
  emailInput.value = draft.email.trim();
  if (
    !draft.firstName.trim() ||
    !draft.lastName.trim() ||
    !emailInput.validity.valid
  ) {
    step.value = 0;
    error.value = "Enter the operator's name and a valid login email.";
    return;
  }
  if (draft.isSuperAdmin && !session.me?.isSuperAdmin) {
    step.value = 1;
    error.value =
      "Your access no longer allows platform administrator creation.";
    return;
  }
  if (
    !draft.isSuperAdmin &&
    !draft.roleIds?.length &&
    !noPermissionsConfirmed.value
  ) {
    step.value = 1;
    next();
    return;
  }
  if (draft.roleIds?.length && !canAssignRoles.value) {
    error.value =
      "Your access no longer allows role assignment. Review the selected roles.";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const result = await read(p.usersCreate, () =>
      adminApi.createUser({
        ...draft,
        firstName: draft.firstName.trim(),
        lastName: draft.lastName.trim(),
        email: draft.email.trim(),
        isSuperAdmin: !!session.me?.isSuperAdmin && draft.isSuperAdmin,
        roleIds: draft.isSuperAdmin ? [] : [...(draft.roleIds || [])],
      }),
    );
    emit("dirty", false);
    emit("created", result);
    invalidate();
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <p v-if="!can(p.usersCreate)" class="notice">
    Your access does not allow operator creation.
  </p>
  <form
    v-else
    ref="formElement"
    class="operator-create-flow"
    @submit.prevent="step === stages.length - 1 ? create() : next()"
  >
    <nav aria-label="Operator setup" class="flow-stages">
      <button
        v-for="(stage, index) in stages"
        :key="stage"
        type="button"
        :disabled="busy || index > visited"
        :aria-current="step === index ? 'step' : undefined"
        :class="{ current: step === index }"
        @click="
          step = index;
          error = '';
        "
      >
        <span>{{ index + 1 }}</span
        >{{ stage }}
      </button>
    </nav>
    <fieldset :disabled="busy">
      <template v-if="step === 0">
        <h2>Operator identity</h2>
        <div class="editor-grid">
          <label class="field"
            >First name<input
              v-model="draft.firstName"
              autocomplete="given-name"
              required
              maxlength="100"
          /></label>
          <label class="field"
            >Last name<input
              v-model="draft.lastName"
              autocomplete="family-name"
              required
              maxlength="100"
          /></label>
          <label class="field full-width"
            >Login email<input
              v-model="draft.email"
              type="email"
              autocomplete="email"
              required
              maxlength="320"
          /></label>
        </div>
      </template>
      <template v-else-if="step === 1">
        <h2>Access roles</h2>
        <label v-if="session.me?.isSuperAdmin" class="checkbox-line"
          ><input v-model="draft.isSuperAdmin" type="checkbox" />Platform
          administrator — full platform access</label
        >
        <p v-if="draft.isSuperAdmin" class="notice">
          Platform administrators have full access without assigned roles.
        </p>
        <template v-else>
          <template v-if="canAssignRoles">
            <label class="search-field"
              ><input
                v-model="roleSearch"
                type="search"
                aria-label="Search assignable roles"
                placeholder="Search roles"
            /></label>
            <ResourceState
              :loading="available.loading.value"
              :error="available.error.value"
              :empty="!choices.length"
              title="No assignable roles"
              description="Roles must be active and within your access level."
              @retry="available.refresh()"
            >
              <div class="role-options">
                <label
                  v-for="role in choices"
                  :key="role.id"
                  class="role-option"
                  ><input
                    type="checkbox"
                    :checked="draft.roleIds?.includes(role.id)"
                    :disabled="
                      !draft.roleIds?.includes(role.id) &&
                      (draft.roleIds?.length || 0) >= 100
                    "
                    @change="toggleRole(role)"
                  />
                  <span
                    ><strong>{{ role.name }}</strong
                    ><small v-if="role.description" class="muted">{{
                      role.description
                    }}</small></span
                  >
                </label>
              </div>
            </ResourceState>
            <Pagination
              v-if="available.data.value"
              :page="rolePage"
              :size="20"
              :total="available.data.value.totalElements"
              @change="rolePage = $event"
            />
            <div v-if="draft.roleIds?.length" class="selected-records">
              <button
                v-for="id in draft.roleIds"
                :key="id"
                type="button"
                class="selected-record"
                @click="
                  draft.roleIds = draft.roleIds?.filter((value) => value !== id)
                "
              >
                {{ roleNames.get(id) || "Selected role" }} ×
              </button>
            </div>
          </template>
          <p v-else class="notice">
            Your access does not allow role assignment. An administrator must
            finish this operator's access.
          </p>
          <label v-if="!draft.roleIds?.length" class="checkbox-line"
            ><input v-model="noPermissionsConfirmed" type="checkbox" />Assign
            access later. This operator will have no permissions.</label
          >
        </template>
      </template>
      <template v-else-if="step === 2">
        <h2>Initial access</h2>
        <label class="role-option"
          ><input
            v-model="draft.initialAccessMethod"
            type="radio"
            value="EMAIL_LINK"
          /><span
            ><strong>Email invitation</strong
            ><small class="muted"
              >Send an activation link after access is created.</small
            ></span
          ></label
        >
        <label class="role-option"
          ><input
            v-model="draft.initialAccessMethod"
            type="radio"
            value="TEMPORARY_PASSWORD"
          /><span
            ><strong>Temporary password</strong
            ><small class="muted"
              >Shown once. The operator sets a new password at first
              sign-in.</small
            ></span
          ></label
        >
      </template>
      <template v-else>
        <h2>Review operator</h2>
        <dl class="record-summary">
          <div>
            <dt>Name</dt>
            <dd>{{ draft.firstName.trim() }} {{ draft.lastName.trim() }}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{{ draft.email.trim() }}</dd>
          </div>
          <div>
            <dt>Access level</dt>
            <dd>
              {{ draft.isSuperAdmin ? "Platform administrator" : "Operator" }}
            </dd>
          </div>
          <div>
            <dt>Roles</dt>
            <dd>
              {{
                draft.isSuperAdmin
                  ? "Full platform access"
                  : draft.roleIds?.length
                    ? draft.roleIds
                        .map((id) => roleNames.get(id) || "Selected role")
                        .join(", ")
                    : "No permissions — assign later"
              }}
            </dd>
          </div>
          <div>
            <dt>Initial access</dt>
            <dd>
              {{
                draft.initialAccessMethod === "EMAIL_LINK"
                  ? "Email invitation"
                  : "Temporary password"
              }}
            </dd>
          </div>
        </dl>
      </template>
    </fieldset>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div class="dialog-actions">
      <button
        v-if="step"
        type="button"
        class="button"
        :disabled="busy"
        @click="
          step--;
          error = '';
        "
      >
        Back
      </button>
      <button class="button primary" :disabled="busy">
        {{
          busy
            ? "Creating…"
            : step === stages.length - 1
              ? draft.initialAccessMethod === "EMAIL_LINK"
                ? "Create and send invitation"
                : "Create operator"
              : "Continue"
        }}
      </button>
    </div>
  </form>
</template>
<style scoped>
.operator-create-flow {
  max-width: 56rem;
}
fieldset {
  padding: 0;
  margin: 1.5rem 0;
  border: 0;
  min-width: 0;
}
h2 {
  margin-bottom: 1rem;
}
.flow-stages {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  border-bottom: 1px solid var(--border);
  padding-bottom: 1rem;
}
.flow-stages button {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 44px;
  padding: 0.5rem 0.875rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: transparent;
  color: inherit;
  font: inherit;
}
.flow-stages button.current {
  border-color: var(--border);
  font-weight: 600;
  background: var(--surface-soft);
}
.flow-stages button:disabled {
  opacity: 0.5;
}
.role-options {
  margin: 1rem 0;
}
.role-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem 0;
  min-height: 44px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
}
.role-option input {
  margin-top: 0.25rem;
}
.role-option small {
  display: block;
  margin-top: 0.35rem;
  line-height: 1.5;
}
.checkbox-line {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 44px;
  margin: 0.75rem 0;
}
</style>
