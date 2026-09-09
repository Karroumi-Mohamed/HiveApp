import { expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router";
import type { PlanOperationalItem } from "@/api/contracts";
import { TooltipProvider } from "@/components/ui/tooltip";
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
  expect(html).toContain("Fonctionnalités incluses");
  expect(html).toContain("Abonnements actuels");
  expect(html).toContain("2 autres fonctionnalités configurées");
  expect(html).toContain("Tarifs de test");
  expect(html).toContain('href="/admin/plans/plan-one"');
  expect(html).toContain("Ouvrir le forfait");
  expect(html).not.toContain("HIDDEN_TECHNICAL_CODE");
  expect(html).not.toContain("<table");
});
test("restricted cards retain explained controls without navigation or mutation links", () => {
  const html = card(false);
  expect(html).not.toContain("href=");
  expect(html).toContain('aria-disabled="true"');
  expect(html).toContain("Consultation non autorisée");
});
test("draft cards cannot offer revision when the backend omits the action", () => {
  const html = card(true, { ...plan, status: "DRAFT", availableActions: [] });
  expect(html).toContain('aria-label="Ouvrir pour réviser"');
  expect(html).toContain('aria-disabled="true"');
  expect(html).toContain('href="/admin/plans/new?from=plan-one"');
});
