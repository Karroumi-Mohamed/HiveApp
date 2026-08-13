import { CaretDownIcon, CaretUpDownIcon, CaretUpIcon } from "@phosphor-icons/react";
import type { RowData, RowSelectionState, SortingState, TableFeatures } from "@tanstack/react-table";
import {
  createColumnHelper,
  rowSelectionFeature,
  rowSortingFeature,
  tableFeatures,
  useTable,
} from "@tanstack/react-table";
import type { ReactNode } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

declare module "@tanstack/table-core" {
  /** Per-column styling hooks, so a column owns its width and alignment with its definition. */
  interface ColumnMeta<TFeatures extends TableFeatures, TData extends RowData, TValue> {
    headerClassName?: string;
    cellClassName?: string;
  }
}

/**
 * The feature set every table in the portal shares.
 *
 * <p>Pagination is deliberately absent. Rows are paged by the server and the table renders
 * exactly the page it is handed, so registering the pagination feature would add a second,
 * client-side notion of "the current page" that could disagree with the request that produced
 * the rows.
 */
export const dataTableFeatures = tableFeatures({ rowSortingFeature, rowSelectionFeature });

/** Column helper bound to the shared feature set. Use this to declare a table's columns. */
export function createDataColumns<T extends RowData>() {
  return createColumnHelper<typeof dataTableFeatures, T>();
}

/**
 * Sortable column header. Typed structurally against the column's sorting API rather than the
 * full generic Column type, which would drag every feature generic through each call site.
 */
export function SortHeader({
  column,
  children,
}: {
  column: {
    getIsSorted: () => false | "asc" | "desc";
    getToggleSortingHandler: () => ((event: unknown) => void) | undefined;
  };
  children: ReactNode;
}) {
  const sorted = column.getIsSorted();
  return (
    <button
      className="-mx-2 inline-flex items-center gap-1.5 rounded-md px-2 py-1 hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      onClick={column.getToggleSortingHandler()}
      type="button"
    >
      {children}
      {!sorted ? (
        <CaretUpDownIcon className="size-3.5 text-muted-foreground/60" />
      ) : sorted === "asc" ? (
        <CaretUpIcon className="size-3.5" />
      ) : (
        <CaretDownIcon className="size-3.5" />
      )}
    </button>
  );
}

export type DataTableProps<T extends RowData> = {
  columns: Parameters<ReturnType<typeof createDataColumns<T>>["columns"]>[0];
  data: T[];
  /** Stable identity for selection — index-based keys break as soon as rows are re-sorted. */
  getRowId: (row: T) => string;
  sorting?: SortingState;
  onSortingChange?: (next: SortingState) => void;
  rowSelection?: RowSelectionState;
  onRowSelectionChange?: (next: RowSelectionState) => void;
  rowHeightPx?: number;
  isLoading?: boolean;
  emptyState?: ReactNode;
  /**
   * Bulk editors keyed by column id, rendered as a trailing row. Each control sits in the column
   * it edits, so the connection between "this select" and "that column" needs no explaining.
   * Pass undefined when nothing is selected and the row disappears.
   */
  bulkRow?: Record<string, ReactNode>;
};

export function DataTable<T extends RowData>({
  columns,
  data,
  getRowId,
  sorting,
  onSortingChange,
  rowSelection,
  onRowSelectionChange,
  rowHeightPx = 57,
  isLoading = false,
  emptyState,
  bulkRow,
}: DataTableProps<T>) {
  const table = useTable({
    features: dataTableFeatures,
    columns,
    data,
    getRowId: (row: T) => getRowId(row),
    // Sorting is applied by the server across the whole result set. Letting the table sort as
    // well would reorder only the rows on screen and call the result sorted.
    manualSorting: true,
    ...(sorting ? { state: { sorting }, onSortingChange } : {}),
    ...(rowSelection ? { state: { rowSelection }, onRowSelectionChange } : {}),
  } as never);

  const rows = table.getRowModel().rows;
  const leafColumns = table.getAllLeafColumns();

  const grid = (
    <Table>
      <TableHeader>
        {table.getHeaderGroups().map((group) => (
          <TableRow key={group.id}>
            {group.headers.map((header) => (
              <TableHead className={header.column.columnDef.meta?.headerClassName} key={header.id}>
                {header.isPlaceholder ? null : <table.FlexRender header={header} />}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            data-state={row.getIsSelected() ? "selected" : undefined}
            key={row.id}
            style={{ height: rowHeightPx }}
          >
            {row.getAllCells().map((cell) => (
              <TableCell className={cell.column.columnDef.meta?.cellClassName} key={cell.id}>
                <table.FlexRender cell={cell} />
              </TableCell>
            ))}
          </TableRow>
        ))}
        {bulkRow ? (
          // Rendered inside the same table so its cells inherit the column widths exactly. A
          // separate floating bar can only ever approximate them, which is what makes a bulk
          // editor feel detached from the column it edits.
          <TableRow className="border-t-2 bg-muted/40 hover:bg-muted/40">
            {leafColumns.map((leaf) => (
              <TableCell className={leaf.columnDef.meta?.cellClassName} key={leaf.id}>
                {bulkRow[leaf.id] ?? null}
              </TableCell>
            ))}
          </TableRow>
        ) : null}
      </TableBody>
    </Table>
  );

  const isEmpty = !isLoading && rows.length === 0;
  if (!isEmpty || !emptyState) return grid;

  return (
    <>
      {grid}
      {/* Only when there are no rows at all. The grid never reserves space for rows it does not
          have — a partly filled page simply ends after its last row. The headers stay above this
          so the columns remain readable while nothing matches. */}
      <div
        className={cn(
          "relative grid min-h-72 place-items-center text-muted-foreground/15",
          "[background-image:repeating-linear-gradient(45deg,transparent,transparent_6px,currentColor_6px,currentColor_7px)]",
        )}
      >
        <div className="text-foreground">{emptyState}</div>
      </div>
    </>
  );
}
