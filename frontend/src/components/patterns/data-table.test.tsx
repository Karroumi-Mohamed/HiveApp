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
const { createDataColumns, DataTable, DataTableExpander } = await import("./data-table");
const { ReferenceTagButton } = await import("./reference-tag");

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
