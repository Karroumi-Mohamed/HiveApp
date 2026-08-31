import type {
  Account,
  AccountBillingProfile,
  AccountBillingProfileInput,
  AuthResponse,
  BillingFinancialTimelineEntry,
  BillingInvoiceDocument,
  BillingInvoiceRow,
  BillingTimelineEntryType,
  ClientBillingInvoiceDetail,
  ClientPlanCatalog,
  ClientSubscriptionChangeApplyResponse,
  ClientSubscriptionChangePreview,
  Collaboration,
  CollaborationGrant,
  Company,
  CompanyShareCode,
  GroupMembership,
  GroupTemplate,
  GroupTemplatePreview,
  Member,
  MemberAccess,
  MemberAuthorization,
  MemberCreation,
  OrganizationGroup,
  PageResponse,
  PermissionOverride,
  PermissionPickerCatalog,
  Role,
  RoleImpact,
  Subscription,
  SubscriptionChangeApplyInput,
  SubscriptionChangeInput,
  SubscriptionChangeOperation,
  UUID,
} from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";

function context() {
  return {
    companyId: window.localStorage.getItem("hiveapp-selected-company"),
    isB2B: window.localStorage.getItem("hiveapp-b2b-mode") === "true",
  };
}

function client<T>(path: string, options: Parameters<typeof apiRequest<T>>[1] = {}) {
  return apiRequest<T>(path, { audience: "client", context: context(), ...options });
}

