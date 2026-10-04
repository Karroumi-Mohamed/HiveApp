import type { AdminMe, MemberPermissions } from "@/api/contracts";

const permission = (feature: string, action: string) =>
  `platform.${feature}.${action}` as const;

export const adminPermissions = {
  customerCommunicationsRead: permission("customer_communications", "read"),
  customerCommunicationsChoose: permission(
    "customer_communications",
    "choose_recipients",
  ),
  customerCommunicationsCreate: permission("customer_communications", "create"),
  customerCommunicationsEdit: permission("customer_communications", "edit"),
  customerCommunicationsPublish: permission(
    "customer_communications",
    "publish",
  ),
  customerCommunicationsMarketing: permission(
    "customer_communications",
    "publish_marketing",
  ),
  customerCommunicationsCancel: permission("customer_communications", "cancel"),
  customerCommunicationsResults: permission(
    "customer_communications",
    "read_results",
  ),
  customerCommunicationsRetry: permission(
    "customer_communications",
    "retry_email",
  ),
  notificationsRead: permission("notifications", "read"),
  notificationsMarkRead: permission("notifications", "mark_read"),
  notificationsAcknowledge: permission("notifications", "acknowledge"),
  notificationsArchive: permission("notifications", "archive"),
  notificationsPreferences: permission("notifications", "preferences"),
  notificationsDelivery: permission("notifications", "read_delivery"),
  notificationsRetry: permission("notifications", "retry_delivery"),
  repricingPreview: permission("subscriptions", "preview_repricing"),
  repricingConfirm: permission("subscriptions", "confirm_repricing"),
  repricingList: permission("subscriptions", "list_repricing"),
  repricingRead: permission("subscriptions", "read_repricing"),
  repricingResults: permission("subscriptions", "read_repricing_results"),
  repricingIdentities: permission("subscriptions", "read_repricing_identities"),
  repricingCancel: permission("subscriptions", "cancel_repricing"),
  repricingRetry: permission("subscriptions", "retry_repricing"),
  repricingEmail: permission("subscriptions", "email_repricing"),
  accessOverview: permission("admin_users", "overview"),
  usersRead: permission("admin_users", "read"),
  usersReadDetail: permission("admin_users", "read_detail"),
  usersCreate: permission("admin_users", "create"),
  usersMutate: permission("admin_users", "toggle_active"),
  usersRename: permission("admin_users", "rename"),
  usersChangeEmail: permission("admin_users", "change_email"),
  usersSendEmailVerification: permission(
    "admin_users",
    "send_email_verification",
  ),
  usersReadPermissions: permission("admin_users", "read_permissions"),
  usersBulkSetActive: permission("admin_users", "bulk_set_active"),
  usersBulkAssignRole: permission("admin_users", "bulk_assign_role"),
  usersBulkResendActivation: permission(
    "admin_users",
    "bulk_resend_activation",
  ),
  usersResendActivation: permission("admin_users", "resend_activation"),
  usersTemporaryAccess: permission("admin_users", "generate_temporary_access"),
  usersAssignRole: permission("admin_users", "assign_role"),
  usersRemoveRole: permission("admin_users", "remove_role"),
  rolesRead: permission("roles", "read"),
  rolesReadDetail: permission("roles", "read_detail"),
  rolesCreate: permission("roles", "create"),
  rolesUpdate: permission("roles", "update"),
  rolesMutate: permission("roles", "toggle_active"),
  rolesBulkSetActive: permission("roles", "bulk_set_active"),
  rolesReadHolders: permission("roles", "read_holders"),
  rolesReadHistory: permission("roles", "read_history"),
  rolesListPresets: permission("roles", "list_presets"),
  rolesListGrantable: permission("roles", "list_grantable_permissions"),
  rolesPreviewImpact: permission("roles", "preview_impact"),
  rolesGrant: permission("roles", "grant_permission"),
  rolesRevoke: permission("roles", "revoke_permission"),
  rolesCreateFromPreset: permission("roles", "create_from_preset"),
  rolesDuplicate: permission("roles", "duplicate"),
  rolesReplacePermissions: permission("roles", "replace_permissions"),
  rolesTransitionStatus: permission("roles", "transition_status"),
  rolesDelete: permission("roles", "delete"),
  plansOverview: permission("plans", "overview"),
  plansList: permission("plans", "list"),
  plansListFamilies: permission("plans", "list_families"),
  plansListVersions: permission("plans", "list_versions"),
  plansCompareVersions: permission("plans", "compare_versions"),
  plansCompare: permission("plans", "compare_plans"),
  plansSelectPublicVersion: permission("plans", "select_public_version"),
  plansUpdateMetadata: permission("plans", "update_metadata"),
  plansPreviewApplication: permission("plans", "preview_version_application"),
  plansApplyVersion: permission("plans", "apply_version"),
  plansCreateApplication: permission("plans", "create_version_rollout"),
  plansListApplications: permission("plans", "list_version_rollouts"),
  plansReadApplication: permission("plans", "get_version_rollout"),
  plansApplicationResults: permission("plans", "list_version_rollout_results"),
  plansConfirmApplication: permission("plans", "confirm_version_rollout"),
  plansCancelApplication: permission("plans", "cancel_version_application"),
  plansRetryApplication: permission("plans", "retry_version_application"),
  plansApplicationIdentities: permission(
    "plans",
    "resolve_version_rollout_identities",
  ),
  plansRetryNotices: permission("plans", "retry_version_notices"),
  plansReadOperations: permission("plans", "read_plan_operations"),
  plansChoose: permission("plans", "choose_plans"),
  plansResolveChoices: permission("plans", "resolve_plan_choices"),
  plansResolveChoiceCodes: permission("plans", "resolve_plan_choice_codes"),
  plansReadDetail: permission("plans", "read_detail"),
  plansListFeatures: permission("plans", "list_features"),
  plansListSubscribers: permission("plans", "list_subscribers"),
  plansListFamilySubscribers: permission("plans", "list_family_subscribers"),
  plansReadVersionHistory: permission("plans", "read_version_history"),
  plansLookupSubscriberOwnerEmail: permission(
    "plans",
    "lookup_subscriber_owner_email",
  ),
  plansPreviewDelete: permission("plans", "preview_delete"),
  plansCreate: permission("plans", "create"),
  plansUpdate: permission("plans", "update"),
  plansDuplicate: permission("plans", "duplicate"),
  plansRevise: permission("plans", "revise"),
  plansPreviewActivation: permission("plans", "preview_plan_activation"),
  plansTransition: permission("plans", "transition_status"),
  plansDelete: permission("plans", "delete"),
  plansAssignFeature: permission("plans", "assign_feature"),
  plansUpdateFeature: permission("plans", "update_feature"),
  plansRemoveFeature: permission("plans", "remove_feature"),
  addOnsList: permission("plans", "list_add_ons"),
  addOnsReadOperations: permission("plans", "read_add_on_operations"),
  addOnsChoose: permission("plans", "choose_add_ons"),
  addOnsResolveChoices: permission("plans", "resolve_add_on_choices"),
  addOnsResolveChoiceCodes: permission("plans", "resolve_add_on_choice_codes"),
  addOnsReadDetail: permission("plans", "read_add_on"),
  addOnsCreate: permission("plans", "create_add_on"),
  addOnsUpdate: permission("plans", "update_add_on"),
  addOnsRevise: permission("plans", "revise_add_on"),
  addOnsPreviewActivation: permission("plans", "preview_add_on_activation"),
  addOnsTransition: permission("plans", "transition_add_on"),
  addOnsDelete: permission("plans", "delete_add_on"),
  addOnsAssignFeature: permission("plans", "assign_add_on_feature"),
  addOnsUpdateFeature: permission("plans", "update_add_on_feature"),
  addOnsRemoveFeature: permission("plans", "remove_add_on_feature"),
  quotaPackagesList: permission("plans", "list_quota_packages"),
  quotaPackagesReadOperations: permission(
    "plans",
    "read_quota_package_operations",
  ),
  quotaPackagesChoose: permission("plans", "choose_quota_packages"),
  quotaPackagesResolveChoices: permission(
    "plans",
    "resolve_quota_package_choices",
  ),
  quotaPackagesResolveChoiceCodes: permission(
    "plans",
    "resolve_quota_package_choice_codes",
  ),
  quotaPackagesReadDetail: permission("plans", "read_quota_package"),
  quotaPackagesCreate: permission("plans", "create_quota_package"),
  quotaPackagesUpdate: permission("plans", "update_quota_package"),
  quotaPackagesRevise: permission("plans", "revise_quota_package"),
  quotaPackagesCompare: permission("plans", "compare_quota_package"),
  quotaPackagesPreviewActivation: permission(
    "plans",
    "preview_quota_package_activation",
  ),
  quotaPackagesLifecycle: permission("plans", "lifecycle_quota_package"),
  quotaPackagesHistory: permission("plans", "read_quota_package_history"),
  quotaPackagesTransition: permission("plans", "transition_quota_package"),
  quotaPackagesDelete: permission("plans", "delete_quota_package"),
  priceBooksList: permission("price_books", "list"),
  priceBooksPreviewChange: permission("price_books", "preview_change"),
  priceBooksChange: permission("price_books", "change"),
  priceBooksRescheduleChange: permission("price_books", "reschedule_change"),
  priceBooksCancelChange: permission("price_books", "cancel_change"),
  priceBooksRead: permission("price_books", "read"),
  priceBooksReadHistory: permission("price_books", "read_history"),
  priceBooksCreate: permission("price_books", "create"),
  priceBooksUpdateDraft: permission("price_books", "update_draft"),
  priceBooksPreviewActivation: permission("price_books", "preview_activation"),
  priceBooksPreviewReplacement: permission(
    "price_books",
    "preview_replacement",
  ),
  priceBooksActivate: permission("price_books", "activate"),
  priceBooksPause: permission("price_books", "pause"),
  priceBooksReactivate: permission("price_books", "reactivate"),
  priceBooksRevise: permission("price_books", "revise"),
  priceBooksScheduleReplacement: permission(
    "price_books",
    "schedule_replacement",
  ),
  priceBooksArchive: permission("price_books", "archive"),
  priceBooksDeleteDraft: permission("price_books", "delete_draft"),
  subscriptionsRead: permission("subscriptions", "read"),
  subscriptionsSearch: permission("subscriptions", "search_accounts"),
  subscriptionsLookupAccountOwnerEmail: permission(
    "subscriptions",
    "lookup_account_owner_email",
  ),
  subscriptionsChooseAccounts: permission("subscriptions", "choose_accounts"),
  subscriptionsResolveAccountChoices: permission(
    "subscriptions",
    "resolve_account_choices",
  ),
  subscriptionsReadChanges: permission("subscriptions", "read_changes"),
  subscriptionsChooseChangeOptions: permission(
    "subscriptions",
    "choose_change_options",
  ),
  subscriptionsPreviewChange: permission("subscriptions", "preview_change"),
  subscriptionsApplyChange: permission("subscriptions", "apply_change"),
  subscriptionsCancelChange: permission("subscriptions", "cancel_change"),
  subscriptionsReadLifecycleActions: permission(
    "subscriptions",
    "read_lifecycle_actions",
  ),
  subscriptionsPreviewLifecycle: permission(
    "subscriptions",
    "preview_lifecycle",
  ),
  subscriptionsCancelAtPeriodEnd: permission(
    "subscriptions",
    "cancel_at_period_end",
  ),
  subscriptionsKeepRenewing: permission("subscriptions", "keep_renewing"),
  subscriptionsCancelImmediately: permission(
    "subscriptions",
    "cancel_immediately",
  ),
  subscriptionsSuspend: permission("subscriptions", "suspend"),
  subscriptionsRestore: permission("subscriptions", "restore"),
  subscriptionsExtendGrace: permission("subscriptions", "extend_grace"),
  subscriptionsReadLifecycleHistory: permission(
    "subscriptions",
    "read_lifecycle_history",
  ),
  subscriptionsConfirmCheckout: permission("subscriptions", "confirm_checkout"),
  subscriptionsPreviewChangeJob: permission(
    "subscriptions",
    "preview_change_job",
  ),
  subscriptionsConfirmChangeJob: permission(
    "subscriptions",
    "confirm_change_job",
  ),
  subscriptionsListChangeJobs: permission("subscriptions", "list_change_jobs"),
  subscriptionsReadChangeJob: permission("subscriptions", "read_change_job"),
  subscriptionsReadChangeJobResults: permission(
    "subscriptions",
    "read_change_job_results",
  ),
  subscriptionsReadChangeJobResultIdentities: permission(
    "subscriptions",
    "read_change_job_result_identities",
  ),
  subscriptionsCancelChangeJob: permission(
    "subscriptions",
    "cancel_change_job",
  ),
  subscriptionsRetryChangeJob: permission("subscriptions", "retry_change_job"),
  subscriptionsPreviewSpecialAgreement: permission(
    "subscriptions",
    "preview_special_agreement",
  ),
  subscriptionsCreateSpecialAgreement: permission(
    "subscriptions",
    "create_special_agreement",
  ),
  subscriptionsReadSpecialAgreements: permission(
    "subscriptions",
    "read_special_agreements",
  ),
  subscriptionsSearchSpecialAgreements: permission(
    "subscriptions",
    "search_special_agreements",
  ),
  subscriptionsReadSpecialAgreement: permission(
    "subscriptions",
    "read_special_agreement",
  ),
  subscriptionsCancelSpecialAgreement: permission(
    "subscriptions",
    "cancel_special_agreement",
  ),
  subscriptionsRetrySpecialAgreement: permission(
    "subscriptions",
    "retry_special_agreement",
  ),
  subscriptionsResolveSpecialAgreementManualReview: permission(
    "subscriptions",
    "resolve_special_agreement_manual_review",
  ),
  subscriptionsReadSpecialAgreementAnalytics: permission(
    "subscriptions",
    "read_special_agreement_analytics",
  ),
  billingListInvoices: permission("billing", "list_invoices"),
  billingReadInvoice: permission("billing", "read_invoice"),
  billingReadInvoiceDocument: permission("billing", "read_invoice_document"),
  billingReadAccountIdentity: permission("billing", "read_account_identity"),
  billingReadAccountTimeline: permission("billing", "read_account_timeline"),
  billingReadAccountProfile: permission(
    "billing",
    "read_account_billing_profile",
  ),
  billingUpdateAccountProfile: permission(
    "billing",
    "update_account_billing_profile",
  ),
  billingReadPayments: permission("billing", "read_payments"),
  billingReadPaymentReferences: permission(
    "billing",
    "read_payment_references",
  ),
  billingManualSettlement: permission("billing", "manual_settlement"),
  billingIssueCredit: permission("billing", "issue_credit"),
  billingPreviewRefund: permission("billing", "preview_refund"),
  billingCreateRefund: permission("billing", "create_refund"),
  billingRecordManualRefund: permission("billing", "record_manual_refund"),
  billingListReconciliation: permission("billing", "list_reconciliation"),
  billingListProviderEvents: permission("billing", "list_provider_events"),
  billingReconcileProviderEvent: permission(
    "billing",
    "reconcile_provider_event",
  ),
  billingPreviewChargeRetry: permission("billing", "preview_charge_retry"),
  billingRetryCharge: permission("billing", "retry_charge"),
  analyticsReadSummary: permission("analytics", "read_summary"),
  analyticsReadFinancialSeries: permission(
    "analytics",
    "read_financial_series",
  ),
  analyticsReadSubscriptionSeries: permission(
    "analytics",
    "read_subscription_series",
  ),
  analyticsReadOfferSeries: permission("analytics", "read_offer_series"),
  analyticsReadOperations: permission("analytics", "read_operations"),
  activitiesRead: permission("activities", "read"),
  activitiesReadPayload: permission("activities", "read_payload"),
  activitiesReadActorIdentity: permission("activities", "read_actor_identity"),
  activitiesReadAccountIdentity: permission(
    "activities",
    "read_account_identity",
  ),
  communicationsRead: permission("communications", "read"),
  communicationsReadRecipientIdentity: permission(
    "communications",
    "read_recipient_identity",
  ),
  communicationsReadFailureEvidence: permission(
    "communications",
    "read_failure_evidence",
  ),
  observabilityReadHealth: permission("observability", "read_health"),
  observabilityReadBacklogs: permission("observability", "read_backlogs"),
  observabilityReadLogAccess: permission("observability", "read_log_access"),
  registryRead: permission("registry", "read"),
  registryFeatureCatalog: permission("registry", "feature_catalog"),
  registryPermissionCatalog: permission("registry", "permission_catalog"),
  registryControlHistory: permission("registry", "control_history"),
  registrySync: permission("registry", "sync_status"),
  registryPublicVisibility: permission("registry", "update_public_visibility"),
  registryNewSales: permission("registry", "update_new_sales"),
  registryNewGrants: permission("registry", "update_new_grants"),
  registryRuntime: permission("registry", "update_emergency_runtime"),
  commercialInspectCompatibility: permission(
    "commercial_availability",
    "inspect_compatibility",
  ),
  commercialPreviewPlanPolicy: permission(
    "commercial_availability",
    "preview_plan_policy",
  ),
  commercialUpdatePlanPolicy: permission(
    "commercial_availability",
    "update_plan_policy",
  ),
  commercialPreviewAddOnVisibility: permission(
    "commercial_availability",
    "preview_add_on_visibility",
  ),
  commercialUpdateAddOnVisibility: permission(
    "commercial_availability",
    "update_add_on_visibility",
  ),
  commercialPreviewQuotaVisibility: permission(
    "commercial_availability",
    "preview_quota_visibility",
  ),
  commercialUpdateQuotaVisibility: permission(
    "commercial_availability",
    "update_quota_visibility",
  ),
  commercialReadHistory: permission("commercial_availability", "read_history"),
  commercialPoliciesList: permission("commercial_policies", "list"),
  commercialPoliciesRead: permission("commercial_policies", "read"),
  commercialPoliciesCreate: permission("commercial_policies", "create"),
  commercialPoliciesUpdateDraft: permission(
    "commercial_policies",
    "update_draft",
  ),
  commercialPoliciesDuplicate: permission("commercial_policies", "duplicate"),
  commercialPoliciesRevise: permission("commercial_policies", "revise"),
  commercialPoliciesCompare: permission("commercial_policies", "compare"),
  commercialPoliciesReadRevisions: permission(
    "commercial_policies",
    "read_revisions",
  ),
  commercialPoliciesReadHistory: permission(
    "commercial_policies",
    "read_history",
  ),
  commercialPoliciesReadActivations: permission(
    "commercial_policies",
    "read_activations",
  ),
  commercialPoliciesReadActivationAccounts: permission(
    "commercial_policies",
    "read_activation_accounts",
  ),
  commercialPoliciesPreviewAudience: permission(
    "commercial_policies",
    "preview_audience",
  ),
  commercialPoliciesPreviewActivation: permission(
    "commercial_policies",
    "preview_activation",
  ),
  commercialPoliciesActivate: permission("commercial_policies", "activate"),
  commercialPoliciesPause: permission("commercial_policies", "pause"),
  commercialPoliciesResume: permission("commercial_policies", "resume"),
  commercialPoliciesEnd: permission("commercial_policies", "end"),
  commercialPoliciesArchive: permission("commercial_policies", "archive"),
  commercialPoliciesDeleteDraft: permission(
    "commercial_policies",
    "delete_draft",
  ),
  commercialPoliciesReadOwner: permission("commercial_policies", "read_owner"),
  commercialPoliciesReassignOwner: permission(
    "commercial_policies",
    "reassign_owner",
  ),
  commercialPoliciesChooseAccounts: permission(
    "commercial_policies",
    "choose_accounts",
  ),
  commercialPoliciesResolveAccountChoices: permission(
    "commercial_policies",
    "resolve_account_choices",
  ),
  commercialPoliciesChooseSegments: permission(
    "commercial_policies",
    "choose_segments",
  ),
  commercialPoliciesResolveSegmentChoices: permission(
    "commercial_policies",
    "resolve_segment_choices",
  ),
  campaignsList: permission("campaigns", "list"),
  campaignsRead: permission("campaigns", "read"),
  campaignsReadOperations: permission("campaigns", "read_operations"),
  campaignsCreate: permission("campaigns", "create"),
  campaignsUpdate: permission("campaigns", "update"),
  campaignsReadEditableDefinition: permission(
    "campaigns",
    "read_editable_definition",
  ),
  campaignsDuplicate: permission("campaigns", "duplicate"),
  campaignsRevise: permission("campaigns", "revise"),
  campaignsCompare: permission("campaigns", "compare"),
  campaignsRevisions: permission("campaigns", "revisions"),
  campaignsHistory: permission("campaigns", "history"),
  campaignsPreviewSchedule: permission("campaigns", "preview_schedule"),
  campaignsSchedule: permission("campaigns", "schedule"),
  campaignsPause: permission("campaigns", "pause"),
  campaignsResume: permission("campaigns", "resume"),
  campaignsEnd: permission("campaigns", "end"),
  campaignsArchive: permission("campaigns", "archive"),
  campaignsDeleteDraft: permission("campaigns", "delete_draft"),
  campaignsOwner: permission("campaigns", "owner"),
  campaignsReassignOwner: permission("campaigns", "reassign_owner"),
  campaignsChooseOwners: permission("campaigns", "choose_owners"),
  campaignsResolveOwnerChoices: permission(
    "campaigns",
    "resolve_owner_choices",
  ),
  campaignsChooseAccounts: permission("campaigns", "choose_accounts"),
  campaignsResolveAccountChoices: permission(
    "campaigns",
    "resolve_account_choices",
  ),
  campaignsChooseSegments: permission("campaigns", "choose_segments"),
  campaignsResolveSegmentChoices: permission(
    "campaigns",
    "resolve_segment_choices",
  ),
  campaignsReadAudience: permission("campaigns", "read_audience"),
  campaignsReadAudienceIdentities: permission(
    "campaigns",
    "read_audience_identities",
  ),
  offersList: permission("offers", "list"),
  offersRead: permission("offers", "read"),
  offersReadOperations: permission("offers", "read_operations"),
  offersReadEditableDefinition: permission(
    "offers",
    "read_editable_definition",
  ),
  offersPreviewCreateDefinition: permission(
    "offers",
    "preview_create_definition",
  ),
  offersPreviewUpdateDefinition: permission(
    "offers",
    "preview_update_definition",
  ),
  offersCreate: permission("offers", "create"),
  offersUpdate: permission("offers", "update"),
  offersDuplicate: permission("offers", "duplicate"),
  offersRevise: permission("offers", "revise"),
  offersRevisions: permission("offers", "revisions"),
  offersCompare: permission("offers", "compare"),
  offersPreviewPublish: permission("offers", "preview_publish"),
  offersPublish: permission("offers", "publish"),
  offersRetire: permission("offers", "retire"),
  offersRestore: permission("offers", "restore"),
  offersArchive: permission("offers", "archive"),
  offersDelete: permission("offers", "delete"),
  offersReadOwner: permission("offers", "read_owner"),
  offersReassignOwner: permission("offers", "reassign_owner"),
  offersReadStats: permission("offers", "read_stats"),
  offersReadRedemptions: permission("offers", "read_redemptions"),
  offersReadRedemptionDetail: permission("offers", "read_redemption_detail"),
  offersReadRedemptionIdentities: permission(
    "offers",
    "read_redemption_identities",
  ),
  offersHistory: permission("offers", "history"),
  offersChooseAccounts: permission("offers", "choose_accounts"),
  offersResolveAccountChoices: permission("offers", "resolve_account_choices"),
  offersChooseProducts: permission("offers", "choose_products"),
  offersResolveProductChoices: permission("offers", "resolve_product_choices"),
  offersChooseQuotaResources: permission("offers", "choose_quota_resources"),
  offersChooseOwners: permission("offers", "choose_owners"),
  offersResolveOwnerChoices: permission("offers", "resolve_owner_choices"),
  offersChooseCampaigns: permission("offers", "choose_campaigns"),
  offersResolveCampaignChoices: permission(
    "offers",
    "resolve_campaign_choices",
  ),
  offersPreviewForAccount: permission("offers", "preview_for_account"),
  offersApplyForAccount: permission("offers", "apply_for_account"),
  segmentsList: permission("segments", "list"),
  segmentsRead: permission("segments", "read_detail"),
  segmentsCreate: permission("segments", "create"),
  segmentsUpdateDraft: permission("segments", "update_draft"),
  segmentsDuplicate: permission("segments", "duplicate"),
  segmentsRevise: permission("segments", "revise"),
  segmentsCompare: permission("segments", "compare"),
  segmentsReadRevisions: permission("segments", "read_revisions"),
  segmentsReadHistory: permission("segments", "read_history"),
  segmentsCount: permission("segments", "count"),
  segmentsPreview: permission("segments", "preview"),
  segmentsReadSampleIdentities: permission(
    "segments",
    "read_sample_identities",
  ),
  segmentsActivate: permission("segments", "activate"),
  segmentsArchive: permission("segments", "archive"),
  segmentsDeleteDraft: permission("segments", "delete_draft"),
  segmentsReadActivations: permission("segments", "read_activations"),
  segmentsReadActivationAudience: permission(
    "segments",
    "read_activation_audience",
  ),
  segmentsReadActivationIdentities: permission(
    "segments",
    "read_activation_identities",
  ),
  segmentsReadOwner: permission("segments", "read_owner"),
  segmentsReassignOwner: permission("segments", "reassign_owner"),
  segmentsChooseAccounts: permission("segments", "choose_accounts"),
  segmentsResolveAccountChoices: permission(
    "segments",
    "resolve_account_choices",
  ),
} as const;

