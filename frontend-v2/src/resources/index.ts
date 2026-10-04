import { billingResources } from "./billing";
import { catalogResources } from "./catalog";
import { commercialResources } from "./commercial";
import { settingsResources } from "./settings";
import { operationResources } from "./operations";
import type { Resource } from "./types";
export const resources: Record<string, Resource> = Object.fromEntries(
  [
    ...catalogResources,
    ...billingResources,
    ...commercialResources,
    ...settingsResources,
    ...operationResources,
  ].map((r) => [r.key, r]),
);

const lifecycleFilters: Record<string, string[]> = {
  plans: ["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"],
  addons: ["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"],
  capacity: ["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"],
  prices: ["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"],
  campaigns: ["DRAFT", "SCHEDULED", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"],
  segments: ["DRAFT", "ACTIVE", "ARCHIVED"],
  policies: ["DRAFT", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"],
  offers: ["DRAFT", "PUBLISHED", "RETIRED", "ARCHIVED"],
  roles: ["ACTIVE", "INACTIVE", "ARCHIVED"],
  jobs: [
    "PREVIEWED",
    "QUEUED",
    "SCHEDULED",
    "RUNNING",
    "COMPLETED",
    "COMPLETED_WITH_ERRORS",
    "CANCELLED",
  ],
  invoices: ["OPEN", "SETTLED", "SETTLED_ZERO", "CANCELLED"],
  "notification-events": ["PENDING", "DELIVERED", "FAILED"],
  "notification-emails": [
    "PENDING",
    "SENDING",
    "SENT",
    "SUPPRESSED",
    "FAILED",
    "CANCELLED",
  ],
};
for (const [key, values] of Object.entries(lifecycleFilters))
  resources[key]!.filters = [
    { key: "status", label: "Statuses", values },
    ...(resources[key]!.filters || []).filter(
      (filter) => filter.key !== "status",
    ),
  ];
for (const key of [
  "jobs",
  "messages",
  "repricing",
  "rollouts",
  "inbox",
  "provider-events",
  "provider-commands",
  "notification-events",
  "notification-emails",
  "preferences",
  "attention",
])
  resources[key]!.searchable = false;

const planGroups: Record<string, { key: string; label: string }> = {
  features: { key: "features", label: "Features" },
  compatibility: { key: "features", label: "Features" },
  subscribers: { key: "subscribers", label: "Subscribers" },
  "family-subscribers": { key: "subscribers", label: "Subscribers" },
  revisions: { key: "revisions", label: "Versions" },
  rollouts: { key: "revisions", label: "Versions" },
  history: { key: "history", label: "History" },
  "version-history": { key: "history", label: "History" },
};
for (const section of resources.plans!.sections || []) {
  if (planGroups[section.key]) section.group = planGroups[section.key];
  if (section.key === "subscribers") section.label = "Current revision";
  if (section.key === "family-subscribers") section.label = "All revisions";
}
for (const key of ["plans", "addons", "capacity"])
  resources[key]!.createRequirements = [resources.prices!.createPermission!];
