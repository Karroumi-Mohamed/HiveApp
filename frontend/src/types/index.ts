// ============================================
// Authentication & Identity Types
// ============================================

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

// ============================================
// Account Types
// ============================================

export interface Account {
  id: string;
  name: string;
  slug: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  name: string;
  slug: string;
}

export interface UpdateAccountRequest {
  name?: string;
  slug?: string;
}

// ============================================
// Company Types
// ============================================

export interface CompanyModule {
  id: string;
  moduleId: string;
  moduleName: string;
  active: boolean;
  activatedAt: string | null;
}

export interface Company {
  id: string;
  name: string;
  accountId: string;
  modules: CompanyModule[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateCompanyRequest {
  name: string;
  accountId: string;
}

export interface UpdateCompanyRequest {
  name?: string;
}

// ============================================
// Member Types
// ============================================

export interface MemberRole {
  id: string;
  name: string;
  description: string | null;
}

export interface Member {
  id: string;
  userId: string;
  userEmail: string;
  userFirstName: string;
  userLastName: string;
  accountId: string;
  companyId: string;
  companyName: string;
  roles: MemberRole[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMemberRequest {
  userId: string;
  companyId: string;
  roleIds: string[];
}

export interface AssignRoleRequest {
  roleIds: string[];
}

// ============================================
// Role Types
// ============================================

export interface Role {
  id: string;
  name: string;
  description: string | null;
  accountId: string;
  isDefault: boolean;
  permissions: Permission[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
  accountId: string;
  permissionIds: string[];
}

export interface UpdateRoleRequest {
  name?: string;
  description?: string;
  permissionIds?: string[];
}

// ============================================
// Permission Types
// ============================================

export type PermissionContext = 'ADMIN_PLATFORM' | 'CLIENT_OWN_ACCOUNT' | 'CLIENT_COLLABORATION';

export interface Permission {
  id: string;
  name: string;
  resource: string;
  action: string;
  context: PermissionContext;
  description: string | null;
  createdAt: string;
}

export interface CreatePermissionRequest {
  name: string;
  resource: string;
  action: string;
  context: PermissionContext;
  description?: string;
}

// ============================================
// Plan Types
// ============================================

export interface PlanFeature {
  id: string;
  name: string;
  key: string;
  limit: number | null;
  unlimited: boolean;
}

export interface Plan {
  id: string;
  name: string;
  description: string | null;
  price: number;
  billingPeriod: 'MONTHLY' | 'YEARLY';
  features: PlanFeature[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePlanRequest {
  name: string;
  description?: string;
  price: number;
  billingPeriod: 'MONTHLY' | 'YEARLY';
  featureIds: string[];
}

// ============================================
// Subscription Types
// ============================================

export type SubscriptionStatus = 'ACTIVE' | 'CANCELLED' | 'EXPIRED' | 'PENDING';

export interface Subscription {
  id: string;
  accountId: string;
  planId: string;
  planName: string;
  status: SubscriptionStatus;
  startDate: string;
  endDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSubscriptionRequest {
  accountId: string;
  planId: string;
}

// ============================================
// Module Types
// ============================================

export interface Feature {
  id: string;
  name: string;
  key: string;
  description: string | null;
  moduleId: string;
}

export interface Module {
  id: string;
  name: string;
  key: string;
  description: string | null;
  features: Feature[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateModuleRequest {
  name: string;
  key: string;
  description?: string;
}

export interface CreateFeatureRequest {
  name: string;
  key: string;
  description?: string;
  moduleId: string;
}

// ============================================
// Collaboration Types
// ============================================

export interface CollaborationPermission {
  id: string;
  permissionId: string;
  permissionName: string;
}

export interface Collaboration {
  id: string;
  sourceAccountId: string;
  sourceAccountName: string;
  targetAccountId: string;
  targetAccountName: string;
  permissions: CollaborationPermission[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCollaborationRequest {
  sourceAccountId: string;
  targetAccountId: string;
  permissionIds: string[];
}

export interface UpdateCollaborationPermissionsRequest {
  permissionIds: string[];
}

// ============================================
// Admin Types
// ============================================

export interface AdminPermission {
  id: string;
  name: string;
  resource: string;
  action: string;
}

export interface AdminRole {
  id: string;
  name: string;
  description: string | null;
  permissions: AdminPermission[];
}

export interface AdminUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: AdminRole[];
}

export interface CreateAdminRoleRequest {
  name: string;
  description?: string;
  permissionIds: string[];
}

// ============================================
// API Response Types
// ============================================

export interface ApiResponse<T> {
  data: T;
  message?: string;
}

export interface ApiError {
  status: number;
  message: string;
  errors?: Record<string, string[]>;
  timestamp: string;
  path: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// ============================================
// UI State Types
// ============================================

export interface SidebarItem {
  id: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  path: string;
  children?: SidebarItem[];
  badge?: string | number;
  requiredPermission?: string;
}

export interface BreadcrumbItem {
  label: string;
  path?: string;
}

export interface ToastOptions {
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message?: string;
  duration?: number;
}

// ============================================
// Form Types
// ============================================

export interface FormFieldError {
  field: string;
  message: string;
}

export interface FormState<T> {
  values: T;
  errors: FormFieldError[];
  isSubmitting: boolean;
  isValid: boolean;
}

// ============================================
// Permission Resolution Types
// ============================================

export interface PermissionSet {
  permissions: Set<string>;
}

export interface ResolvedPermissions {
  rolePermissions: string[];
  planCeiling: string[];
  modulePermissions: string[];
  effectivePermissions: string[];
}

// ============================================
// Dashboard Types
// ============================================

export interface DashboardStats {
  totalAccounts: number;
  activeSubscriptions: number;
  totalMembers: number;
  activeCollaborations: number;
}

export interface RecentActivity {
  id: string;
  type: 'account_created' | 'member_added' | 'subscription_created' | 'collaboration_created';
  description: string;
  timestamp: string;
  actor: string;
}