export const clientPermissions = {
  communicationsRead: permission("workspace", "read_communications"),
  communicationsMarkRead: permission("workspace", "mark_communication_read"),
  communicationsAcknowledge: permission("workspace", "acknowledge_warning"),
  communicationsArchive: permission("workspace", "archive_communication"),
  notificationsChoose: permission(
    "workspace",
    "choose_notification_recipients",
  ),
  notificationsSend: permission("workspace", "send_notification"),
  notificationsSent: permission("workspace", "read_sent_notifications"),
  notificationsPreferences: permission("workspace", "notification_preferences"),
  communicationsPreferences: permission(
    "workspace",
    "communication_preferences",
  ),
  workspaceRead: permission("workspace", "read"),
  workspaceDelete: permission("workspace", "delete"),
  companiesRead: permission("company", "read_all"),
  companyDetailRead: permission("company", "read_single"),
  companiesCreate: permission("company", "create"),
  companiesUpdate: permission("company", "update"),
  companiesDelete: permission("company", "delete"),
  companiesReactivate: permission("company", "reactivate"),
  organizationRead: permission("organization", "list_groups"),
  organizationCreate: permission("organization", "create"),
  organizationUpdate: permission("organization", "update"),
  organizationMove: permission("organization", "move"),
  organizationReorder: permission("organization", "reorder"),
  organizationArchive: permission("organization", "archive"),
  organizationRestore: permission("organization", "restore"),
  organizationDelete: permission("organization", "delete"),
  organizationListMemberships: permission("organization", "list_memberships"),
  organizationListMemberPlacements: permission(
    "organization",
    "list_member_placements",
  ),
  organizationPutMember: permission("organization", "put_membership"),
  organizationRemoveMember: permission("organization", "remove_membership"),
  organizationCreateTemplate: permission("organization", "create_template"),
  organizationListTemplates: permission("organization", "list_templates"),
  organizationPreviewTemplate: permission("organization", "preview_template"),
  organizationInstantiateTemplate: permission(
    "organization",
    "instantiate_template",
  ),
  organizationDeleteTemplate: permission("organization", "delete_template"),
  membersRead: permission("staff", "read"),
  membersReadAccess: permission("staff", "read_access"),
  membersReadAuthorization: permission("staff", "read_authorization"),
  membersReadOverrides: permission("staff", "read_overrides"),
  membersCreate: permission("staff", "create"),
  membersUpdate: permission("staff", "update"),
  membersDeactivate: permission("staff", "delete"),
  membersReactivate: permission("staff", "reactivate"),
  membersAssignRole: permission("staff", "assign_role"),
  membersRemoveRole: permission("staff", "remove_role"),
  membersGrantPermission: permission("staff", "grant_permission"),
  membersRevokePermission: permission("staff", "revoke_permission"),
  membersRegenerateAccess: permission("staff", "regenerate_access"),
  membersResetAccess: permission("staff", "reset_access"),
  membersUnlockAccess: permission("staff", "unlock_access"),
  rolesRead: permission("rbac", "view"),
  roleDetailRead: permission("rbac", "read"),
  rolesImpact: permission("rbac", "impact"),
  rolesPermissionCatalog: permission("rbac", "permission_catalog"),
  rolesCreate: permission("rbac", "create"),
  rolesUpdate: permission("rbac", "update"),
  rolesDelete: permission("rbac", "delete"),
  rolesGrant: permission("rbac", "grant"),
  rolesRevoke: permission("rbac", "revoke"),
  rolesActivate: permission("rbac", "activate"),
  rolesDeactivate: permission("rbac", "deactivate"),
  rolesArchive: permission("rbac", "archive"),
  rolesDuplicate: permission("rbac", "duplicate"),
  collaborationsRead: permission("b2b", "view"),
  collaborationDetailRead: permission("b2b", "read_detail"),
  incomingCollaborationsRead: permission("b2b", "view_incoming"),
  collaborationsRequest: permission("b2b", "request"),
  collaborationsResolveShareCode: permission("b2b", "resolve_share_code"),
  collaborationsAccept: permission("b2b", "accept"),
  collaborationsReject: permission("b2b", "reject"),
  collaborationsCancelRequest: permission("b2b", "cancel_request"),
  collaborationsSuspend: permission("b2b", "suspend"),
  collaborationsResume: permission("b2b", "resume"),
  collaborationsRevoke: permission("b2b", "revoke"),
  collaborationsGrant: permission("b2b", "grant_permission"),
  collaborationsRevokeGrant: permission("b2b", "revoke_permission"),
  collaborationsReadPermissions: permission("b2b", "read_permissions"),
  collaborationsPermissionCatalog: permission("b2b", "permission_catalog"),
  collaborationsReadShareCode: permission("b2b", "read_share_code"),
  collaborationsManageShareCode: permission("b2b", "manage_share_code"),
  collaborationsRegenerateShareCode: permission("b2b", "regenerate_share_code"),
  subscriptionRead: permission("subscription", "read"),
  subscriptionReadPriceNotices: permission(
    "subscription",
    "read_price_notices",
  ),
  subscriptionReadContentNotices: permission(
    "subscription",
    "read_content_notices",
  ),
  subscriptionMarkContentNoticeRead: permission(
    "subscription",
    "mark_content_notice_read",
  ),
  subscriptionMarkPriceNoticeRead: permission(
    "subscription",
    "mark_price_notice_read",
  ),
  subscriptionReadSpecialAgreements: permission(
    "subscription",
    "read_special_agreements",
  ),
  subscriptionCatalog: permission("subscription", "catalog"),
  subscriptionPreview: permission("subscription", "preview"),
  subscriptionApply: permission("subscription", "apply"),
  subscriptionReadChanges: permission("subscription", "read_changes"),
  subscriptionCancel: permission("subscription", "cancel_change"),
  subscriptionListInvoices: permission("subscription", "list_invoices"),
  subscriptionReadInvoice: permission("subscription", "read_invoice"),
  subscriptionReadInvoiceDocument: permission(
    "subscription",
    "read_invoice_document",
  ),
  subscriptionReadFinancialTimeline: permission(
    "subscription",
    "read_financial_timeline",
  ),
  subscriptionReadBillingProfile: permission(
    "subscription",
    "read_billing_profile",
  ),
  subscriptionUpdateBillingProfile: permission(
    "subscription",
    "update_billing_profile",
  ),
  subscriptionOfferCatalog: permission("subscription", "offer_catalog"),
  subscriptionOfferDetail: permission("subscription", "offer_detail"),
  subscriptionOfferCode: permission("subscription", "offer_code"),
  subscriptionOfferPreview: permission("subscription", "offer_preview"),
  subscriptionOfferAccept: permission("subscription", "offer_accept"),
  subscriptionOfferHistory: permission("subscription", "offer_history"),
  subscriptionOfferHistoryDetail: permission(
    "subscription",
    "offer_history_detail",
  ),
} as const;

