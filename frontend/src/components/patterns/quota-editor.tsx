import type { QuotaLimit } from "@/api/contracts";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export type QuotaSlot = { resource: string; type: string; unit: string };

function replaceQuota(value: QuotaLimit[], resource: string, next: QuotaLimit | null) {
  const remaining = value.filter((item) => item.resource !== resource);
  return next ? [...remaining, next] : remaining;
}

export function QuotaEditor({
  slots,
  value,
  onChange,
  disabled = false,
}: {
  slots: QuotaSlot[];
  value: QuotaLimit[];
  onChange: (value: QuotaLimit[]) => void;
  disabled?: boolean;
}) {
  if (!slots.length) return <p className="text-sm text-muted-foreground">Cette fonctionnalité n’a aucun quota.</p>;
  return (
    <div className="divide-y border-y">
      {slots.map((slot) => {
        const configured = value.find((item) => item.resource === slot.resource);
        const mode = configured?.mode ?? "NONE";
        return (
          <div className="grid gap-3 py-3 sm:grid-cols-[1fr_9rem_9rem] sm:items-center" key={slot.resource}>
            <div>
              <p className="text-sm font-medium">{slot.resource}</p>
              <p className="text-xs text-muted-foreground">
                {slot.type} · {slot.unit}
              </p>
            </div>
            <Select
              disabled={disabled}
              onValueChange={(nextMode) =>
                onChange(
                  replaceQuota(
                    value,
                    slot.resource,
                    nextMode === "NONE"
                      ? null
                      : {
                          resource: slot.resource,
                          mode: nextMode as QuotaLimit["mode"],
                          limit: nextMode === "FINITE" ? (configured?.limit ?? 0) : null,
                        },
                  ),
                )
              }
              value={mode}
            >
              <SelectTrigger aria-label={`Mode du quota ${slot.resource}`}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NONE">Non défini</SelectItem>
                <SelectItem value="FINITE">Limité</SelectItem>
                <SelectItem value="UNLIMITED">Illimité</SelectItem>
              </SelectContent>
            </Select>
            <Input
              aria-label={`Limite ${slot.resource}`}
              disabled={disabled || mode !== "FINITE"}
              min="0"
              onChange={(event) =>
                onChange(
                  replaceQuota(value, slot.resource, {
                    resource: slot.resource,
                    mode: "FINITE",
                    limit: Number(event.target.value),
                  }),
                )
              }
              type="number"
              value={mode === "FINITE" ? (configured?.limit ?? 0) : ""}
            />
          </div>
        );
      })}
    </div>
  );
}
