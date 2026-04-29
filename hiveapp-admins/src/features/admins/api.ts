import { api } from '@/lib/api'

export interface AdminUserDto {
  id: string
  userId: string
  isSuperAdmin: boolean
  isActive: boolean
}

export interface AdminRoleDto {
  id: string
  name: string
  description?: string
  isActive: boolean
}

export const adminUsersApi = {
  listUsers: async () => {
    return api.get('users').json<AdminUserDto[]>()
  },
  
  listRoles: async () => {
    return api.get('roles').json<AdminRoleDto[]>()
  },

  toggleUserActive: async (id: string) => {
    return api.post(`users/${id}/toggle-active`).text()
  },

  toggleRoleActive: async (id: string) => {
    return api.post(`roles/${id}/toggle-active`).text()
  }
}
