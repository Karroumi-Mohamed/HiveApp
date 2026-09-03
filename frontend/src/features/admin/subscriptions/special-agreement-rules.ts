import type {
  SpecialAgreementDefinition,
  SpecialAgreementEndInstruction,
  SpecialAgreementPricingMode,
  SpecialAgreementSettlementMode,
  SubscriptionChangeInput,
} from "@/api/contracts";
import { isCommercialAmount } from "@/lib/exact-decimal";

export type AgreementTermMode = "ONE_MONTH" | "MONTHS" | "EXACT";

export function addUtcCalendarMonths(start: string, months: number): string {
  const source = new Date(start);
  if (Number.isNaN(source.getTime()) || !Number.isInteger(months) || months < 1) return "";
  const year = source.getUTCFullYear();
  const month = source.getUTCMonth() + months;
  const day = source.getUTCDate();
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return new Date(
    Date.UTC(
      year,
      month,
      Math.min(day, lastDay),
      source.getUTCHours(),
      source.getUTCMinutes(),
      source.getUTCSeconds(),
      source.getUTCMilliseconds(),
    ),
  ).toISOString();
}

export function toInstant(localValue: string): string | null {
  const parsed = new Date(localValue);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

export function toLocalDateTime(instant: string): string {
  const date = new Date(instant);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

export function agreementEnd(
  startsAt: string,
  mode: AgreementTermMode,
  months: number,
  exactEnd: string,
): string | null {
  const start = toInstant(startsAt);
  if (!start) return null;
  if (mode === "EXACT") return toInstant(exactEnd);
  return addUtcCalendarMonths(start, mode === "ONE_MONTH" ? 1 : months) || null;
}

export function buildAgreementDefinition(input: {
  selection: SubscriptionChangeInput;
  quotaBonuses: SpecialAgreementDefinition["quotaBonuses"];
  startsAt: string;
  endsAt: string;
  pricingMode: SpecialAgreementPricingMode;
  customTotal: string;
  currencyCode: string;
  settlementMode: SpecialAgreementSettlementMode;
  endInstruction: SpecialAgreementEndInstruction;
  followOnPricingMode: SpecialAgreementPricingMode;
  followOnCustomAmount: string;
}): SpecialAgreementDefinition {
  return {
    selection: { ...input.selection, timing: "IMMEDIATE" },
    quotaBonuses: input.quotaBonuses.filter((item) => item.quantity > 0),
    startsAt: input.startsAt,
    endsAt: input.endsAt,
    pricingMode: input.pricingMode,
    customTotal: input.pricingMode === "CUSTOM_TOTAL" ? input.customTotal : null,
    currencyCode: input.currencyCode,
    settlementMode: input.pricingMode === "COMPLIMENTARY" ? "NONE" : input.settlementMode,
    endInstruction: input.endInstruction,
    followOnPricingMode: input.endInstruction === "CONTINUE_REVIEWED_TERMS" ? input.followOnPricingMode : null,
    followOnCustomAmount:
      input.endInstruction === "CONTINUE_REVIEWED_TERMS" && input.followOnPricingMode === "CUSTOM_TOTAL"
        ? input.followOnCustomAmount
        : null,
  };
}

export function agreementDefinitionProblem(definition: SpecialAgreementDefinition): string | null {
  if (!definition.endsAt || new Date(definition.endsAt) <= new Date(definition.startsAt)) {
    return "La fin doit être postérieure au début.";
  }
  if (definition.pricingMode === "CUSTOM_TOTAL" && !validAmount(definition.customTotal)) {
    return "Saisissez un montant total valide.";
  }
  if (
    definition.endInstruction === "CONTINUE_REVIEWED_TERMS" &&
    definition.followOnPricingMode === "CUSTOM_TOTAL" &&
    !validAmount(definition.followOnCustomAmount)
  ) {
    return "Saisissez le montant récurrent après la période.";
  }
  return null;
}

function validAmount(value: string | null): boolean {
  return Boolean(value && isCommercialAmount(value));
}
