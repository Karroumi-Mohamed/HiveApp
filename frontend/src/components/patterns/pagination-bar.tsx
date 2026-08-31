import { CaretLeftIcon, CaretRightIcon } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";

export function PaginationBar({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <div className="flex flex-col justify-between gap-3 border-t px-4 py-3 text-sm sm:flex-row sm:items-center">
      <p className="text-xs text-muted-foreground">
        <span className="font-medium text-foreground tabular-nums">{totalElements}</span> résultats
      </p>
      <div className="flex items-center gap-2">
        <span className="text-xs text-muted-foreground">
          Page {Math.min(page + 1, Math.max(totalPages, 1))} sur {Math.max(totalPages, 1)}
        </span>
        <Button
          aria-label="Page précédente"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          size="icon-sm"
          variant="outline"
        >
          <CaretLeftIcon className="rtl:rotate-180" />
        </Button>
        <Button
          aria-label="Page suivante"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          size="icon-sm"
          variant="outline"
        >
          <CaretRightIcon className="rtl:rotate-180" />
        </Button>
      </div>
    </div>
  );
}
