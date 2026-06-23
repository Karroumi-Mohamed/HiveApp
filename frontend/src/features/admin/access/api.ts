import { apiClient } from '../../../lib/api'

export type AdminRoleSummary = {
  id: string
  name: string
  description: string | null
  isActive: boolean
}

export type AdminPermissionSummary = {
  id: string
  code: string
  name: string
  description: string | null
  action: string | null
  resource: string | null
}

export type AdminUserRecord = {
  id: string
  userId: string
  email: string
  isSuperAdmin: boolean
  isActive: boolean
  roles: AdminRoleSummary[]
}

export type AdminRoleRecord = AdminRoleSummary & {
  permissions: AdminPermissionSummary[]
}

type RawAdminRoleSummary = Omit<AdminRoleSummary, 'isActive'> & {
  isActive?: boolean
  active?: boolean
}

type RawAdminUserRecord = Omit<AdminUserRecord, 'isActive' | 'isSuperAdmin' | 'roles'> & {
  isActive?: boolean
  active?: boolean
  isSuperAdmin?: boolean
  superAdmin?: boolean
  roles?: RawAdminRoleSummary[]
}

type RawAdminRoleRecord = RawAdminRoleSummary & {
  permissions?: AdminPermissionSummary[]
}

export type PermissionCatalogModule = {
  code: string
  features: Array<{
    code: string
    displayName: string
    description: string | null
    permissions: AdminPermissionSummary[]
  }>
}

export async function getAdminUsers() {
  const response = await apiClient.get<RawAdminUserRecord[]>('/api/admin/users')
  return response.data.map(normalizeAdminUser)
}

export async function createAdminUser(request: { userId: string; isSuperAdmin: boolean }) {
  const response = await apiClient.post<RawAdminUserRecord>('/api/admin/users', request)
  return normalizeAdminUser(response.data)
}

export async function toggleAdminUserActive(adminUserId: string) {
  await apiClient.post(`/api/admin/users/${adminUserId}/toggle-active`)
}

export async function assignAdminUserRole(adminUserId: string, adminRoleId: string) {
  await apiClient.post(`/api/admin/users/${adminUserId}/roles`, { adminRoleId })
}

export async function removeAdminUserRole(adminUserId: string, adminRoleId: string) {
  await apiClient.delete(`/api/admin/users/${adminUserId}/roles/${adminRoleId}`)
}

export async function getAdminRoles() {
  const response = await apiClient.get<RawAdminRoleRecord[]>('/api/admin/roles')
  return response.data.map(normalizeAdminRole)
}

export async function createAdminRole(request: { name: string; description: string | null }) {
  const response = await apiClient.post<RawAdminRoleRecord>('/api/admin/roles', request)
  return normalizeAdminRole(response.data)
}

export async function updateAdminRole(
  roleId: string,
  request: { name: string; description: string | null },
) {
  const response = await apiClient.put<RawAdminRoleRecord>(`/api/admin/roles/${roleId}`, request)
  return normalizeAdminRole(response.data)
}

export async function toggleAdminRoleActive(roleId: string) {
  await apiClient.post(`/api/admin/roles/${roleId}/toggle-active`)
}

export async function grantAdminRolePermission(roleId: string, permissionId: string) {
  await apiClient.post(`/api/admin/roles/${roleId}/permissions`, { permissionId })
}

export async function revokeAdminRolePermission(roleId: string, permissionId: string) {
  await apiClient.delete(`/api/admin/roles/${roleId}/permissions/${permissionId}`)
}

export async function getPlatformAdminPermissionCatalog() {
  const response = await apiClient.get<PermissionCatalogModule[]>(
    '/api/admin/registry/permission-catalog',
    { params: { audience: 'PLATFORM_ADMIN_ROLE_GRANTABLE' } },
  )
  return response.data
}

function normalizeAdminUser(user: RawAdminUserRecord): AdminUserRecord {
  return {
    ...user,
    isActive: user.isActive ?? Boolean(user.active),
    isSuperAdmin: user.isSuperAdmin ?? Boolean(user.superAdmin),
    roles: (user.roles ?? []).map(normalizeAdminRoleSummary),
  }
}

function normalizeAdminRole(role: RawAdminRoleRecord): AdminRoleRecord {
  return {
    ...normalizeAdminRoleSummary(role),
    permissions: role.permissions ?? [],
  }
}

function normalizeAdminRoleSummary(role: RawAdminRoleSummary): AdminRoleSummary {
  return {
    id: role.id,
    name: role.name,
    description: role.description,
    isActive: role.isActive ?? Boolean(role.active),
  }
}
