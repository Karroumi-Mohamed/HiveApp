import { computed, readonly, ref, watch } from "vue";
import { session } from "./session";

export type Appearance = "light" | "dark" | "system";
const preference = ref<Appearance>("system");
const systemIsDark = ref(false);
export const appearance = readonly(preference);
export const resolvedAppearance = computed(() =>
  preference.value === "system"
    ? systemIsDark.value
      ? "dark"
      : "light"
    : preference.value,
);
const key = (operatorId: string) => `hive-v2-appearance:${operatorId}`;
let initialized = false;
const valid = (value: unknown): value is Appearance =>
  value === "light" || value === "dark" || value === "system";

function load() {
  let stored: string | null = null;
  try {
    if (session.me) stored = localStorage.getItem(key(session.me.id));
  } catch {
    // A browser that disables storage can still use the appearance control.
  }
  preference.value = valid(stored) ? stored : "system";
}

export function setAppearance(value: Appearance): boolean {
  if (!valid(value)) return false;
  preference.value = value;
  try {
    if (session.me) localStorage.setItem(key(session.me.id), value);
    return true;
  } catch {
    return false;
  }
}

export function initializeAppearance() {
  if (initialized) return;
  initialized = true;
  const system = window.matchMedia("(prefers-color-scheme: dark)");
  systemIsDark.value = system.matches;
  system.addEventListener(
    "change",
    (event) => (systemIsDark.value = event.matches),
  );
  watch(() => session.me?.id, load, { immediate: true, flush: "sync" });
  watch(
    resolvedAppearance,
    (value) => {
      document.documentElement.dataset.theme = value;
      document.documentElement.style.colorScheme = value;
    },
    { immediate: true, flush: "sync" },
  );
  window.addEventListener("storage", (event) => {
    if (session.me && (event.key === key(session.me.id) || event.key === null))
      load();
  });
}
