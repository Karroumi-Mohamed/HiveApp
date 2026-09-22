import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import type { Icon } from "@phosphor-icons/react";
import { Window } from "happy-dom";
import type { SVGProps } from "react";

const browser = new Window({ url: "http://localhost:3000/admin/plans" });
const browserGlobals = [
  "window",
  "document",
  "navigator",
  "localStorage",
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
] as const;

for (const key of browserGlobals) {
  const value = key === "window" ? browser : browser[key];
  Object.defineProperty(globalThis, key, { configurable: true, value });
}
Object.defineProperty(globalThis, "requestAnimationFrame", {
  configurable: true,
  value: (callback: FrameRequestCallback) => setTimeout(() => callback(Date.now()), 0),
});
Object.defineProperty(globalThis, "cancelAnimationFrame", {
  configurable: true,
  value: (id: number) => clearTimeout(id),
});

const { cleanup, render } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { I18nextProvider } = await import("react-i18next");
const { MemoryRouter } = await import("react-router");
const { i18n } = await import("@/app/i18n");
const { ThemeProvider } = await import("@/app/theme-provider");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { ProductShell } = await import("./product-shell");

function TestIcon(props: SVGProps<SVGSVGElement>) {
  return <svg aria-hidden="true" {...props} />;
}

const groups = [
  {
    label: "Commercial",
    items: [{ label: "Forfaits", to: "/admin/plans", icon: TestIcon as Icon }],
  },
];

function renderShell() {
  return render(
    <I18nextProvider i18n={i18n}>
      <ThemeProvider>
        <TooltipProvider>
          <MemoryRouter initialEntries={["/admin/plans"]}>
            <ProductShell email="admin@hiveapp.test" groups={groups} label="Administration" onLogout={() => {}}>
              <p>Contenu</p>
            </ProductShell>
          </MemoryRouter>
        </TooltipProvider>
      </ThemeProvider>
    </I18nextProvider>,
  );
}

beforeEach(async () => {
  cleanup();
  browser.localStorage.clear();
  await i18n.changeLanguage("fr");
});

afterEach(() => cleanup());

describe("product shell navigation", () => {
  test("group disclosure exposes its state and hides its links when collapsed", async () => {
    const screen = renderShell();
    const user = userEvent.setup({ document: browser.document as unknown as Document });
    const disclosure = screen.getByRole("button", { name: "Commercial" });

    expect(disclosure.getAttribute("aria-expanded")).toBe("true");
    expect(disclosure.getAttribute("aria-controls")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Forfaits" })).toBeTruthy();

    await user.click(disclosure);

    expect(disclosure.getAttribute("aria-expanded")).toBe("false");
    expect(screen.queryByRole("link", { name: "Forfaits" })).toBeNull();
  });

  test("compact mode keeps named links, one expand control, and no boxed active state", async () => {
    const screen = renderShell();
    const user = userEvent.setup({ document: browser.document as unknown as Document });

    await user.click(screen.getByRole("button", { name: "Réduire la navigation" }));

    expect(browser.localStorage.getItem("hiveapp_sidebar_collapsed")).toBe("true");
    expect(screen.getAllByRole("button", { name: "Développer la navigation" })).toHaveLength(1);
    expect(screen.getByRole("navigation").classList.contains("sidebar-nav-scroll--compact")).toBeTrue();
    const activeLink = screen.getByRole("link", { name: "Forfaits" });
    expect(activeLink.classList.contains("shadow-xs")).toBeFalse();
    expect(activeLink.classList.contains("bg-sidebar-accent")).toBeFalse();
  });

  test("shell controls follow the Arabic locale", async () => {
    await i18n.changeLanguage("ar");
    const screen = renderShell();
    const user = userEvent.setup({ document: browser.document as unknown as Document });

    expect(screen.getByRole("button", { name: "طيّ قائمة التنقل" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "تسجيل الخروج" })).toBeTruthy();
    expect(screen.getByText("جلسة نشطة")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "طيّ قائمة التنقل" }));
    expect(screen.getAllByRole("button", { name: "توسيع قائمة التنقل" })).toHaveLength(1);
  });
});
