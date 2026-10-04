<script setup lang="ts">
import { ref } from "vue";
import PageHeading from "@/components/PageHeading.vue";
import { session, notify, can } from "@/data/session";
import { adminApi } from "@/api/admin-api";
import { communicationApi } from "@/api/communication-api";
import { adminPermissions as p } from "@/auth/permissions";
import { read, write } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import ResourceState from "@/components/ResourceState.vue";
import AccessResult from "@/components/AccessResult.vue";
import { errorMessage } from "@/lib/format";
import type { RecordData } from "@/resources/types";
const result = ref<RecordData>(),
  busy = ref(false),
  error = ref("");
const language = useResource(() =>
  can(p.notificationsPreferences)
    ? read(p.notificationsPreferences, () => communicationApi.language(true))
    : Promise.resolve(undefined),
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
async function saveLanguage(value: string) {
  busy.value = true;
  try {
    await write(p.notificationsPreferences, () =>
      communicationApi.setLanguage(value as "fr" | "ar", true),
    );
    notify("Language saved.");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <PageHeading title="Profile" />
  <dl class="record-summary">
    <div>
      <dt>Email</dt>
      <dd>{{ session.me?.email }}</dd>
    </div>
    <div>
      <dt>Access</dt>
      <dd>{{ session.me?.isSuperAdmin ? "Administrator" : "Operator" }}</dd>
    </div>
    <div>
      <dt>Email verification</dt>
      <dd>{{ session.me?.emailVerified ? "Verified" : "Not verified" }}</dd>
    </div>
  </dl>
  <button
    v-if="!session.me?.emailVerified"
    class="button"
    :disabled="busy"
    @click="verify"
  >
    Verify email
  </button>
  <section v-if="can(p.notificationsPreferences)" class="report-section">
    <h2>Notification language</h2>
    <ResourceState
      :loading="language.loading.value"
      :error="language.error.value"
      @retry="language.refresh()"
      ><select
        aria-label="Notification language"
        :value="language.data.value?.language"
        :disabled="busy"
        @change="saveLanguage(($event.target as HTMLSelectElement).value)"
      >
        <option value="fr">French</option>
        <option value="ar">Arabic</option>
      </select></ResourceState
    ><RouterLink class="text-link" to="/settings?view=preferences"
      >Notification preferences</RouterLink
    >
  </section>
  <p v-if="error" class="notice error">{{ error }}</p>
  <AccessResult :result="result" @close="result = undefined" />
</template>
