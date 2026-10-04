<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import PageHeading from "@/components/PageHeading.vue";
import ViewTabs from "@/components/ViewTabs.vue";
import ResourceState from "@/components/ResourceState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import ActionDialog from "@/components/ActionDialog.vue";
import Facts from "@/components/Facts.vue";
import { gateway, read } from "@/data/gateway";
import { adminApi } from "@/api/admin-api";
import { useResource } from "@/composables/useResource";
import { money, date, label } from "@/lib/format";
import { can } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
import { invoiceActions, paymentActions } from "@/resources/billing";
import type { Action, RecordData } from "@/resources/types";
import { text } from "@/resources/fields";
const route = useRoute(),
  id = computed(() => String(route.params.id));
const invoice = useResource(
  () =>
    can(p.billingReadInvoice)
      ? gateway.invoice(id.value)
      : Promise.resolve(undefined),
  [id],
);
const data = invoice.data;
const view = computed(() =>
  (route.query.view === "document" || !can(p.billingReadInvoice)) &&
  can(p.billingReadInvoiceDocument)
    ? "document"
    : "details",
);
const document = useResource(
  () =>
    view.value === "document"
      ? read(p.billingReadInvoiceDocument, () =>
          adminApi.billingInvoiceDocument(id.value),
        )
      : Promise.resolve(undefined),
  [id, view],
);
const doc = document.data;
const action = ref<Action>(),
  actionData = ref<RecordData>({});
