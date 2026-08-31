import { PlusIcon, TrashIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import type { OfferPricedChoice, OfferPricingMode, OfferQuotaResourceChoice } from "@/api/offer-contracts";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { OfferDraft, OfferProductDraft } from "./offer-editor-rules";

function money(value: string, currency: string) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return `${value} ${currency}`;
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency }).format(amount);
}

function cycle(value: OfferPricedChoice["billingCycle"]) {
  return value === "MONTHLY" ? "mois" : value === "YEARLY" ? "an" : "unique";
}

function choiceLabel(choice: OfferPricedChoice) {
  return `${choice.productName} · ${money(choice.amount, choice.currencyCode)} / ${cycle(choice.billingCycle)} · R${choice.productRevisionNumber}`;
}

function selected(item: OfferProductDraft, choice: OfferPricedChoice) {
  return item.productId === choice.productId && item.priceId === choice.priceId;
}

function PricingModeSelect({
  item,
  onChange,
}: {
  item: OfferProductDraft;
  onChange: (item: OfferProductDraft) => void;
}) {
  return (
    <Select
      onValueChange={(value) => onChange({ ...item, pricingMode: value as OfferPricingMode })}
      value={item.pricingMode}
    >
      <SelectTrigger aria-label="Traitement du prix" className="h-8 w-36">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="PAID">Prix normal</SelectItem>
        <SelectItem value="FREE">Offert</SelectItem>
      </SelectContent>
    </Select>
  );
}

function ProductRows({
  choices,
  items,
  kind,
  onChange,
}: {
  choices: OfferPricedChoice[];
  items: OfferProductDraft[];
  kind: "ADD_ON" | "QUOTA_PACKAGE";
  onChange: (items: OfferProductDraft[]) => void;
}) {
  const toggle = (choice: OfferPricedChoice, checked: boolean) => {
    if (!checked) return onChange(items.filter((item) => !selected(item, choice)));
    if (items.some((item) => item.productId === choice.productId)) {
      return onChange(
        items.map((item) => (item.productId === choice.productId ? { ...item, priceId: choice.priceId } : item)),
      );
    }
    onChange([...items, { productId: choice.productId, priceId: choice.priceId, pricingMode: "PAID", quantity: 1 }]);
  };
  return (
    <div className="divide-y border-y">
      {choices.map((choice) => {
        const item = items.find((candidate) => selected(candidate, choice));
        const sameProductSelected = items.some((candidate) => candidate.productId === choice.productId);
        return (
          <div className="grid gap-3 py-3 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center" key={choice.priceId}>
            <Label className="flex min-w-0 cursor-pointer items-start gap-3 font-normal">
              <Checkbox checked={Boolean(item)} onCheckedChange={(checked) => toggle(choice, checked === true)} />
              <span className="min-w-0">
                <span className="block truncate font-medium">{choice.productName}</span>
                <span className="block text-xs text-muted-foreground">
                  {money(choice.amount, choice.currencyCode)} / {cycle(choice.billingCycle)} · R
                  {choice.productRevisionNumber}
                  {sameProductSelected && !item ? " · autre prix sélectionné" : ""}
                </span>
              </span>
            </Label>
            {item && kind === "QUOTA_PACKAGE" ? (
              <Input
                aria-label={`Quantité de ${choice.productName}`}
                className="h-8 w-24"
                min={1}
                onChange={(event) =>
                  onChange(
                    items.map((candidate) =>
                      candidate === item
                        ? { ...candidate, quantity: Math.max(1, Number(event.target.value) || 1) }
                        : candidate,
                    ),
                  )
                }
                type="number"
                value={item.quantity}
              />
            ) : (
              <span />
            )}
            {item ? (
              <PricingModeSelect
                item={item}
                onChange={(next) => onChange(items.map((candidate) => (candidate === item ? next : candidate)))}
              />
            ) : (
              <span />
            )}
          </div>
        );
      })}
      {!choices.length ? (
        <p className="py-5 text-sm text-muted-foreground">Aucun produit compatible disponible.</p>
      ) : null}
    </div>
  );
}

