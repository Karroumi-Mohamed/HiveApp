import {
  BuildingsIcon,
  ChartLineUpIcon,
  EnvelopeSimpleIcon,
  HandshakeIcon,
  HeartbeatIcon,
  PulseIcon,
  ReceiptIcon,
  StackIcon,
} from "@phosphor-icons/react";
import { PlaceholderPage } from "@/components/patterns/placeholder-page";

/**
 * The planned admin sections, navigable before they are built. Each page states what was decided
 * for the section, so the navigation carries the product roadmap instead of hiding it.
 */

export function AdminRoleTemplatesPlaceholderPage() {
  return (
    <PlaceholderPage
      description="Rôles de départ publiés par HiveApp, que les clients adoptent dans leur espace."
      icon={StackIcon}
      planned={[
        "Créer et publier des modèles versionnés, permission par permission",
        "Visibilité calculée selon le forfait et les add-ons de chaque compte",
        "Adoption par les clients avec comparaison des versions",
        "Statistiques d’adoption par modèle et par version",
      ]}
      title="Modèles de rôles"
    />
  );
}

export function AdminAccountsPlaceholderPage() {
  return (
    <PlaceholderPage
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
    <PlaceholderPage
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

export function AdminBillingPlaceholderPage() {
  return (
    <PlaceholderPage
      description="Factures, paiements et remboursements des abonnements."
      icon={ReceiptIcon}
      planned={[
        "Factures émises et leur statut de règlement",
        "Paiements échoués et relances",
        "Remboursements et avoirs, avec justification",
      ]}
      title="Facturation"
    />
  );
}

export function AdminActivitiesPlaceholderPage() {
  return (
    <PlaceholderPage
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
    <PlaceholderPage
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
    <PlaceholderPage
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
    <PlaceholderPage
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
