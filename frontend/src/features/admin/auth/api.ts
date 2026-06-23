import { apiClient } from '../../../lib/api'

export type AdminLoginRequest = {
  email: string
  password: string
}

export type AdminLoginResponse = {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export type AdminMe = {
  id: string
  email: string
  isSuperAdmin: boolean
  isActive: boolean
  permissions: string[]
}

export async function loginAdmin(payload: AdminLoginRequest) {
  const response = await apiClient.post<AdminLoginResponse>('/api/admin/auth/login', payload)
  return response.data
}

export async function getAdminMe() {
  const response = await apiClient.get<AdminMe>('/api/admin/me')
  return response.data
}
