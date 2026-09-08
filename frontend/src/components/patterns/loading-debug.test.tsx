import { afterEach, beforeEach, describe, expect, spyOn, test } from "bun:test";
import { Window } from "happy-dom";
import { act, type ReactNode, StrictMode } from "react";
import type { Root } from "react-dom/client";

const browser = new Window({ url: "http://localhost:3000/admin?debugLoading=1" });
for (const key of ["window", "document", "navigator", "HTMLElement", "Element", "Node", "MutationObserver"] as const) {
  Object.defineProperty(globalThis, key, { configurable: true, value: key === "window" ? browser : browser[key] });
}

const { createRoot } = await import("react-dom/client");
const { LoadingDebugPanel } = await import("./loading-debug");
const { LoadingState } = await import("./remote-state");
const originalEnvironment = process.env.NODE_ENV;
const originalActEnvironment = Object.getOwnPropertyDescriptor(globalThis, "IS_REACT_ACT_ENVIRONMENT");
let clock = 1_000;
let time: ReturnType<typeof spyOn>;
let log: ReturnType<typeof spyOn>;
let root: Root;
let container: HTMLDivElement;

// Use a local React root: importing Testing Library here would bind its global `screen` to
// this document before the existing product-shell suite installs its own browser document.
function render(content: ReactNode) {
  act(() => root.render(content));
}

function matches(text: string) {
  return [...container.querySelectorAll("dt, dd, p, span")].filter((node) => node.textContent === text);
}

function Harness({ loading = true, label = "Profil de test" }: { loading?: boolean; label?: string }) {
  return (
    <StrictMode>
      {loading ? <LoadingState debugLabel={label} /> : <p>Tableau prêt</p>}
      <LoadingDebugPanel />
    </StrictMode>
  );
}

beforeEach(() => {
  Object.defineProperty(globalThis, "IS_REACT_ACT_ENVIRONMENT", { configurable: true, writable: true, value: true });
  process.env.NODE_ENV = "test";
  browser.history.replaceState(null, "", "/admin?debugLoading=1");
  clock = 1_000;
  time = spyOn(performance, "now").mockImplementation(() => clock);
  log = spyOn(console, "info").mockImplementation(() => undefined);
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  if (originalActEnvironment) Object.defineProperty(globalThis, "IS_REACT_ACT_ENVIRONMENT", originalActEnvironment);
  else Reflect.deleteProperty(globalThis, "IS_REACT_ACT_ENVIRONMENT");
  time.mockRestore();
  log.mockRestore();
  if (originalEnvironment === undefined) delete process.env.NODE_ENV;
  else process.env.NODE_ENV = originalEnvironment;
});

describe("loading skeleton diagnostics", () => {
  test("ticks while mounted and retains the final duration without StrictMode duplicate entries", async () => {
    render(<Harness />);
    expect(matches("Profil de test")).toHaveLength(1);
    expect(matches("en cours")).toHaveLength(1);
    expect(log).not.toHaveBeenCalled();

    clock = 3_500;
    await act(async () => new Promise((resolve) => setTimeout(resolve, 120)));
    expect(matches("2.50 sen cours")).toHaveLength(1);
    await act(async () => root.render(<Harness loading={false} />));

    expect(matches("Tableau prêt")).toHaveLength(1);
    expect(matches("Profil de test")).toHaveLength(1);
    expect(matches("2.50 s")).toHaveLength(1);
    expect(matches("en cours")).toHaveLength(0);
    expect(log).toHaveBeenCalledTimes(1);
    expect(log).toHaveBeenCalledWith("[HiveApp loading] Profil de test: 2500 ms (skeleton mounted)");

    clock = 9_000;
    render(<Harness loading={false} />);
    expect(matches("2.50 s")).toHaveLength(1);
    expect(container.querySelector("aside")?.getAttribute("aria-live")).toBe("off");
  });

  test("records a later loading stage separately", async () => {
    render(<Harness label="Premier chargement" />);
    clock = 2_000;
    await act(async () => root.render(<Harness loading={false} />));
    render(<Harness label="Deuxième chargement" />);
    clock = 2_400;
    await act(async () => root.render(<Harness loading={false} />));

    expect(matches("Premier chargement")).toHaveLength(1);
    expect(matches("Deuxième chargement")).toHaveLength(1);
    expect(log).toHaveBeenCalledTimes(2);
    expect(log).toHaveBeenLastCalledWith("[HiveApp loading] Deuxième chargement: 400 ms (skeleton mounted)");
  });

  test("is opt-in and leaves the usual loading status unchanged", async () => {
    browser.history.replaceState(null, "", "/admin");
    render(<Harness label="Disabled trace" />);
    expect(container.querySelector('[role="status"]')?.getAttribute("aria-label")).toBe("Chargement");
    expect(container.querySelector("aside")).toBeNull();
    await act(async () => root.render(null));
    expect(log).not.toHaveBeenCalled();
  });

  test("cannot be enabled in production, even with the query parameter", async () => {
    process.env.NODE_ENV = "production";
    render(<Harness label="Production trace" />);
    expect(container.querySelector("aside")).toBeNull();
    await act(async () => root.render(null));
    expect(log).not.toHaveBeenCalled();
  });
});
