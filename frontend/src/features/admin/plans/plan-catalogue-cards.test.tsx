import { expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router";
import type { PlanOperationalItem, ProductPrice } from "@/api/contracts";
import { TooltipProvider } from "@/components/ui/tooltip";
import { ProductPriceOptions } from "@/features/admin/price-books/product-price-summary";
import { PlanCatalogueCard } from "./plan-catalogue-cards";

const plan = {
  id: "plan-one",
  name: "Entreprise",
  code: "HIDDEN_TECHNICAL_CODE",
  status: "ACTIVE",
  revisionNumber: 2,
  salesVisibility: "PUBLIC",
  includedFeatureCount: 5,
  featureCount: 7,
  currentSubscriberCount: 42,
  blockers: [],
  availableActions: ["REVISE"],
} as unknown as PlanOperationalItem;

function card(allowed = true, item = plan) {
  return renderToStaticMarkup(
    <MemoryRouter>
      <TooltipProvider>
        <PlanCatalogueCard
          plan={item}
          pricing={<p>Tarifs de test</p>}
          canOpen={allowed}
          canRevise={allowed}
          canDuplicate={allowed}
        />
      </TooltipProvider>
    </MemoryRouter>,
  );
}
test("plan catalogue uses a semantic card with meaningful facts and explicit navigation", () => {
  const html = card();
  expect(html).toContain('aria-label="Forfait Entreprise"');
  expect(html).toContain("fonctionnalités");
  expect(html).toContain("abonnés");
  expect(html).not.toContain("autres fonctionnalités configurées");
  expect(html).not.toContain("Catalogue public");
  expect(html).not.toContain("Version 2");
  expect(html).not.toContain("Prêt pour la vente");
  expect(html).not.toContain("Tarifs actuels");
  expect(html).toContain("Tarifs de test");
  expect(html).toContain('href="/admin/plans/plan-one"');
  expect(html).toContain("Ouvrir le forfait");
  expect(html).toMatch(/<fieldset[^>]*aria-label="Actions pour Entreprise"/);
  expect(html).toMatch(/<a[^>]*aria-label="Dupliquer le forfait Entreprise"[^>]*>.*Dupliquer<\/a>/);
  expect(html).toMatch(/<a[^>]*aria-label="Créer une version du forfait Entreprise"[^>]*>.*Créer une version<\/a>/);
  expect(html).not.toContain("bg-muted/40");
  expect(html).not.toContain("shadow-sm");
  expect(html).not.toContain("HIDDEN_TECHNICAL_CODE");
  expect(html).not.toContain("<table");
});
test("restricted cards retain explained controls without navigation or mutation links", () => {
  const html = card(false);
  expect(html).not.toContain("href=");
  expect(html).toContain('aria-disabled="true"');
  expect(html.match(/aria-disabled="true"/g)).toHaveLength(3);
  expect(html).toContain('aria-label="Ouvrir le forfait Entreprise"');
});
test("draft cards cannot offer revision when the backend omits the action", () => {
  const html = card(true, { ...plan, status: "DRAFT", availableActions: [] });
  expect(html).toContain('aria-label="Créer une version du forfait Entreprise"');
  expect(html).toContain('aria-disabled="true"');
  expect(html).toContain('href="/admin/plans/new?from=plan-one"');
});

test("cards keep sale problems visible without routine lifecycle or deletion notices", () => {
  const html = card(true, {
    ...plan,
    blockers: ["DEFAULT_PLAN_LOCKED", "PUBLISHED_PRICE_HISTORY", "NO_ACTIVE_PRICE"],
  });
  expect(html).toContain("Aucun tarif actif");
  expect(html).not.toContain("Forfait par défaut protégé");
  expect(html).not.toContain("Historique tarifaire publié");
});

test("catalogue prices group exact amounts with one currency and their own cycle", () => {
  const prices = [
    { id: "m", amount: "99.99", currencyCode: "USD", billingCycle: "MONTHLY" },
    { id: "y", amount: "999", currencyCode: "USD", billingCycle: "YEARLY" },
    { id: "free", amount: "0", currencyCode: "MAD", billingCycle: "MONTHLY" },
  ] as ProductPrice[];
  const html = renderToStaticMarkup(<ProductPriceOptions prices={prices} variant="catalogue" />);
  const text = html.replace(/<[^>]*>/g, "");
  expect(text).toContain("99,99USD / mois");
  expect(text).toContain("999,00USD / an");
  expect(text).toContain("0,00MAD / mois");
  expect(text).not.toContain("$US");
  expect(text.match(/USD/g)).toHaveLength(2);
  expect(html).toContain('aria-label="Tarifs actuels"');
});