export function OfferProductSelector({
  draft,
  plans,
  addOns,
  packages,
  quotaResources,
  onChange,
}: {
  draft: OfferDraft;
  plans: OfferPricedChoice[];
  addOns: OfferPricedChoice[];
  packages: OfferPricedChoice[];
  quotaResources: OfferQuotaResourceChoice[];
  onChange: (draft: OfferDraft) => void;
}) {
  const availableResources = quotaResources.filter(
    (choice) =>
      !draft.finiteQuotaBonuses.some(
        (bonus) => bonus.featureCode === choice.featureCode && bonus.resource === choice.resource,
      ),
  );
  const [resourceKey, setResourceKey] = useState("");
  useEffect(() => {
    if (resourceKey && availableResources.some((choice) => `${choice.featureCode}:${choice.resource}` === resourceKey))
      return;
    const first = availableResources[0];
    setResourceKey(first ? `${first.featureCode}:${first.resource}` : "");
  }, [availableResources, resourceKey]);
  const addBonus = () => {
    const choice = availableResources.find((item) => `${item.featureCode}:${item.resource}` === resourceKey);
    if (!choice) return;
    onChange({
      ...draft,
      finiteQuotaBonuses: [
        ...draft.finiteQuotaBonuses,
        { featureCode: choice.featureCode, resource: choice.resource, quantity: 1 },
      ],
    });
  };
  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <div>
          <h2 className="font-medium">Forfait cible</h2>
          <p className="text-sm text-muted-foreground">Le prix exact fixe la devise et le cycle de toute l’offre.</p>
        </div>
        <Select
          onValueChange={(priceId) => {
            const choice = plans.find((candidate) => candidate.priceId === priceId);
            if (choice)
              onChange({
                ...draft,
                plan: { productId: choice.productId, priceId: choice.priceId, pricingMode: "PAID", quantity: 1 },
              });
          }}
          value={draft.plan.priceId || undefined}
        >
          <SelectTrigger aria-label="Forfait et prix">
            <SelectValue placeholder="Sélectionner un forfait et un prix" />
          </SelectTrigger>
          <SelectContent>
            {plans.map((choice) => (
              <SelectItem key={choice.priceId} value={choice.priceId}>
                {choiceLabel(choice)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </section>
      <section className="space-y-3">
        <div>
          <h2 className="font-medium">Add-ons</h2>
          <p className="text-sm text-muted-foreground">
            Sélectionnez une révision et un prix exacts. Un produit peut être facturé ou offert.
          </p>
        </div>
        <ProductRows
          choices={addOns}
          items={draft.addOns}
          kind="ADD_ON"
          onChange={(items) => onChange({ ...draft, addOns: items })}
        />
      </section>
      <section className="space-y-3">
        <div>
          <h2 className="font-medium">Packs de capacité</h2>
          <p className="text-sm text-muted-foreground">La quantité est figée dans les conditions acceptées.</p>
        </div>
        <ProductRows
          choices={packages}
          items={draft.quotaPackages}
          kind="QUOTA_PACKAGE"
          onChange={(items) => onChange({ ...draft, quotaPackages: items })}
        />
      </section>
      <section className="space-y-3">
        <div>
          <h2 className="font-medium">Capacité offerte en plus</h2>
          <p className="text-sm text-muted-foreground">
            Bonus fini, rattaché à une ressource déjà présente dans la sélection.
          </p>
        </div>
        <div className="divide-y border-y">
          {draft.finiteQuotaBonuses.map((bonus) => (
            <div
              className="grid gap-3 py-3 sm:grid-cols-[minmax(0,1fr)_120px_auto] sm:items-center"
              key={`${bonus.featureCode}:${bonus.resource}`}
            >
              <span>
                <span className="block font-medium">
                  {quotaResources.find(
                    (item) => item.featureCode === bonus.featureCode && item.resource === bonus.resource,
                  )?.featureName ?? bonus.featureCode}
                </span>
                <span className="block text-xs text-muted-foreground">{bonus.resource}</span>
              </span>
              <Input
                aria-label={`Bonus ${bonus.resource}`}
                min={1}
                onChange={(event) =>
                  onChange({
                    ...draft,
                    finiteQuotaBonuses: draft.finiteQuotaBonuses.map((item) =>
                      item === bonus ? { ...item, quantity: Math.max(1, Number(event.target.value) || 1) } : item,
                    ),
                  })
                }
                type="number"
                value={bonus.quantity}
              />
              <Button
                aria-label={`Supprimer le bonus ${bonus.resource}`}
                onClick={() =>
                  onChange({ ...draft, finiteQuotaBonuses: draft.finiteQuotaBonuses.filter((item) => item !== bonus) })
                }
                size="icon-sm"
                variant="ghost"
              >
                <TrashIcon />
              </Button>
            </div>
          ))}
          {!draft.finiteQuotaBonuses.length ? (
            <p className="py-4 text-sm text-muted-foreground">Aucun bonus de capacité.</p>
          ) : null}
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Select onValueChange={setResourceKey} value={resourceKey || undefined}>
            <SelectTrigger aria-label="Ressource du bonus" className="sm:max-w-md">
              <SelectValue placeholder="Sélectionner une ressource" />
            </SelectTrigger>
            <SelectContent>
              {availableResources.map((choice) => (
                <SelectItem
                  key={`${choice.featureCode}:${choice.resource}`}
                  value={`${choice.featureCode}:${choice.resource}`}
                >
                  {choice.featureName} · {choice.resource} ({choice.unit})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button disabled={!resourceKey} onClick={addBonus} size="sm" type="button" variant="outline">
            <PlusIcon />
            Ajouter
          </Button>
        </div>
      </section>
    </div>
  );
}
