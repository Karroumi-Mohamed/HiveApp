import { describe, expect, test } from "bun:test";
import {
  actorSurfaceLabel,
  auditOutcomePresentation,
  backlogLabel,
  backlogStatusLabel,
  communicationPurposeLabel,
  communicationStatusPresentation,
  componentStatePresentation,
  formatEvidence,
  logAccessGuidance,
  observabilityComponentLabel,
  observabilityGuidance,
  technicalActionLabel,
  toOptionalInstant,
} from "./operations-presentation";

describe("operations presentation", () => {
  test("turns technical evidence into compact French labels without hiding the source code", () => {
    expect(technicalActionLabel("platform.plans.preview_activation")).toBe("Aperçu avant activation");
    expect(technicalActionLabel("platform.system.process_due")).toBe("Traitement des échéances");
    expect(technicalActionLabel("platform.system.automatic_resume")).toBe("Reprise automatique");
    expect(actorSurfaceLabel("SYSTEM")).toBe("Système");
    expect(auditOutcomePresentation("FAILED")).toEqual({ label: "Échouée", tone: "danger" });
    expect(communicationPurposeLabel("EMAIL_VERIFICATION")).toBe("Vérification de l’adresse email");
    expect(communicationStatusPresentation("SUPPRESSED")).toEqual({ label: "Non envoyé (dev)", tone: "neutral" });
    expect(componentStatePresentation("DEGRADED")).toEqual({ label: "Dégradé", tone: "warning" });
  });

  test("pretty prints JSON evidence and preserves non-JSON evidence", () => {
    expect(formatEvidence('{"after":{"active":true}}')).toContain('\n  "after"');
    expect(formatEvidence("safe text")).toBe("safe text");
    expect(formatEvidence(null)).toBe("Aucune donnée enregistrée.");
  });

  test("turns optional local filter dates into API instants", () => {
    expect(toOptionalInstant("")).toBeUndefined();
    expect(toOptionalInstant("2026-08-31T12:30")).toMatch(/^2026-08-31T/);
  });

  test("presents observability vocabulary in French while keeping unknown provider labels usable", () => {
    expect(observabilityComponentLabel("database", "Database")).toBe("Base de données");
    expect(observabilityComponentLabel("custom", "Search cluster")).toBe("Search cluster");
    expect(observabilityGuidance("email", "SUPPRESSED", "English fallback")).toContain("aucun e-mail");
    expect(backlogLabel("billing_outbox", "Payment commands")).toBe("Commandes de paiement");
    expect(backlogStatusLabel("UNMATCHED")).toBe("Non rapprochés");
    expect(logAccessGuidance(false)).toContain("ne conserve pas les journaux bruts");
  });
});
