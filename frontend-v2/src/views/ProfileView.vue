<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import AccessResult from "@/components/AccessResult.vue";
import PersonalNotificationSettings from "@/components/settings/PersonalNotificationSettings.vue";
import PersonalPermissionInventory from "@/components/settings/PersonalPermissionInventory.vue";
import PersonalAppearanceSettings from "@/components/settings/PersonalAppearanceSettings.vue";
import { session, can, notify } from "@/data/session";
import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { errorMessage } from "@/lib/format";
import type { RecordData } from "@/resources/types";
const route = useRoute();
const result = ref<RecordData>(),
  busy = ref(false),
  error = ref("");
const tabs = computed(() => [
  { key: "access", label: "My access" },
  ...(can(p.notificationsRead)
    ? [{ key: "notifications", label: "Notifications" }]
    : []),
  { key: "appearance", label: "Appearance" },
]);
const view = computed(
  () => tabs.value.find((tab) => tab.key === route.query.view)?.key || "access",
);
async function verify() {
  busy.value = true;
  error.value = "";
  try {
    result.value = await adminApi.sendMyEmailVerification();
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function refresh() {
  busy.value = true;
  error.value = "";
  try {
    session.me = await adminApi.me();
    notify("Profile refreshed.");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <PageHeading title="Profile"
    ><button class="button" :disabled="busy" @click="refresh">
      Refresh access
    </button></PageHeading
  >
  <ViewTabs :tabs="tabs" :current="view" />
  <PersonalNotificationSettings v-if="view === 'notifications'" />
  <PersonalAppearanceSettings v-else-if="view === 'appearance'" />
  <template v-else>
    <section v-if="!session.me?.emailVerified" class="notice warning">
      <strong>Verify your email to enable password recovery.</strong>
      <button class="button small" :disabled="busy" @click="verify">
        {{ busy ? "Processing…" : "Send verification" }}
      </button>
    </section>
    <dl class="record-summary">
      <div>
        <dt>Email</dt>
        <dd>{{ session.me?.email }}</dd>
      </div>
      <div>
        <dt>Access level</dt>
        <dd>
          {{ session.me?.isSuperAdmin ? "Platform administrator" : "Operator" }}
        </dd>
      </div>
      <div>
        <dt>Access state</dt>
        <dd>{{ session.me?.isActive ? "Active" : "Inactive" }}</dd>
      </div>
      <div>
        <dt>Email verification</dt>
        <dd>{{ session.me?.emailVerified ? "Verified" : "Not verified" }}</dd>
      </div>
    </dl>
    <PersonalPermissionInventory :permissions="session.me?.permissions || []" />
  </template>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p>
  <AccessResult :result="result" @close="result = undefined" />
</template>
