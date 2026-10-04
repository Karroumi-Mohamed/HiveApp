<script setup lang="ts">
import { computed, ref } from "vue";
import {
  communicationApi,
  type NotificationSetting,
  type NotificationLanguage,
} from "@/api/communication-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can, notify } from "@/data/session";
import { read } from "@/data/gateway";
import { useResource } from "@/composables/useResource";
import ResourceState from "@/components/ResourceState.vue";
import { label, errorMessage } from "@/lib/format";
const settings = useResource(() =>
  read(p.notificationsRead, () => communicationApi.settings(true)),
);
const language = useResource(() =>
  read(p.notificationsRead, () => communicationApi.language(true)),
);
const editable = computed(
  () => can(p.notificationsPreferences) && can(p.notificationsRead),
);
const pendingTopics = ref<string[]>([]),
  languageBusy = ref(false),
  error = ref("");
async function setChannel(
  setting: NotificationSetting,
  field: "inAppEnabled" | "emailEnabled",
  enabled: boolean,
) {
  if (!editable.value || pendingTopics.value.includes(setting.topic)) return;
  error.value = "";
  pendingTopics.value = [...pendingTopics.value, setting.topic];
  try {
    const updated = await read(p.notificationsPreferences, () =>
      communicationApi.setting({ ...setting, [field]: enabled }, true),
    );
    settings.data.value = settings.data.value?.map((row) =>
      row.topic === updated.topic ? updated : row,
    );
    notify("Notification preference saved.");
  } catch (e) {
    error.value = errorMessage(e);
    await settings.refresh(true);
  } finally {
    pendingTopics.value = pendingTopics.value.filter(
      (topic) => topic !== setting.topic,
    );
  }
}
async function setLanguage(value: string) {
  if (!editable.value) return;
  languageBusy.value = true;
  error.value = "";
  try {
    language.data.value = await read(p.notificationsPreferences, () =>
      communicationApi.setLanguage(value as NotificationLanguage, true),
    );
    notify("Notification language saved.");
  } catch (e) {
    error.value = errorMessage(e);
    await language.refresh(true);
  } finally {
    languageBusy.value = false;
  }
}
</script>
<template>
  <section class="report-section">
    <h2>Notification preferences</h2>
    <p class="muted small">
      Optional notices. Required warnings and marketing consent remain separate.
    </p>
    <p v-if="!editable" class="notice">
      You can view these preferences. Your access does not allow changes.
    </p>
    <ResourceState
      :loading="settings.loading.value"
      :error="settings.error.value"
      @retry="settings.refresh()"
    >
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">Topic</th>
              <th scope="col">In-app</th>
              <th scope="col">Email</th>
              <th scope="col"><span class="sr-only">Save state</span></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="setting in settings.data.value || []"
              :key="setting.topic"
            >
              <th scope="row">{{ label(setting.topic) }}</th>
              <td
                v-for="field in ['inAppEnabled', 'emailEnabled'] as const"
                :key="field"
              >
                <label
                  class="row"
                  style="min-height: 44px; width: fit-content; gap: 0.5rem"
                >
                  <input
                    type="checkbox"
                    :checked="setting[field]"
                    :disabled="
                      !editable || pendingTopics.includes(setting.topic)
                    "
                    :aria-label="
                      label(setting.topic) +
                      ' · ' +
                      (field === 'inAppEnabled'
                        ? 'In-app notifications'
                        : 'Email notifications')
                    "
                    @change="
                      setChannel(
                        setting,
                        field,
                        ($event.target as HTMLInputElement).checked,
                      )
                    "
                  />
                  <span>{{ setting[field] ? "On" : "Off" }}</span>
                </label>
              </td>
              <td>
                <span
                  v-if="pendingTopics.includes(setting.topic)"
                  class="muted small"
                  role="status"
                  >Saving…</span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ResourceState>
  </section>
  <section class="report-section">
    <h2>Notification language</h2>
    <ResourceState
      :loading="language.loading.value"
      :error="language.error.value"
      @retry="language.refresh()"
    >
      <label class="field" style="max-width: 20rem"
        >Email language
        <select
          :value="language.data.value?.language"
          :disabled="!editable || languageBusy"
          @change="setLanguage(($event.target as HTMLSelectElement).value)"
        >
          <option value="fr">Français</option>
          <option value="ar">العربية</option>
        </select>
      </label>
      <span v-if="languageBusy" role="status" class="muted small">Saving…</span>
    </ResourceState>
  </section>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p>
</template>