/**
 * The subscription screen contains three independently guarded read surfaces. Keeping this list
 * shared prevents the route and navigation from silently making `subscription.read` a prerequisite
 * for the separately authorized catalog and change-history endpoints.
 */
export const clientSubscriptionSurfacePermissions = [
  clientPermissions.subscriptionReadContentNotices,
  clientPermissions.subscriptionReadPriceNotices,
  clientPermissions.subscriptionRead,
  clientPermissions.subscriptionReadSpecialAgreements,
  clientPermissions.subscriptionCatalog,
  clientPermissions.subscriptionReadChanges,
  clientPermissions.subscriptionListInvoices,
  clientPermissions.subscriptionReadFinancialTimeline,
  clientPermissions.subscriptionReadBillingProfile,
] as const;

/** Receiving never depends on the permission to send or inspect sent messages. */
export const clientNotificationSurfacePermissions = [
  clientPermissions.communicationsRead,
] as const;
export const clientCommunicationSurfacePermissions = [
  clientPermissions.notificationsSend,
  clientPermissions.notificationsSent,
] as const;

export const adminBillingSurfacePermissions = [
  adminPermissions.billingListInvoices,
  adminPermissions.billingListReconciliation,
  adminPermissions.billingListProviderEvents,
] as const;

export const adminAnalyticsSurfacePermissions = [
  adminPermissions.analyticsReadSummary,
  adminPermissions.analyticsReadFinancialSeries,
  adminPermissions.analyticsReadSubscriptionSeries,
  adminPermissions.analyticsReadOfferSeries,
  adminPermissions.analyticsReadOperations,
] as const;

