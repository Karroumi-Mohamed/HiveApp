import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { ProductPriceDraftErrors, ProductPriceDraftFields } from "./product-price-rules";

function FieldError({ message }: { message?: string }) {
  return message ? (
    <p className="text-xs text-destructive" role="alert">
      {message}
    </p>
  ) : null;
}

export function ProductPriceTermsForm({
  fields,
  errors,
  onChange,
}: {
  fields: ProductPriceDraftFields;
  errors: ProductPriceDraftErrors;
  onChange: (next: ProductPriceDraftFields) => void;
}) {
  const set = <K extends keyof ProductPriceDraftFields>(key: K, value: ProductPriceDraftFields[K]) =>
    onChange({ ...fields, [key]: value });

  return (
    <fieldset className="grid gap-5 border-0 p-0 sm:grid-cols-2">
      <legend className="sr-only">Conditions tarifaires</legend>
      <div className="space-y-2">
        <Label htmlFor="price-book-amount">Montant</Label>
        <Input
          aria-describedby={errors.amount ? "price-book-amount-error" : undefined}
          aria-invalid={Boolean(errors.amount)}
          id="price-book-amount"
          inputMode="decimal"
          min="0"
          onChange={(event) => set("amount", event.target.value)}
          step="0.0001"
          type="number"
          value={fields.amount}
        />
        <div id="price-book-amount-error">
          <FieldError message={errors.amount} />
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="price-book-currency">Devise</Label>
        <Input
          aria-describedby={errors.currencyCode ? "price-book-currency-error" : undefined}
          aria-invalid={Boolean(errors.currencyCode)}
          autoCapitalize="characters"
          id="price-book-currency"
          maxLength={3}
          onChange={(event) => set("currencyCode", event.target.value.toUpperCase())}
          placeholder="MAD"
          value={fields.currencyCode}
        />
        <p className="text-xs text-muted-foreground">Code ISO, par exemple MAD, EUR ou USD.</p>
        <div id="price-book-currency-error">
          <FieldError message={errors.currencyCode} />
        </div>
      </div>
      <div className="space-y-2">
        <Label>Cycle de facturation</Label>
        <Select
          onValueChange={(value: ProductPriceDraftFields["billingCycle"]) => set("billingCycle", value)}
          value={fields.billingCycle}
        >
          <SelectTrigger aria-label="Cycle de facturation">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="MONTHLY">Mensuel</SelectItem>
            <SelectItem value="YEARLY">Annuel</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div aria-hidden="true" className="hidden sm:block" />
      <div className="space-y-2">
        <Label htmlFor="price-book-effective-from">Valide à partir du</Label>
        <Input
          aria-describedby={errors.effectiveFrom ? "price-book-effective-from-error" : undefined}
          aria-invalid={Boolean(errors.effectiveFrom)}
          id="price-book-effective-from"
          onChange={(event) => set("effectiveFrom", event.target.value)}
          type="datetime-local"
          value={fields.effectiveFrom}
        />
        <div id="price-book-effective-from-error">
          <FieldError message={errors.effectiveFrom} />
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="price-book-effective-until">Valide jusqu’au</Label>
        <Input
          aria-describedby={errors.effectiveUntil ? "price-book-effective-until-error" : undefined}
          aria-invalid={Boolean(errors.effectiveUntil)}
          id="price-book-effective-until"
          onChange={(event) => set("effectiveUntil", event.target.value)}
          type="datetime-local"
          value={fields.effectiveUntil}
        />
        <p className="text-xs text-muted-foreground">Laissez vide si aucune fin n’est prévue.</p>
        <div id="price-book-effective-until-error">
          <FieldError message={errors.effectiveUntil} />
        </div>
      </div>
    </fieldset>
  );
}
