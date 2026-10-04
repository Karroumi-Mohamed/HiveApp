import {
  ref,
  watch,
  onMounted,
  onUnmounted,
  toValue,
  type WatchSource,
} from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
export function useDraftGuard(source: WatchSource<unknown>) {
  const dirty = ref(false),
    open = ref(false);
  let resolve: ((allow: boolean) => void) | undefined;
  watch(
    () => JSON.stringify(toValue(source)),
    () => (dirty.value = true),
  );
  function guard() {
    if (!dirty.value) return true;
    open.value = true;
    return new Promise<boolean>((answer) => (resolve = answer));
  }
  function answer(allow: boolean) {
    open.value = false;
    if (allow) dirty.value = false;
    resolve?.(allow);
    resolve = undefined;
  }
  function beforeUnload(e: BeforeUnloadEvent) {
    if (dirty.value) {
      e.preventDefault();
      e.returnValue = "";
    }
  }
  onBeforeRouteLeave(guard);
  onBeforeRouteUpdate(guard);
  onMounted(() => window.addEventListener("beforeunload", beforeUnload));
  onUnmounted(() => {
    window.removeEventListener("beforeunload", beforeUnload);
    resolve?.(false);
  });
  return { open, answer, saved: () => (dirty.value = false) };
}