export const clientApi = {
  account: () => client<Account>("/api/v1/accounts/me"),
  deactivateAccount: () => client<void>("/api/v1/accounts/me", { method: "DELETE" }),
  companies: () => client<Company[]>("/api/v1/companies"),
  company: (id: UUID) => client<Company>(`/api/v1/companies/${id}`),
  createCompany: (input: unknown) => client<Company>("/api/v1/companies", { method: "POST", body: jsonBody(input) }),
  updateCompany: (id: UUID, input: unknown) =>
    client<Company>(`/api/v1/companies/${id}`, { method: "PATCH", body: jsonBody(input) }),
  deactivateCompany: (id: UUID) => client<void>(`/api/v1/companies/${id}`, { method: "DELETE" }),
  reactivateCompany: (id: UUID) => client<Company>(`/api/v1/companies/${id}/reactivate`, { method: "POST" }),

  groups: (companyId: UUID) => client<OrganizationGroup[]>("/api/v1/organization/groups", { query: { companyId } }),
  createGroup: (input: unknown) =>
    client<OrganizationGroup>("/api/v1/organization/groups", { method: "POST", body: jsonBody(input) }),
  updateGroup: (id: UUID, input: unknown) =>
    client<OrganizationGroup>(`/api/v1/organization/groups/${id}`, { method: "PATCH", body: jsonBody(input) }),
  moveGroup: (id: UUID, parentId: UUID | null) =>
    client<OrganizationGroup>(`/api/v1/organization/groups/${id}/move`, {
      method: "POST",
      body: jsonBody({ parentId }),
    }),
  reorderGroups: (companyId: UUID, parentId: UUID | null, orderedGroupIds: UUID[]) =>
    client<void>("/api/v1/organization/groups/reorder", {
      method: "PUT",
      body: jsonBody({ companyId, parentId, orderedGroupIds }),
    }),
  archiveGroup: (id: UUID) => client<void>(`/api/v1/organization/groups/${id}/archive`, { method: "POST" }),
  restoreGroup: (id: UUID) => client<void>(`/api/v1/organization/groups/${id}/restore`, { method: "POST" }),
  deleteGroup: (id: UUID) => client<void>(`/api/v1/organization/groups/${id}`, { method: "DELETE" }),
  groupMembers: (id: UUID, includeDescendants = false) =>
    client<GroupMembership[]>(`/api/v1/organization/groups/${id}/members`, { query: { includeDescendants } }),
  putGroupMember: (groupId: UUID, memberId: UUID, positionTitle: string | null) =>
    client<GroupMembership>(`/api/v1/organization/groups/${groupId}/members/${memberId}`, {
      method: "PUT",
      body: jsonBody({ positionTitle }),
    }),
  removeGroupMember: (groupId: UUID, memberId: UUID) =>
    client<void>(`/api/v1/organization/groups/${groupId}/members/${memberId}`, { method: "DELETE" }),
  memberPlacements: (memberId: UUID, companyId: UUID) =>
    client<GroupMembership[]>(`/api/v1/organization/members/${memberId}/placements`, { query: { companyId } }),
  templates: (companyId: UUID) => client<GroupTemplate[]>("/api/v1/organization/templates", { query: { companyId } }),
  createTemplate: (input: unknown) =>
    client<GroupTemplate>("/api/v1/organization/templates", { method: "POST", body: jsonBody(input) }),
  previewTemplate: (id: UUID, companyId: UUID, parentId?: UUID | null) =>
    client<GroupTemplatePreview>(`/api/v1/organization/templates/${id}/preview`, { query: { companyId, parentId } }),
  instantiateTemplate: (id: UUID, companyId: UUID, parentId?: UUID | null) =>
    client<OrganizationGroup[]>(`/api/v1/organization/templates/${id}/instantiate`, {
      method: "POST",
      body: jsonBody({ companyId, parentId }),
    }),
  deleteTemplate: (id: UUID) => client<void>(`/api/v1/organization/templates/${id}`, { method: "DELETE" }),

  members: () => client<Member[]>("/api/v1/members"),
  createMember: (input: unknown) =>
    client<MemberCreation>("/api/v1/members", { method: "POST", body: jsonBody(input) }),
  updateMember: (id: UUID, displayName: string) =>
    client<Member>(`/api/v1/members/${id}`, { method: "PATCH", body: jsonBody({ displayName }) }),
  deactivateMember: (id: UUID, reason: string) =>
    client<void>(`/api/v1/members/${id}/deactivate`, { method: "POST", body: jsonBody({ reason }) }),
  reactivateMember: (id: UUID, reason: string) =>
    client<Member>(`/api/v1/members/${id}/reactivate`, { method: "POST", body: jsonBody({ reason }) }),
  memberAuthorization: (id: UUID) => client<MemberAuthorization>(`/api/v1/members/${id}/authorization`),
  memberAccess: (id: UUID) => client<Omit<MemberAccess, "temporaryPassword">>(`/api/v1/members/${id}/access`),
  regenerateMemberAccess: (id: UUID) =>
    client<MemberAccess>(`/api/v1/members/${id}/access/regenerate`, { method: "POST" }),
  resetMemberAccess: (id: UUID) => client<MemberAccess>(`/api/v1/members/${id}/access/reset`, { method: "POST" }),
  unlockMemberAccess: (id: UUID) => client<void>(`/api/v1/members/${id}/access/unlock`, { method: "POST" }),
  assignMemberRole: (memberId: UUID, input: unknown) =>
    client<void>(`/api/v1/members/${memberId}/roles`, { method: "POST", body: jsonBody(input) }),
  removeMemberRole: (memberId: UUID, roleId: UUID, scope: string, companyId?: UUID | null) =>
    client<void>(`/api/v1/members/${memberId}/roles/${roleId}`, { method: "DELETE", query: { scope, companyId } }),
  memberOverrides: (memberId: UUID, scope: string, companyId?: UUID | null) =>
    client<PermissionOverride[]>(`/api/v1/members/${memberId}/permissions`, { query: { scope, companyId } }),
  grantMemberOverride: (memberId: UUID, input: unknown) =>
    client<void>(`/api/v1/members/${memberId}/permissions`, { method: "POST", body: jsonBody(input) }),
  revokeMemberOverride: (memberId: UUID, permissionCode: string, scope: string, companyId?: UUID | null) =>
    client<void>(`/api/v1/members/${memberId}/permissions/${encodeURIComponent(permissionCode)}`, {
      method: "DELETE",
      query: { scope, companyId },
    }),

  roles: () => client<Role[]>("/api/v1/roles"),
  role: (id: UUID) => client<Role>(`/api/v1/roles/${id}`),
  roleCatalog: (roleId?: UUID) =>
    client<PermissionPickerCatalog>("/api/v1/roles/permission-catalog", { query: { roleId } }),
  createRole: (input: unknown) => client<Role>("/api/v1/roles", { method: "POST", body: jsonBody(input) }),
  updateRole: (id: UUID, input: unknown) =>
    client<Role>(`/api/v1/roles/${id}`, { method: "PUT", body: jsonBody(input) }),
  duplicateRole: (id: UUID, input: unknown) =>
    client<Role>(`/api/v1/roles/${id}/duplicate`, { method: "POST", body: jsonBody(input) }),
  deleteRole: (id: UUID) => client<void>(`/api/v1/roles/${id}`, { method: "DELETE" }),
  roleImpact: (id: UUID, changeType: string, permissionCode?: string) =>
    client<RoleImpact>(`/api/v1/roles/${id}/impact`, { query: { changeType, permissionCode } }),
  transitionRole: (id: UUID, action: "activate" | "deactivate" | "archive", input?: unknown) =>
    client<Role>(`/api/v1/roles/${id}/${action}`, { method: "POST", body: input ? jsonBody(input) : undefined }),
  addRolePermission: (
    id: UUID,
    permissionCode: string,
    registryVersion: string,
    expectedVersion?: number,
    confirmedAssignmentCount?: number,
  ) =>
    client<Role>(`/api/v1/roles/${id}/permissions`, {
      method: "POST",
      query: { permissionCode, registryVersion, expectedVersion, confirmedAssignmentCount },
    }),
  removeRolePermission: (
    id: UUID,
    permissionCode: string,
    expectedVersion?: number,
    confirmedAssignmentCount?: number,
  ) =>
    client<Role>(`/api/v1/roles/${id}/permissions/${encodeURIComponent(permissionCode)}`, {
      method: "DELETE",
      query: { expectedVersion, confirmedAssignmentCount },
    }),

  collaborations: () => client<Collaboration[]>("/api/v1/collaborations"),
  incomingCollaborations: () => client<Collaboration[]>("/api/v1/collaborations/incoming"),
  collaboration: (id: UUID) => client<Collaboration>(`/api/v1/collaborations/${id}`),
  initiateCollaboration: (input: unknown) =>
    client<Collaboration>("/api/v1/collaborations/initiate", { method: "POST", body: jsonBody(input) }),
  resolveShareCode: (shareCode: string) =>
    client<{ providerAccountName: string; companyName: string; companyCountry: string | null }>(
      "/api/v1/collaborations/share-code/resolve",
      { method: "POST", body: jsonBody({ shareCode }) },
    ),
  shareCode: (companyId: UUID) => client<CompanyShareCode>(`/api/v1/collaborations/companies/${companyId}/share-code`),
  regenerateShareCode: (companyId: UUID) =>
    client<CompanyShareCode>(`/api/v1/collaborations/companies/${companyId}/share-code`, { method: "POST" }),
  setShareCodeEnabled: (companyId: UUID, enabled: boolean) =>
    client<CompanyShareCode>(`/api/v1/collaborations/companies/${companyId}/share-code`, {
      method: "PATCH",
      query: { enabled },
    }),
  transitionCollaboration: (
    id: UUID,
    action: "accept" | "reject" | "cancel-request" | "suspend" | "resume",
    input: unknown,
  ) => client<Collaboration>(`/api/v1/collaborations/${id}/${action}`, { method: "PATCH", body: jsonBody(input) }),
  revokeCollaboration: (id: UUID, input: unknown) =>
    client<Collaboration>(`/api/v1/collaborations/${id}`, { method: "DELETE", body: jsonBody(input) }),
  collaborationPermissions: (id: UUID) => client<CollaborationGrant[]>(`/api/v1/collaborations/${id}/permissions`),
  collaborationCatalog: (id: UUID) =>
    client<PermissionPickerCatalog>(`/api/v1/collaborations/${id}/permission-catalog`),
  grantCollaborationPermission: (id: UUID, permissionCode: string, registryVersion: string) =>
    client<void>(`/api/v1/collaborations/${id}/permissions`, {
      method: "POST",
      body: jsonBody({ permissionCode, registryVersion }),
    }),
  revokeCollaborationPermission: (id: UUID, permissionCode: string) =>
    client<void>(`/api/v1/collaborations/${id}/permissions/${encodeURIComponent(permissionCode)}`, {
      method: "DELETE",
    }),

  subscription: () => client<Subscription>("/api/v1/subscriptions/me"),
  planCatalog: () => client<ClientPlanCatalog>("/api/v1/subscriptions/catalog"),
  previewSubscriptionChange: (input: SubscriptionChangeInput) =>
    client<ClientSubscriptionChangePreview>("/api/v1/subscriptions/preview", {
      method: "POST",
      body: jsonBody(input),
    }),
  applySubscriptionChange: (input: SubscriptionChangeApplyInput) =>
    client<ClientSubscriptionChangeApplyResponse>("/api/v1/subscriptions/apply", {
      method: "POST",
      body: jsonBody(input),
    }),
  subscriptionChanges: (
    query: {
      page?: number;
      size?: number;
      sort?: "createdAt" | "effectiveAt" | "status" | "timing";
      direction?: "asc" | "desc";
    } = {},
  ) => client<PageResponse<SubscriptionChangeOperation>>("/api/v1/subscriptions/changes", { query }),
  cancelSubscriptionChange: (id: UUID) =>
    client<SubscriptionChangeOperation>(`/api/v1/subscriptions/changes/${id}`, { method: "DELETE" }),
  subscriptionInvoices: (
    query: {
      page?: number;
      size?: number;
      sort?: "issuedAt" | "invoiceNumber" | "status" | "amount";
      direction?: "asc" | "desc";
    } = {},
  ) => client<PageResponse<BillingInvoiceRow>>("/api/v1/subscriptions/invoices", { query }),
  subscriptionInvoice: (id: UUID) => client<ClientBillingInvoiceDetail>(`/api/v1/subscriptions/invoices/${id}`),
  subscriptionInvoiceDocument: (id: UUID) =>
    client<BillingInvoiceDocument>(`/api/v1/subscriptions/invoices/${id}/document`),
  subscriptionFinancialTimeline: (
    query: {
      type?: BillingTimelineEntryType;
      currencyCode?: string;
      occurredFrom?: string;
      occurredUntil?: string;
      page?: number;
      size?: number;
    } = {},
  ) => client<PageResponse<BillingFinancialTimelineEntry>>("/api/v1/subscriptions/financial-timeline", { query }),
  billingProfile: () => client<AccountBillingProfile>("/api/v1/subscriptions/billing-profile"),
  updateBillingProfile: (input: AccountBillingProfileInput) =>
    client<AccountBillingProfile>("/api/v1/subscriptions/billing-profile", {
      method: "PUT",
      body: jsonBody(input),
    }),
};

export const authApi = {
  changeInitialPassword: (token: string, newPassword: string) =>
    apiRequest<AuthResponse>("/api/v1/auth/initial-password/change", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: jsonBody({ newPassword }),
    }),
  logoutInitialAccess: (token: string) =>
    apiRequest<void>("/api/v1/auth/initial-password/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    }),
  requestPasswordReset: (email: string) =>
    apiRequest<void>("/api/v1/auth/password-reset/request", { method: "POST", body: jsonBody({ email }) }),
  completePasswordReset: (token: string, newPassword: string) =>
    apiRequest<AuthResponse>("/api/v1/auth/password-reset/complete", {
      method: "POST",
      body: jsonBody({ token, newPassword }),
    }),
  completeActivation: (token: string, newPassword: string) =>
    apiRequest<AuthResponse>("/api/v1/auth/activation/complete", {
      method: "POST",
      body: jsonBody({ token, newPassword }),
    }),
};