// Activity evidence enrichments are supplementary: every corresponding backend endpoint also
// requires the base metadata permission. They must therefore never make the page reachable alone.
export const adminActivitiesSurfacePermissions = [
  adminPermissions.activitiesRead,
] as const;

// Recipient and failure evidence follow the same conjunctive contract as activity enrichments.
export const adminCommunicationsSurfacePermissions = [
  adminPermissions.communicationsRead,
  adminPermissions.customerCommunicationsRead,
  adminPermissions.notificationsDelivery,
] as const;

export const adminObservabilitySurfacePermissions = [
  adminPermissions.observabilityReadHealth,
  adminPermissions.observabilityReadBacklogs,
  adminPermissions.observabilityReadLogAccess,
] as const;

export const adminInvoiceDetailSurfacePermissions = [
  adminPermissions.billingReadInvoice,
  adminPermissions.billingReadInvoiceDocument,
  adminPermissions.billingReadAccountIdentity,
  adminPermissions.billingReadPayments,
  adminPermissions.billingManualSettlement,
  adminPermissions.billingPreviewChargeRetry,
] as const;

/**
 * Account subscription details are three independently readable operator surfaces. An operator
 * who may prepare a reviewed change or inspect its history must not also need the broader current
 * subscription read permission merely to reach the route.
 */
