import { MinusIcon, PlusIcon } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";

export function SubscriptionQuantityControl({
  label,
  value,
  maximum,
  onChange,
  disabled = false,
}: {
  label: string;
  value: number;
  maximum: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}) {
  return (
    <div className="inline-flex items-center rounded-lg border">
      <Button
        aria-label={`Réduire ${label}`}
        disabled={disabled || value <= 0}
        onClick={() => onChange(value - 1)}
        size="icon-sm"
        type="button"
        variant="ghost"
      >
        <MinusIcon />
      </Button>
      <output aria-label={`Quantité ${label}`} className="min-w-8 text-center text-sm font-semibold tabular-nums">
        {value}
      </output>
      <Button
        aria-label={`Augmenter ${label}`}
        disabled={disabled || value >= maximum}
        onClick={() => onChange(value + 1)}
        size="icon-sm"
        type="button"
        variant="ghost"
      >
        <PlusIcon />
      </Button>
    </div>
  );
}

/**
 * A retained package can be held at its exact historical quantity even when it
 * is no longer sold. In that state the backend accepts keep-or-remove, not an
 * arbitrary intermediate quantity.
 */
export function RetainedSubscriptionQuantityControl({
  label,
  value,
  retainedQuantity,
  maximum,
  quantityEditable,
  removable,
  onChange,
}: {
  label: string;
  value: number;
  retainedQuantity: number;
  maximum: number;
  quantityEditable: boolean;
  removable: boolean;
  onChange: (value: number) => void;
}) {
  if (quantityEditable) {
    return <SubscriptionQuantityControl label={label} maximum={maximum} onChange={onChange} value={value} />;
  }
  if (removable) {
    return (
      <Button onClick={() => onChange(value > 0 ? 0 : retainedQuantity)} size="sm" type="button" variant="ghost">
        {value > 0 ? "Retirer" : "Conserver"}
      </Button>
    );
  }
  return <span className="text-sm font-semibold tabular-nums">× {retainedQuantity}</span>;
}
