import { onScopeDispose, ref, shallowRef, watch, type WatchSource } from "vue";
import { revision } from "@/data/session";
import { errorMessage } from "@/lib/format";

// Each loader owns its request generation. A slow previous route/filter cannot replace newer data.
export function useResource<T>(
  loader: () => Promise<T>,
  sources: WatchSource[] = [],
  pollMs = 0,
  shouldPoll: () => boolean = () => true,
) {
  const data = shallowRef<T>();
  const loading = ref(true);
  const error = ref("");
  let generation = 0;
  let disposed = false;
  let timer: ReturnType<typeof setTimeout>;
  async function refresh(quiet = false) {
    const current = ++generation;
    if (!quiet) loading.value = true;
    error.value = "";
    try {
      const result = await loader();
      if (!disposed && current === generation) data.value = result;
    } catch (e) {
      if (!disposed && current === generation) error.value = errorMessage(e);
    } finally {
      if (!disposed && current === generation) loading.value = false;
    }
  }
  watch(
    [revision, ...sources],
    () => {
      data.value = undefined;
      void refresh();
    },
    { immediate: true },
  );
  async function poll() {
    if (disposed) return;
    if (!document.hidden && !loading.value && shouldPoll()) await refresh(true);
    if (!disposed) timer = setTimeout(poll, pollMs);
  }
  if (pollMs) timer = setTimeout(poll, pollMs);
  onScopeDispose(() => {
    disposed = true;
    generation++;
    clearTimeout(timer);
  });
  return { data, loading, error, refresh };
}
