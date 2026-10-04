<script setup lang="ts">
import { computed, ref } from "vue";
import AppDialog from "./AppDialog.vue";
import ResourceState from "./ResourceState.vue";
import Pagination from "./Pagination.vue";
import { adminApi } from "@/api/admin-api";
import { adminPermissions as p } from "@/auth/permissions";
import { can } from "@/data/session";
import { read } from "@/data/gateway";
import { errorMessage } from "@/lib/format";
import type { RecordData } from "@/resources/types";
const props = defineProps<{ planId?: string }>();
const permission = computed(() =>
  props.planId
    ? p.plansLookupSubscriberOwnerEmail
    : p.subscriptionsLookupAccountOwnerEmail,
);
const open = ref(false),
  email = ref(""),
  page = ref(0),
  busy = ref(false),
  error = ref(""),
  data = ref<RecordData>();
async function search(nextPage = 0) {
  page.value = nextPage;
  busy.value = true;
  error.value = "";
  try {
    const result = await read<any>(permission.value, () =>
      props.planId
        ? adminApi.planSubscribersByOwnerEmail(props.planId, {
            ownerEmail: email.value,
            page: nextPage,
            size: 20,
          })
        : adminApi.subscriptionAccountsByOwnerEmail({
            ownerEmail: email.value,
            page: nextPage,
            size: 20,
          }),
    );
    data.value = {
      ...result,
      content: result.content.map((r: any) => r.account || r.subscriber),
    };
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <button v-if="can(permission)" class="button" @click="open = true">
    Find by owner email</button
  ><AppDialog :open="open" title="Find customer" @close="open = false"
    ><form @submit.prevent="search()">
      <label class="field"
        >Owner email<input v-model="email" type="email" required
      /></label>
      <div class="dialog-actions">
        <button class="button primary" :disabled="busy">Find</button>
      </div>
    </form>
    <ResourceState
      v-if="data || error || busy"
      :loading="busy"
      :error="error"
      :empty="!data?.content.length"
      title="No customers found"
      @retry="search(page)"
      ><div class="record-links">
        <RouterLink
          v-for="r in data?.content"
          :key="r.id || r.accountId"
          class="text-link"
          :to="'/customers/' + (r.accountId || r.id)"
          >{{ r.accountName || r.name }}</RouterLink
        >
      </div>
      <Pagination
        v-if="data"
        :page="data.page"
        :size="data.size"
        :total="data.totalElements"
        @change="search($event)" /></ResourceState
  ></AppDialog>
</template>