const settleAction: Action = {
  key: "settle",
  label: "Record manual settlement",
  permission: p.billingManualSettlement,
  fields: [text("reference", "Payment reference", true)],
  execute: (d, i) =>
    gateway.settle(d.invoice.id, { reference: i.reference, reason: i.reason }),
};
const actions = computed(() =>
  data.value
    ? [
        ...(data.value.invoice.status === "OPEN" ? [settleAction] : []),
        ...invoiceActions,
      ].filter(
        (a) => can(a.permission) && (!a.visible || a.visible(data.value!)),
      )
    : [],
);
function open(a: Action, d: RecordData) {
  action.value = a;
  actionData.value = d;
}
function printInvoice() {
  window.print();
}
</script>
<template>
  <RouterLink class="back-link" to="/billing">All invoices</RouterLink>
  <PageHeading
    :title="
      data?.invoice.invoiceNumber || doc?.invoice.invoiceNumber || 'Invoice'
    "
    ><StatusBadge v-if="data" :status="data.invoice.status" />
    <details v-if="actions.length" class="more-actions">
      <summary class="button">Actions</summary>
      <div class="action-menu">
        <button
          v-for="a in actions"
          :key="a.key"
          @click="
            open(a, data!);
            ($event.currentTarget as HTMLElement)
              .closest('details')
              ?.removeAttribute('open');
          "
        >
          {{ a.label }}
        </button>
      </div>
    </details>
    <button
      v-if="view === 'document' && doc"
      class="button"
      @click="printInvoice"
    >
      Print
    </button></PageHeading
  >
  <ViewTabs
    :tabs="[
      ...(can(p.billingReadInvoice)
        ? [{ key: 'details', label: 'Details' }]
        : []),
      ...(can(p.billingReadInvoiceDocument)
        ? [{ key: 'document', label: 'Invoice document' }]
        : []),
    ]"
    :current="view"
  />
  <ResourceState
    v-if="view === 'details'"
    :loading="invoice.loading.value"
    :error="invoice.error.value"
    @retry="invoice.refresh()"
  >
    <template v-if="data">
      <section class="report-section">
        <header class="section-heading">
          <h2>Invoice</h2>
          <RouterLink
            v-if="data.invoice.account"
            class="text-link"
            :to="'/customers/' + data.invoice.account.id + '?view=billing'"
            >{{ data.invoice.account.name }}</RouterLink
          >
        </header>
        <dl class="record-summary">
          <div>
            <dt>Issued</dt>
            <dd>{{ date(data.invoice.issuedAt) }}</dd>
          </div>
          <div>
            <dt>Service period</dt>
            <dd>
              {{ date(data.invoice.periodStart) }} –
              {{ date(data.invoice.periodEnd) }}
            </dd>
          </div>
          <div>
            <dt>Billing cycle</dt>
            <dd>{{ label(data.invoice.billingCycle) }}</dd>
          </div>
          <div>
            <dt>Total</dt>
            <dd>
              {{ money(data.invoice.totalAmount, data.invoice.currencyCode) }}
            </dd>
          </div>
          <div>
            <dt>Settled</dt>
            <dd>{{ date(data.invoice.settledAt, true) }}</dd>
          </div>
          <div>
            <dt>Credits</dt>
            <dd>{{ money(data.creditedAmount, data.invoice.currencyCode) }}</dd>
          </div>
          <div>
            <dt>Refunds</dt>
            <dd>{{ money(data.refundedAmount, data.invoice.currencyCode) }}</dd>
          </div>
        </dl>
        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Description</th>
                <th>Quantity</th>
                <th class="numeric">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in data.lines" :key="line.id">
                <td>
                  {{ line.sourceName
                  }}<small class="muted"> · {{ label(line.type) }}</small>
                </td>
                <td>{{ line.quantity }}</td>
                <td class="numeric">
                  {{ money(line.lineAmount, line.currencyCode) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section v-if="can(p.billingReadPayments)" class="report-section">
        <h2>Payments</h2>
        <ResourceState :empty="!data.payments.length" title="No payments"
          ><div class="table-scroll">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Kind</th>
                  <th>Status</th>
                  <th>Amount</th>
                  <th>Settlement evidence</th>
                  <th>Completed</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="payment in data.payments" :key="payment.id">
                  <td>{{ label(payment.kind) }}</td>
                  <td><StatusBadge :status="payment.status" /></td>
                  <td>{{ money(payment.amount, payment.currencyCode) }}</td>
                  <td>
                    {{
                      payment.trustedForSettlement ? "Trusted" : "Unconfirmed"
                    }}
                  </td>
                  <td>{{ date(payment.completedAt, true) }}</td>
                  <td>
                    <button
                      v-for="a in paymentActions.filter((a) =>
                        can(a.permission),
                      )"
                      :key="a.key"
                      class="text-link"
                      @click="open(a, payment)"
                    >
                      {{ a.label }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div></ResourceState
        >
      </section>
      <section v-if="data.credits.length" class="report-section">
        <h2>Credits</h2>
        <Facts :data="data.credits" />
      </section>
      <section v-if="data.refunds.length" class="report-section">
        <h2>Refunds</h2>
        <Facts :data="data.refunds" />
      </section> </template
  ></ResourceState>
  <ResourceState
    v-else
    :loading="document.loading.value"
    :error="document.error.value"
    @retry="document.refresh()"
    ><article v-if="doc" class="invoice-document">
      <header class="section-heading">
        <h2>Invoice {{ doc.invoice.invoiceNumber }}</h2>
        <span>{{ date(doc.invoice.issuedAt) }}</span>
      </header>
      <p v-if="!doc.fiscalReady" class="notice warning">
        Missing fiscal details:
        {{ doc.missingFiscalFields.map(label).join(", ") }}
      </p>
      <div class="document-parties">
        <section
          v-for="party in [
            { label: 'Seller', data: doc.seller },
            { label: 'Customer', data: doc.customer },
          ]"
          :key="party.label"
        >
          <h3>{{ party.label }}</h3>
          <p>
            <strong>{{ party.data.name }}</strong>
          </p>
          <p>{{ party.data.address }}</p>
          <p>{{ party.data.countryCode }}</p>
          <p v-if="party.data.taxId">Tax ID: {{ party.data.taxId }}</p>
          <p>{{ party.data.billingEmail }}</p>
        </section>
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th>Description</th>
            <th>Quantity</th>
            <th class="numeric">Amount</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in doc.lines" :key="line.id">
            <td>{{ line.sourceName }}</td>
            <td>{{ line.quantity }}</td>
            <td class="numeric">
              {{ money(line.lineAmount, line.currencyCode) }}
            </td>
          </tr>
        </tbody>
      </table>
      <div class="invoice-total">
        <span>Total</span
        ><strong>{{
          money(doc.invoice.totalAmount, doc.invoice.currencyCode)
        }}</strong>
      </div>
    </article></ResourceState
  >
  <ActionDialog
    :action="action"
    :data="actionData"
    @close="action = undefined"
    @done="
      invoice.refresh();
      document.refresh();
    "
  />
</template>
<style scoped>
.document-parties {
  display: flex;
  gap: 80px;
  margin: 28px 0;
}
.document-parties p {
  margin: 5px 0;
}
.invoice-document {
  max-width: 960px;
}
.data-table td:last-child .text-link {
  margin-right: 14px;
}
@media (max-width: 650px) {
  .document-parties {
    gap: 25px;
    flex-wrap: wrap;
  }
}
</style>
