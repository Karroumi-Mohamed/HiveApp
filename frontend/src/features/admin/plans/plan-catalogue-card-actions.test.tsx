import { afterEach, expect, test } from "bun:test";
import { Window } from "happy-dom";
import type { PlanOperationalItem } from "@/api/contracts";

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
  "PointerEvent",
  "MutationObserver",
  "ResizeObserver",
  "getComputedStyle",
] as const) {
  Object.defineProperty(globalThis, key, { configurable: true, value: key === "window" ? browser : browser[key] });
}

const { cleanup, render } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { MemoryRouter, useLocation } = await import("react-router");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { PlanCatalogueCard } = await import("./plan-catalogue-cards");

const plan = {
  id: "plan-one",
  name: "Enterprise",
  status: "ACTIVE",
  includedFeatureCount: 7,
  currentSubscriberCount: 0,
  blockers: [],
  availableActions: ["REVISE"],
} as unknown as PlanOperationalItem;

function Location() {
  const location = useLocation();
  return <output aria-label="Page actuelle">{location.pathname + location.search}</output>;
}

function mount(allowed: boolean, item = plan) {
  return render(
    <MemoryRouter initialEntries={["/admin/plans"]}>
      <TooltipProvider delayDuration={0}>
        <PlanCatalogueCard
          plan={item}
          pricing={<p>99,99 USD / mois</p>}
          canOpen={allowed}
          canDuplicate={allowed}
          canRevise={allowed}
        />
        <Location />
      </TooltipProvider>
    </MemoryRouter>,
  );
}

afterEach(() => cleanup());

test("labelled catalogue actions preserve their navigation destinations", async () => {
  const view = mount(true);
  const user = userEvent.setup({ document: view.container.ownerDocument });
  const duplicate = view.getByRole("link", { name: "Dupliquer le forfait Enterprise" });
  const revise = view.getByRole("link", { name: "Réviser le forfait Enterprise" });
  const open = view.getByRole("link", { name: "Ouvrir le forfait Enterprise" });
  expect(duplicate.textContent).toBe("Dupliquer");
  expect(revise.textContent).toBe("Réviser");
  expect(open.textContent).toBe("Ouvrir");
  await user.click(duplicate);
  expect(view.getByLabelText("Page actuelle").textContent).toBe("/admin/plans/new?from=plan-one");
  await user.click(revise);
  expect(view.getByLabelText("Page actuelle").textContent).toBe("/admin/plans/plan-one");
  await user.click(open);
  expect(view.getByLabelText("Page actuelle").textContent).toBe("/admin/plans/plan-one");
});

test("unavailable catalogue actions explain themselves on keyboard focus and cannot navigate", async () => {
  const view = mount(false);
  const user = userEvent.setup({ document: view.container.ownerDocument });
  expect(view.queryByRole("link")).toBeNull();
  for (const [label, reason] of [
    ["Dupliquer le forfait Enterprise", "Duplication non autorisée"],
    ["Réviser le forfait Enterprise", "Révision non autorisée"],
    ["Ouvrir le forfait Enterprise", "Consultation non autorisée"],
  ] as const) {
    await user.tab();
    const action = view.getByRole("button", { name: label });
    expect(view.container.ownerDocument.activeElement).toBe(action);
    expect(action.getAttribute("aria-disabled")).toBe("true");
    expect((await view.findByRole("tooltip")).textContent).toBe(reason);
    await user.keyboard("{Enter}");
    expect(view.getByLabelText("Page actuelle").textContent).toBe("/admin/plans");
  }
});

test("draft revision keeps its state-specific explanation with a visible label", async () => {
  const view = mount(true, { ...plan, status: "DRAFT", availableActions: [] });
  const user = userEvent.setup({ document: view.container.ownerDocument });
  await user.tab();
  await user.tab();
  expect(view.container.ownerDocument.activeElement).toBe(
    view.getByRole("button", { name: "Réviser le forfait Enterprise" }),
  );
  expect((await view.findByRole("tooltip")).textContent).toBe("La révision n’est pas disponible dans cet état");
  await user.keyboard("{Enter}");
  expect(view.getByLabelText("Page actuelle").textContent).toBe("/admin/plans");
});