export const adminSubscriptionDetailSurfacePermissions = [
  adminPermissions.subscriptionsRead,
  adminPermissions.subscriptionsChooseChangeOptions,
  adminPermissions.subscriptionsReadChanges,
  adminPermissions.subscriptionsReadLifecycleActions,
  adminPermissions.subscriptionsReadLifecycleHistory,
  adminPermissions.billingReadAccountTimeline,
  adminPermissions.billingReadAccountProfile,
  adminPermissions.subscriptionsReadSpecialAgreements,
  adminPermissions.subscriptionsReadSpecialAgreement,
  adminPermissions.subscriptionsPreviewSpecialAgreement,
] as const;

export const adminSubscriptionJobDetailSurfacePermissions = [
  adminPermissions.subscriptionsReadChangeJob,
  adminPermissions.subscriptionsReadChangeJobResults,
] as const;

export const adminPriceBookDetailSurfacePermissions = [
  adminPermissions.priceBooksRead,
  adminPermissions.priceBooksReadHistory,
] as const;

export const adminCommercialPolicyDetailSurfacePermissions = [
  adminPermissions.commercialPoliciesRead,
  adminPermissions.commercialPoliciesReadRevisions,
  adminPermissions.commercialPoliciesCompare,
  adminPermissions.commercialPoliciesReadHistory,
  adminPermissions.commercialPoliciesReadActivations,
  adminPermissions.commercialPoliciesReadActivationAccounts,
  adminPermissions.commercialPoliciesPreviewAudience,
  adminPermissions.commercialPoliciesReadOwner,
] as const;

