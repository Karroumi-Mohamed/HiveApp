<script setup lang="ts">
import { computed, reactive } from "vue";
import { useRoute, useRouter } from "vue-router";
import CustomersView from "./CustomersView.vue";
import CustomerWorkspaceTabs from "@/components/CustomerWorkspaceTabs.vue";
import PageHeading from "@/components/PageHeading.vue";
import ResourceList from "@/components/ResourceList.vue";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { withReturnTo } from "@/lib/navigation";
import FieldInput from "@/components/FieldInput.vue";
import { accountField } from "@/resources/fields";
const route = useRoute(),
  router = useRouter();
const customer = reactive({ accountId: "" });
const narrowDirectory = computed(() =>
  can(
    p.subscriptionsChooseAccounts,
    p.subscriptionsChooseChangeOptions,
    p.subscriptionsPreviewChange,
    p.subscriptionsApplyChange,
  ),
);
const available = computed(() =>
  [
    {
      key: "directory",
      permission: can(p.subscriptionsSearch)
        ? p.subscriptionsSearch
        : can(p.subscriptionsChooseAccounts)
          ? p.subscriptionsChooseAccounts
          : can(p.subscriptionsChooseChangeOptions)
            ? p.subscriptionsChooseChangeOptions
            : can(p.subscriptionsPreviewChange)
              ? p.subscriptionsPreviewChange
              : p.subscriptionsApplyChange,
    },
    { key: "agreements", permission: p.subscriptionsSearchSpecialAgreements },
    {
      key: "messages",
      permission: can(p.customerCommunicationsRead)
        ? p.customerCommunicationsRead
        : p.customerCommunicationsCreate,
    },
    { key: "invoices", permission: p.billingListInvoices },
  ].filter((t) => can(t.permission)),
);
const view = computed(() =>
  String(route.query.view || available.value[0]?.key || ""),
);
const allowed = computed(() =>
  available.value.some((t) => t.key === view.value),
);
</script>
<template>
  <CustomersView v-if="view === 'directory' && can(p.subscriptionsSearch)" />
  <template v-else-if="view === 'directory' && narrowDirectory">
    <PageHeading title="Customers" /><CustomerWorkspaceTabs />
    <form
      class="compact-form"
      @submit.prevent="
        router.push(
          withReturnTo('/customers/' + customer.accountId, route.fullPath),
        )
      "
    >
      <FieldInput
        v-if="can(p.subscriptionsChooseAccounts)"
        :field="accountField('accountId', 'Find customer', true)"
        :data="customer"
      />
      <label v-else class="field"
        >Customer ID<input
          v-model="customer.accountId"
          required
          pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
      /></label>
      <div class="form-actions">
        <button class="button primary" :disabled="!customer.accountId">
          Open customer
        </button>
      </div>
    </form>
  </template>
  <template v-else>
    <PageHeading title="Customers">
      <RouterLink
        v-if="
          view === 'messages' &&
          can(p.customerCommunicationsCreate) &&
          !can(p.customerCommunicationsRead)
        "
        class="button primary"
        :to="withReturnTo('/customers/communications/new', route.fullPath)"
        >New message</RouterLink
      >
    </PageHeading>
    <CustomerWorkspaceTabs />
    <p v-if="!allowed" class="notice">You do not have access to this view.</p>
    <ResourceList
      v-else-if="view !== 'messages' || can(p.customerCommunicationsRead)"
      :resource-key="view"
    />
    <p v-else class="notice">
      You can create messages. Viewing existing communications requires
      additional access.
    </p>
  </template>
</template>
