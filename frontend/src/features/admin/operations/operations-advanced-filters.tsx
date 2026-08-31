import { FunnelSimpleIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

export function OperationsAdvancedFilters({
  activeCount,
  children,
  onApply,
  onClear,
}: {
  activeCount: number;
  children: React.ReactNode;
  onApply: () => void;
  onClear: () => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Popover onOpenChange={setOpen} open={open}>
      <PopoverTrigger asChild>
        <Button size="sm" type="button" variant="outline">
          <FunnelSimpleIcon />
          Plus de filtres{activeCount ? ` (${activeCount})` : ""}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(32rem,calc(100vw-2rem))]">
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            onApply();
            setOpen(false);
          }}
        >
          <div className="grid gap-3 sm:grid-cols-2">{children}</div>
          <div className="flex justify-end gap-2 border-t pt-3">
            <Button
              onClick={() => {
                onClear();
                setOpen(false);
              }}
              size="sm"
              type="button"
              variant="ghost"
            >
              Effacer
            </Button>
            <Button size="sm" type="submit">
              Appliquer
            </Button>
          </div>
        </form>
      </PopoverContent>
    </Popover>
  );
}

export function OperationsFilterField({
  children,
  htmlFor,
  label,
}: {
  children: React.ReactNode;
  htmlFor: string;
  label: string;
}) {
  return (
    <div className="space-y-1 text-xs font-medium">
      <label className="block text-muted-foreground" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
    </div>
  );
}
