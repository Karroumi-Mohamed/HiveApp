import type { CommercialChoiceState } from "@/api/contracts";

export type CommercialCodeChoice = { code: string; choiceState: CommercialChoiceState };

export function mergeCommercialChoices<T extends CommercialCodeChoice>(visible: T[], selected: T[]): T[] {
  return [...new Map([...visible, ...selected].map((choice) => [choice.code, choice])).values()];
}

/** Retained or missing values remain removable, but can never be newly selected. */
export function isCommercialChoiceDisabled(
  choice: CommercialCodeChoice,
  currentlySelected: boolean,
  conflicts = false,
): boolean {
  if (currentlySelected) return false;
  return choice.choiceState !== "SELECTABLE" || conflicts;
}

export function commercialChoiceDescription(choice: CommercialCodeChoice & { revisionNumber: number }): string {
  if (choice.choiceState === "NO_LONGER_ACTIVE") return "Sélection conservée — plus disponible";
  if (choice.choiceState === "MISSING") return "Référence absente — peut seulement être retirée";
  return `Révision R${choice.revisionNumber}`;
}
