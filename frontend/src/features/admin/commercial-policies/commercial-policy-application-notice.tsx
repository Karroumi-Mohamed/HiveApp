import { WarningCircleIcon } from "@phosphor-icons/react";
import type { CommercialPolicyExecutionBlocker } from "@/api/contracts";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { executionBlocker } from "./commercial-policy-rules";

/** Shared wording keeps policy activation distinct from subscription application. */
export function CommercialPolicyExplicitApplicationNotice() {
  return (
    <Alert>
      <AlertTitle>Application lors d’une opération explicite</AlertTitle>
      <AlertDescription>
        L’activation rend la politique éligible sans modifier les abonnements en masse. Ses effets sont évalués quand un
        changement d’abonnement est prévisualisé puis confirmé.
      </AlertDescription>
    </Alert>
  );
}

export function CommercialPolicyApplicationStatus({
  executionSupported,
  blockers,
}: {
  executionSupported: boolean;
  blockers: CommercialPolicyExecutionBlocker[];
}) {
  if (executionSupported && !blockers.length) return null;
  return (
    <Alert className="border-warning/30 bg-warning/5">
      <WarningCircleIcon />
      <AlertTitle>
        {executionSupported ? "Application explicite disponible" : "Application aux abonnements indisponible"}
      </AlertTitle>
      <AlertDescription>
        {executionSupported ? (
          <p>
            L’activation ne change rien immédiatement. La politique est évaluée pendant une opération d’abonnement
            prévisualisée et confirmée.
          </p>
        ) : (
          <p>La définition peut être activée, mais ses effets ne sont pas encore appliqués aux abonnements.</p>
        )}
        {blockers.length ? (
          <ul className="mt-2 list-disc ps-4">
            {blockers.map((blocker) => (
              <li key={blocker}>{executionBlocker[blocker]}</li>
            ))}
          </ul>
        ) : null}
      </AlertDescription>
    </Alert>
  );
}
