import { describe, expect, test } from "bun:test";
import {
  addUtcCalendarMonths,
  agreementDefinitionProblem,
  agreementEnd,
  buildAgreementDefinition,
} from "./special-agreement-rules";

const selection = {
  targetPlanCode: "PRO",
  addOnCodes: [],
  quotaPackages: [],
  timing: "AT_RENEWAL" as const,
  planPriceSelection: { priceEntryId: "price-1", currencyCode: "USD", billingCycle: "MONTHLY" as const },
};

describe("special agreement rules", () => {
  test("uses calendar arithmetic and clamps month ends", () => {
    expect(addUtcCalendarMonths("2028-01-31T10:30:00.000Z", 1)).toBe("2028-02-29T10:30:00.000Z");
    expect(addUtcCalendarMonths("2027-01-31T10:30:00.000Z", 1)).toBe("2027-02-28T10:30:00.000Z");
  });

  test("one month and N-month presets produce exact instants", () => {
    expect(agreementEnd("2028-01-31T10:30", "ONE_MONTH", 7, "")).toBe("2028-02-29T10:30:00.000Z");
    expect(agreementEnd("2028-01-31T10:30", "MONTHS", 2, "")).toBe("2028-03-31T10:30:00.000Z");
  });

  test("forces immediate selection semantics and strips irrelevant fields", () => {
    expect(
      buildAgreementDefinition({
        selection,
        quotaBonuses: [{ featureCode: "platform.staff", resource: "members", quantity: 0 }],
        startsAt: "2028-01-01T00:00:00.000Z",
        endsAt: "2028-02-01T00:00:00.000Z",
        pricingMode: "COMPLIMENTARY",
        customTotal: "99",
        currencyCode: "USD",
        settlementMode: "MANUAL",
        endInstruction: "END_ACCESS",
        followOnPricingMode: "CUSTOM_TOTAL",
        followOnCustomAmount: "5",
      }),
    ).toMatchObject({
      selection: { timing: "IMMEDIATE" },
      quotaBonuses: [],
      customTotal: null,
      settlementMode: "NONE",
      followOnPricingMode: null,
      followOnCustomAmount: null,
    });
  });

  test("rejects amounts that cannot cross the exact backend money boundary", () => {
    const definition = buildAgreementDefinition({
      selection,
      quotaBonuses: [],
      startsAt: "2028-01-01T00:00:00.000Z",
      endsAt: "2028-02-01T00:00:00.000Z",
      pricingMode: "CUSTOM_TOTAL",
      customTotal: "1e3",
      currencyCode: "USD",
      settlementMode: "MANUAL",
      endInstruction: "END_ACCESS",
      followOnPricingMode: "CATALOGUE_TOTAL",
      followOnCustomAmount: "",
    });

    expect(agreementDefinitionProblem(definition)).toBe("Saisissez un montant total valide.");
  });
});
