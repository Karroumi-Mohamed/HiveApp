import { useEffect, useRef, useState, useSyncExternalStore } from "react";

type LoadingSpan = { id: number; label: string; startedAt: number; endedAt: number | null };
const emptySpans: LoadingSpan[] = [];
let spans = emptySpans;
let nextId = 0;
const listeners = new Set<() => void>();

function enabled() {
  return (
    process.env.NODE_ENV !== "production" &&
    typeof window !== "undefined" &&
    new URLSearchParams(window.location.search).get("debugLoading") === "1"
  );
}

function publish(next: LoadingSpan[]) {
  // Keep all running spans and only the six most recent completed ones. No persistent storage.
  const completed = next.filter((span) => span.endedAt !== null).slice(-6);
  spans = next.filter((span) => span.endedAt === null || completed.includes(span));
  for (const listener of listeners) listener();
}

function begin(label: string) {
  const span: LoadingSpan = { id: ++nextId, label, startedAt: performance.now(), endedAt: null };
  publish([...spans, span]);
  return (endedAt: number) => {
    publish(spans.map((entry) => (entry.id === span.id ? { ...entry, endedAt } : entry)));
    console.info(`[HiveApp loading] ${label}: ${(endedAt - span.startedAt).toFixed(0)} ms (skeleton mounted)`);
  };
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function LoadingMarker({ label }: { label: string }) {
  const finish = useRef<ReturnType<typeof begin> | null>(null);
  const generation = useRef(0);
  useEffect(() => {
    finish.current ??= begin(label);
    const current = ++generation.current;
    return () => {
      const endedAt = performance.now();
      // StrictMode rehearses effect cleanup/setup without removing the skeleton. Do not count
      // that as a completed loading stage; finalize only if the marker really stays unmounted.
      queueMicrotask(() => {
        if (generation.current === current) finish.current?.(endedAt);
      });
    };
  }, [label]);
  return null;
}

export function LoadingDebugMarker({ label }: { label: string }) {
  return enabled() ? <LoadingMarker key={label} label={label} /> : null;
}

function LoadingPanel() {
  const entries = useSyncExternalStore(
    subscribe,
    () => spans,
    () => emptySpans,
  );
  const [now, setNow] = useState(() => performance.now());
  const [hidden, setHidden] = useState(false);
  const running = entries.some((span) => span.endedAt === null);
  useEffect(() => {
    if (!running || hidden) return;
    // Only this small panel rerenders; the skeleton, page and queries do not tick with it.
    const timer = window.setInterval(() => setNow(performance.now()), 100);
    return () => window.clearInterval(timer);
  }, [running, hidden]);

  if (hidden || entries.length === 0) return null;
  return (
    <aside
      aria-label="Diagnostic de chargement"
      aria-live="off"
      className="fixed start-4 bottom-4 z-[70] w-80 max-w-[calc(100vw-2rem)] rounded-lg border bg-popover p-3 text-xs text-popover-foreground shadow-md"
    >
      <div className="mb-2 flex items-center justify-between gap-3">
        <strong>Temps du squelette · DEV</strong>
        <button
          className="rounded px-1 text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
          onClick={() => setHidden(true)}
          type="button"
        >
          Masquer
        </button>
      </div>
      <dl className="space-y-2">
        {entries.map((span) => (
          <div className="flex items-baseline justify-between gap-3" key={span.id}>
            <dt className="min-w-0">{span.label}</dt>
            <dd className="shrink-0 text-end tabular-nums">
              {(Math.max(0, (span.endedAt ?? now) - span.startedAt) / 1000).toFixed(2)} s
              {span.endedAt === null ? <span className="ms-1 text-muted-foreground">en cours</span> : null}
            </dd>
          </div>
        ))}
      </dl>
    </aside>
  );
}

/** Opt-in for this page load with ?debugLoading=1. Excluded from production behavior. */
export function LoadingDebugPanel() {
  return enabled() ? <LoadingPanel /> : null;
}
