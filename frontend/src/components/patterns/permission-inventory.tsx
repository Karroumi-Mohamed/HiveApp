import { CaretDownIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";

export function PermissionInventory({ permissions }: { permissions: string[] }) {
  const [open, setOpen] = useState(false);
  return (
    <Collapsible onOpenChange={setOpen} open={open}>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-sm font-semibold">Permissions effectives</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {permissions.length} actions disponibles dans ce contexte
          </p>
        </div>
        <CollapsibleTrigger asChild>
          <Button size="sm" variant="ghost">
            {open ? "Masquer" : "Afficher"}
            <CaretDownIcon className={open ? "rotate-180" : undefined} />
          </Button>
        </CollapsibleTrigger>
      </div>
      <CollapsibleContent>
        <div className="mt-4 grid border-y sm:grid-cols-2">
          {permissions.toSorted().map((permission) => (
            <code className="min-w-0 break-all border-b py-2 text-xs sm:odd:pe-4 sm:even:ps-4" key={permission}>
              {permission}
            </code>
          ))}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
