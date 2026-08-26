import { afterEach, describe, expect, test } from "bun:test";
import { Window } from "happy-dom";

const browser = new Window({ url: "http://localhost:3000/admin/plans" });
for (const key of [
  "window",
  "document",
  "navigator",
  "HTMLElement",
  "Element",
  "Node",
  "Event",
  "CustomEvent",
  "MouseEvent",
  "MutationObserver",
  "getComputedStyle",
] as const) {
  const value = key === "window" ? browser : browser[key];
  Object.defineProperty(globalThis, key, { configurable: true, value });
}

const { cleanup, render, waitFor } = await import("@testing-library/react");
const { QueryClient, QueryClientProvider, useQuery } = await import("@tanstack/react-query");
const { commercialQueryEnabled } = await import("@/features/commercial/commercial-query");
const { meetsPermissionRequirement, ReadPermissionGate } = await import("./permission-gate");

function PermissionBoundQuery({ allowed, queryFn }: { allowed: boolean; queryFn: () => string }) {
  useQuery({
    queryKey: ["permission-bound-query"],
    queryFn,
    enabled: commercialQueryEnabled((permission) => allowed && permission === "catalog", "catalog"),
  });
  return null;
}

afterEach(() => cleanup());

describe("read permission gate", () => {
  test("does not mount protected children while loading", () => {
    const view = render(
      <ReadPermissionGate allowed loading>
        <p>Contenu protégé</p>
      </ReadPermissionGate>,
    );

    expect(view.getByRole("status").getAttribute("aria-label")).toBe("Chargement");
    expect(view.queryByText("Contenu protégé")).toBeNull();
  });

  test("renders an accessible denied state without mounting protected children", () => {
    const view = render(
      <ReadPermissionGate allowed={false}>
        <p>Contenu protégé</p>
      </ReadPermissionGate>,
    );

    expect(view.getByRole("status")).toBeTruthy();
    expect(view.getByText("Accès indisponible")).toBeTruthy();
    expect(view.queryByText("Contenu protégé")).toBeNull();
  });

  test("mounts protected children only when access is granted", () => {
    const view = render(
      <ReadPermissionGate allowed>
        <p>Contenu protégé</p>
      </ReadPermissionGate>,
    );

    expect(view.getByText("Contenu protégé")).toBeTruthy();
    expect(view.queryByText("Accès indisponible")).toBeNull();
  });

  test("supports combined all-of and any-of route requirements", () => {
    const granted = new Set(["read", "history"]);
    const can = (permission: string) => granted.has(permission);

    expect(meetsPermissionRequirement(can, { allOf: ["read"], anyOf: ["catalog", "history"] })).toBeTrue();
    expect(meetsPermissionRequirement(can, { allOf: ["read", "write"], anyOf: ["history"] })).toBeFalse();
    expect(meetsPermissionRequirement(can, { anyOf: ["catalog", "write"] })).toBeFalse();
  });

  test("does not start a permission-bound query until its distinct read permission is granted", async () => {
    let calls = 0;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const queryFn = () => {
      calls += 1;
      return "catalog";
    };
    const view = render(
      <QueryClientProvider client={queryClient}>
        <PermissionBoundQuery allowed={false} queryFn={queryFn} />
      </QueryClientProvider>,
    );

    expect(calls).toBe(0);
    view.rerender(
      <QueryClientProvider client={queryClient}>
        <PermissionBoundQuery allowed queryFn={queryFn} />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(calls).toBe(1));
  });
});
