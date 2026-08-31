import {
  BuildingsIcon,
  ChartLineUpIcon,
  EnvelopeSimpleIcon,
  HandshakeIcon,
  HeartbeatIcon,
  PulseIcon,
  StackIcon,
} from "@phosphor-icons/react";
import type { ComponentProps } from "react";
import { Navigate } from "react-router";
import { useAdminSession } from "@/auth/session-provider";
import { PlaceholderPage } from "@/components/patterns/placeholder-page";

/**
 * The planned admin sections, navigable before they are built. Each page states what was decided
 * for the section, so the navigation carries the product roadmap instead of hiding it.
 */

function PlannedAdminPage(props: ComponentProps<typeof PlaceholderPage>) {
  const session = useAdminSession();
  if (!session.me?.isSuperAdmin) return <Navigate replace to="/admin" />;
  return <PlaceholderPage {...props} />;
}

export function AdminRoleTemplatesPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="Modèles publiés par HiveApp pour créer des rôles clients indépendants."
      icon={StackIcon}
      planned={[
        "Composer et publier des modèles à partir des permissions accordables aux clients",
        "Calculer leur disponibilité selon le forfait et les add-ons, avec un ciblage optionnel de forfaits",
        "Créer depuis un modèle un rôle client indépendant, modifiable et doté d’un nom unique",
        "Mesurer combien de rôles sont créés depuis chaque modèle",
      ]}
      title="Modèles de rôles"
    />
  );
}

export function AdminAccountsPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="La fiche complète de chaque compte client, au même endroit."
      icon={BuildingsIcon}
      planned={[
        "Fiche 360 : entreprises, membres, abonnement et quotas",
        "Actions de support outillées et tracées",
        "Historique des changements du compte",
      ]}
      title="Comptes"
    />
  );
}

export function AdminCollaborationsPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="Supervision des collaborations B2B entre comptes clients."
      icon={HandshakeIcon}
      planned={[
        "Vue des collaborations actives, suspendues et terminées",
        "Délégations accordées et leur plafond",
        "Intervention support sur une collaboration bloquée",
      ]}
      title="Collaborations"
    />
  );
}

export function AdminActivitiesPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="Qui a fait quoi, sur quoi, et quand — l’historique métier de la plateforme."
      icon={PulseIcon}
      planned={[
        "Journal d’audit consultable et filtrable par acteur, ressource et période",
        "Chaque mutation sensible reliée à son opérateur",
        "Export pour investigation",
      ]}
      title="Activités"
    />
  );
}

export function AdminCommunicationsPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="Les emails que la plateforme envoie, et ce qu’ils deviennent."
      icon={EnvelopeSimpleIcon}
      planned={[
        "Historique des envois : activation, vérification, récupération",
        "Échecs de remise et renvois",
        "Aperçu du contenu réellement envoyé",
      ]}
      title="Communications"
    />
  );
}

export function AdminObservabilityPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="La santé technique de la plateforme, pour les investigations en production."
      icon={HeartbeatIcon}
      planned={[
        "Erreurs et exceptions corrélées aux requêtes",
        "Identifiants de corrélation reliant activité, requête et erreur",
        "Intégration avec l’outillage d’observabilité externe",
      ]}
      title="Santé & journaux"
    />
  );
}

export function AdminAnalyticsPlaceholderPage() {
  return (
    <PlannedAdminPage
      description="Les tendances de la plateforme, au-delà des compteurs du jour."
      icon={ChartLineUpIcon}
      planned={[
        "Croissance des comptes et des membres dans le temps",
        "Adoption des fonctionnalités et pression sur les quotas",
        "Mouvements de forfaits, conversion des essais et revenu configuré",
      ]}
      title="Statistiques"
    />
  );
}
