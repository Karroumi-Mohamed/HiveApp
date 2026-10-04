<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "@/components/Icon.vue";
import AppDialog from "@/components/AppDialog.vue";
import { session, toast, can, signOut } from "@/data/session";
import { adminPermissions as p } from "@/auth/permissions";
const route = useRoute(),
  router = useRouter();
const navigation = [
  {
    path: "/overview",
    title: "Overview",
    icon: "overview",
    permissions: [
      p.plansOverview,
      p.analyticsReadSummary,
      p.analyticsReadFinancialSeries,
      p.analyticsReadSubscriptionSeries,
      p.analyticsReadOfferSeries,
    ],
  },
  {
    path: "/customers",
    title: "Customers",
    icon: "customers",
    permissions: [p.subscriptionsSearch],
  },
  {
    path: "/catalog",
    title: "Catalog",
    icon: "catalog",
    permissions: [
      p.plansList,
      p.plansListFamilies,
      p.addOnsList,
      p.quotaPackagesList,
      p.priceBooksList,
    ],
  },
  {
    path: "/commercial",
    title: "Commercial",
    icon: "commercial",
    permissions: [
      p.campaignsList,
      p.segmentsList,
      p.commercialPoliciesList,
      p.offersList,
    ],
  },
  {
    path: "/billing",
    title: "Billing",
    icon: "billing",
    permissions: [
      p.billingListInvoices,
      p.subscriptionsSearchSpecialAgreements,
      p.billingListReconciliation,
      p.billingListProviderEvents,
    ],
  },
  {
    path: "/operations",
    title: "Operations",
    icon: "operations",
    permissions: [
      p.subscriptionsListChangeJobs,
      p.activitiesRead,
      p.observabilityReadHealth,
      p.observabilityReadBacklogs,
      p.observabilityReadLogAccess,
      p.registrySync,
      p.communicationsRead,
      p.repricingList,
      p.plansListApplications,
      p.analyticsReadOperations,
      p.customerCommunicationsRead,
      p.notificationsRead,
      p.notificationsDelivery,
    ],
  },
] as const;
const visibleNavigation = computed(() =>
  navigation.filter((n) => can(...n.permissions)),
);
const area = computed(
  () =>
    navigation.find((n) => route.path.startsWith(n.path))?.title ||
    String(route.meta.title || "Settings"),
);
const publicPage = computed(() => route.meta.public || !session.token);
const searchOpen = ref(false),
  search = ref(""),
  mobileOpen = ref(false),
  sidebar = ref<HTMLElement>(),
  menuButton = ref<HTMLButtonElement>();
const mobileQuery = window.matchMedia("(max-width: 700px)"),
  isMobile = ref(mobileQuery.matches);