export const adminCommercialSegmentDetailSurfacePermissions = [
  adminPermissions.segmentsRead,
  adminPermissions.segmentsCount,
  adminPermissions.segmentsPreview,
  adminPermissions.segmentsReadSampleIdentities,
  adminPermissions.segmentsReadRevisions,
  adminPermissions.segmentsCompare,
  adminPermissions.segmentsReadHistory,
  adminPermissions.segmentsReadActivations,
  adminPermissions.segmentsReadActivationAudience,
  adminPermissions.segmentsReadActivationIdentities,
  adminPermissions.segmentsReadOwner,
] as const;

export const adminCommercialCampaignDetailSurfacePermissions = [
  adminPermissions.campaignsRead,
  adminPermissions.campaignsReadOperations,
  adminPermissions.campaignsCompare,
  adminPermissions.campaignsRevisions,
  adminPermissions.campaignsHistory,
  adminPermissions.campaignsPreviewSchedule,
  adminPermissions.campaignsReadAudience,
  adminPermissions.campaignsReadAudienceIdentities,
  adminPermissions.campaignsOwner,
] as const;

/** Editing uses the narrow draft-definition contract, never the broad Campaign detail contract. */
export const adminCommercialCampaignEditPermissions = [
  adminPermissions.campaignsUpdate,
  adminPermissions.campaignsReadEditableDefinition,
] as const;

