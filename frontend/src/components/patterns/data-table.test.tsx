import { afterEach, describe, expect, test } from "bun:test";
import type { SortingState } from "@tanstack/react-table";
import { Window } from "happy-dom";
import { useState } from "react";

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
  const value = key === "window" ? browser : browser[key];
  Object.defineProperty(globalThis, key, { configurable: true, value });
}

const { cleanup, render } = await import("@testing-library/react");
const userEvent = (await import("@testing-library/user-event")).default;
const { MemoryRouter, useLocation } = await import("react-router");
const { TooltipProvider } = await import("@/components/ui/tooltip");
const { createDataColumns, DataTable, DataTableExpander, SortHeader } = await import("./data-table");
const { ReferenceTagButton } = await import("./reference-tag");
const { RowAction } = await import("./row-action");

type Item = { id: string; name: string; detail: string };
const column = createDataColumns<Item>();
const columns = column.columns([
  column.display({
    id: "details",
    header: "",
    cell: ({ row }) => (
      <DataTableExpander
        collapseLabel={`Masquer ${row.original.name}`}
        expandLabel={`Afficher ${row.original.name}`}
        row={row}
      />
    ),
  }),
  column.accessor("name", { header: "Nom", cell: ({ getValue }) => getValue() }),
  column.display({
    id: "related",
    header: "Associé",
    cell: ({ row }) => (
      <ReferenceTagButton
        aria-expanded={row.getIsExpanded()}
        aria-label={`${row.getIsExpanded() ? "Masquer" : "Afficher"} les offres de ${row.original.name}`}
        onClick={row.getToggleExpandedHandler()}
      >
        {row.original.detail}
      </ReferenceTagButton>
    ),
  }),
]);

afterEach(() => cleanup());

describe("data table detail rows", () => {
  test("the disclosure control opens and closes an accessible full-width detail row", async () => {
    const view = render(
      <DataTable
        columns={columns}
        data={[{ id: "roles", name: "Workspace Roles", detail: "Custom Roles" }]}
        getRowCanExpand={() => true}
        getRowId={(row) => row.id}
        renderExpandedRow={(row) => <p data-testid="expanded-detail">{row.detail}</p>}
      />,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });

    const disclosure = view.getByRole("button", { name: "Afficher Workspace Roles" });
    expect(disclosure.getAttribute("aria-expanded")).toBe("false");
    expect(view.queryByTestId("expanded-detail")).toBeNull();

    await user.click(disclosure);

    expect(view.getByTestId("expanded-detail").textContent).toBe("Custom Roles");
    expect(view.getByRole("button", { name: "Masquer Workspace Roles" }).getAttribute("aria-expanded")).toBe("true");

    await user.click(view.getByRole("button", { name: "Masquer Workspace Roles" }));
    expect(view.queryByTestId("expanded-detail")).toBeNull();
  });

  test("an inline reference can disclose the same detail row", async () => {
    const view = render(
      <DataTable
        columns={columns}
        data={[{ id: "roles", name: "Workspace Roles", detail: "Custom Roles" }]}
        getRowCanExpand={() => true}
        getRowId={(row) => row.id}
        renderExpandedRow={(row) => <p data-testid="expanded-detail">{row.detail}</p>}
      />,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });

    const reference = view.getByRole("button", { name: "Afficher les offres de Workspace Roles" });
    expect(reference.getAttribute("aria-expanded")).toBe("false");

    await user.click(reference);

    expect(view.getAllByText("Custom Roles")).toHaveLength(2);
    expect(
      view.getByRole("button", { name: "Masquer les offres de Workspace Roles" }).getAttribute("aria-expanded"),
    ).toBe("true");
  });
});

describe("data table sorting semantics", () => {
  test("a sortable heading exposes its controlled sort direction", async () => {
    const sortableColumns = column.columns([
      column.accessor("name", {
        header: ({ column: sortableColumn }) => <SortHeader column={sortableColumn}>Nom</SortHeader>,
        cell: ({ getValue }) => getValue(),
      }),
    ]);
    function SortableTable() {
      const [sorting, setSorting] = useState<SortingState>([]);
      return (
        <DataTable
          columns={sortableColumns}
          data={[{ id: "roles", name: "Workspace Roles", detail: "Custom Roles" }]}
          getRowId={(row) => row.id}
          onSortingChange={setSorting}
          sorting={sorting}
        />
      );
    }
    const view = render(<SortableTable />);
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const header = view.getByRole("columnheader", { name: "Nom" });

    expect(header.getAttribute("aria-sort")).toBeNull();
    await user.click(view.getByRole("button", { name: "Nom" }));
    expect(header.getAttribute("aria-sort")).toBe("ascending");
    await user.click(view.getByRole("button", { name: "Nom" }));
    expect(header.getAttribute("aria-sort")).toBe("descending");
  });
});

describe("row actions", () => {
  test("disabled wins over a destination and keeps the explanatory control non-navigating", async () => {
    function Location() {
      return <output data-testid="location">{useLocation().pathname}</output>;
    }
    const view = render(
      <TooltipProvider>
        <MemoryRouter initialEntries={["/admin/plans"]}>
          <RowAction
            disabled
            disabledLabel="Action indisponible"
            icon={<span aria-hidden="true">×</span>}
            label="Ouvrir"
            to="/admin/plans/plan-1"
          />
          <Location />
        </MemoryRouter>
      </TooltipProvider>,
    );
    const user = userEvent.setup({ document: view.container.ownerDocument });
    const control = view.getByRole("button", { name: "Ouvrir" });

    expect(control.getAttribute("aria-disabled")).toBe("true");
    expect(view.queryByRole("link", { name: "Ouvrir" })).toBeNull();
    await user.click(control);
    expect(view.getByTestId("location").textContent).toBe("/admin/plans");
  });
});