const searchLinks = computed(() =>
  visibleNavigation.value.filter((n) =>
    n.title.toLowerCase().includes(search.value.toLowerCase()),
  ),
);
function resizeNavigation() {
  isMobile.value = mobileQuery.matches;
  if (!isMobile.value) mobileOpen.value = false;
}
watch(mobileOpen, async (open) => {
  document.body.style.overflow = open ? "hidden" : "";
  await nextTick();
  if (open) sidebar.value?.querySelector<HTMLElement>("a,button")?.focus();
  else if (isMobile.value) menuButton.value?.focus();
});
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false;
    searchOpen.value = false;
  },
);
function navigate(path: string) {
  searchOpen.value = false;
  void router.push(path);
}
function keyHandler(event: KeyboardEvent) {
  if (
    (event.metaKey || event.ctrlKey) &&
    event.key === "k" &&
    !publicPage.value
  ) {
    event.preventDefault();
    mobileOpen.value = false;
    searchOpen.value = !searchOpen.value;
  }
  if (mobileOpen.value && event.key === "Escape") mobileOpen.value = false;
  if (mobileOpen.value && event.key === "Tab") {
    const controls = sidebar.value?.querySelectorAll<HTMLElement>(
      "a[href],button:not([disabled])",
    );
    const first = controls?.[0],
      last = controls?.[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }
}
function expired() {
  void router.push({ path: "/login", query: { returnTo: route.fullPath } });
}
async function logout() {
  await signOut();
  await router.push("/login");
}
onMounted(() => {
  window.addEventListener("keydown", keyHandler);
  window.addEventListener("hive-session-expired", expired);
  mobileQuery.addEventListener("change", resizeNavigation);
});
onUnmounted(() => {
  window.removeEventListener("keydown", keyHandler);
  window.removeEventListener("hive-session-expired", expired);
  mobileQuery.removeEventListener("change", resizeNavigation);
  document.body.style.overflow = "";
});
</script>
<template>
  <RouterView v-if="publicPage" />
  <template v-else>
    <a class="skip-link" href="#main-content">Skip to content</a>
    <div class="app-shell">
      <button
        v-if="mobileOpen"
        class="sidebar-scrim"
        aria-label="Close navigation"
        @click="mobileOpen = false"
      />
      <aside
        ref="sidebar"
        class="sidebar"
        :class="{ 'mobile-open': mobileOpen }"
        :role="mobileOpen ? 'dialog' : undefined"
        :aria-modal="mobileOpen || undefined"
        :aria-label="mobileOpen ? 'Navigation' : undefined"
      >
        <RouterLink class="brand" to="/overview" aria-label="Hive overview"
          ><img src="/hive.svg" alt="" /><span
            >hive<span class="brand-period">.</span></span
          ></RouterLink
        >
        <button
          v-if="isMobile"
          class="icon-button mobile-close"
          aria-label="Close navigation drawer"
          @click="mobileOpen = false"
        >
          <Icon name="close" />
        </button>
        <button
          class="sidebar-search"
          @click="
            mobileOpen = false;
            searchOpen = true;
          "
        >
          <Icon name="search" :size="16" /><span>Search</span><kbd>⌘ K</kbd>
        </button>
        <nav class="primary-nav" aria-label="Primary navigation">
          <RouterLink
            v-for="item in visibleNavigation"
            :key="item.path"
            :to="item.path"
            :class="{ active: route.path.startsWith(item.path) }"
            :aria-current="
              route.path.startsWith(item.path) ? 'page' : undefined
            "
            ><Icon :name="item.icon" /><span>{{ item.title }}</span></RouterLink
          >
        </nav>
        <div class="sidebar-bottom">
          <RouterLink
            class="nav-secondary"
            to="/settings"
            :class="{ active: route.path.startsWith('/settings') }"
            ><Icon name="settings" />Settings</RouterLink
          ><RouterLink class="user-profile" to="/settings/profile"
            ><span class="avatar user-avatar">{{
              session.me?.email.slice(0, 2).toUpperCase()
            }}</span>
            <div>
              <strong>{{ session.me?.email }}</strong
              ><small>{{
                session.me?.isSuperAdmin ? "Administrator" : "Operator"
              }}</small>
            </div></RouterLink
          ><button class="nav-secondary" @click="logout">
            <Icon name="logout" />Sign out
          </button>
        </div>
      </aside>
      <div class="main-shell" :inert="mobileOpen || undefined">
        <header class="topbar">
          <div class="topbar-left">
            <button
              ref="menuButton"
              class="icon-button mobile-menu"
              aria-label="Open navigation"
              :aria-expanded="mobileOpen"
              @click="mobileOpen = true"
            >
              <Icon name="menu" /></button
            ><strong>{{ area }}</strong>
          </div>
          <div class="topbar-actions">
            <RouterLink class="topbar-link" to="/operations?view=health"
              >System status</RouterLink
            ><button
              class="icon-button"
              aria-label="Search"
              @click="searchOpen = true"
            >
              <Icon name="search" /></button
            ><RouterLink
              class="icon-button"
              to="/operations?view=inbox"
              aria-label="Notifications"
              ><Icon name="bell"
            /></RouterLink>
          </div>
        </header>
        <main id="main-content" class="main-content" tabindex="-1">
          <RouterView />
        </main>
      </div>
    </div>
    <AppDialog :open="searchOpen" title="Search" @close="searchOpen = false"
      ><form
        @submit.prevent="navigate('/customers?q=' + encodeURIComponent(search))"
      >
        <label class="search-field command-search"
          ><Icon name="search" /><input
            v-model="search"
            autofocus
            aria-label="Search customers or pages"
            placeholder="Customers or pages"
        /></label>
      </form>
      <div class="command-results">
        <button
          v-for="item in searchLinks"
          :key="item.path"
          class="command-item"
          @click="navigate(item.path)"
        >
          <Icon :name="item.icon" />{{ item.title }}</button
        ><button
          v-if="search && can(p.subscriptionsSearch)"
          class="command-item"
          @click="navigate('/customers?q=' + encodeURIComponent(search))"
        >
          Search customers for “{{ search }}”
        </button>
      </div></AppDialog
    >
  </template>
  <div
    v-if="toast.message"
    class="toast"
    :class="toast.tone"
    :role="toast.tone === 'error' ? 'alert' : 'status'"
  >
    <Icon :name="toast.tone === 'success' ? 'success' : 'alert'" /><span>{{
      toast.message
    }}</span
    ><button
      class="icon-button"
      aria-label="Dismiss notification"
      @click="toast.message = ''"
    >
      <Icon name="close" />
    </button>
  </div>
</template>