export const adminOfferDetailSurfacePermissions = [
  adminPermissions.offersRead,
  adminPermissions.offersReadOperations,
  adminPermissions.offersRevisions,
  adminPermissions.offersCompare,
  adminPermissions.offersHistory,
  adminPermissions.offersPreviewPublish,
  adminPermissions.offersReadOwner,
  adminPermissions.offersReadStats,
  adminPermissions.offersReadRedemptions,
  adminPermissions.offersPreviewForAccount,
] as const;

export const adminOfferEditPermissions = [
  adminPermissions.offersUpdate,
  adminPermissions.offersReadEditableDefinition,
  adminPermissions.offersPreviewUpdateDefinition,
] as const;

export const clientOfferSurfacePermissions = [
  clientPermissions.subscriptionOfferCatalog,
  clientPermissions.subscriptionOfferCode,
  clientPermissions.subscriptionOfferHistory,
] as const;

/** Each overview card family is independently readable, including registry sync on its own. */
export const adminOverviewSurfacePermissions = [
  adminPermissions.accessOverview,
  adminPermissions.plansOverview,
  adminPermissions.registrySync,
] as const;

export function adminProfileCan(
  profile: Pick<AdminMe, "isSuperAdmin" | "permissions"> | null | undefined,
  required: string,
) {
  return Boolean(
    profile?.isSuperAdmin || profile?.permissions.includes(required),
  );
}

export function clientProfileCan(
  profile:
    Pick<MemberPermissions, "isOwner" | "permissions"> | null | undefined,
  required: string,
) {
  return Boolean(profile?.isOwner || profile?.permissions.includes(required));
}
