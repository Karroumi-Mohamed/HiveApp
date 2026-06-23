import { apiClient } from '../../../lib/api'

export type BillingCycle = 'MONTHLY' | 'YEARLY' | 'FOREVER'

export type AdminPlan = {
  id: string
  code: string
  name: string
  description: string | null
  price: number | string | null
  billingCycle: BillingCycle
  active?: boolean
  isActive?: boolean
}

export type PlanQuotaConfig = {
  resource: string
  limit: number | null
  pricePerUnit: number | string | null
}

export type AdminPlanFeature = {
  id: string
  featureCode: string
  addOnPrice: number | string | null
  quotaConfigs: PlanQuotaConfig[]
}

export type CreateAdminPlanRequest = {
  code: string
  name: string
  description: string | null
  price: number
  billingCycle: BillingCycle
  inheritFromPlanId: string | null
}

export type SaveAdminPlanFeatureRequest = {
  featureCode: string
  addOnPrice: number | null
  quotaConfigs: PlanQuotaConfig[]
}

export async function getAdminPlans() {
  const response = await apiClient.get<AdminPlan[]>('/api/admin/plans')
  return response.data
}

export async function createAdminPlan(request: CreateAdminPlanRequest) {
  const response = await apiClient.post<AdminPlan>('/api/admin/plans', request)
  return response.data
}

export async function getAdminPlanFeatures(planId: string) {
  const response = await apiClient.get<AdminPlanFeature[]>(`/api/admin/plans/${planId}/features`)
  return response.data
}

export async function assignAdminPlanFeature(planId: string, request: SaveAdminPlanFeatureRequest) {
  const response = await apiClient.post<AdminPlanFeature>(`/api/admin/plans/${planId}/features`, request)
  return response.data
}

export async function updateAdminPlanFeature(
  planId: string,
  planFeatureId: string,
  request: SaveAdminPlanFeatureRequest,
) {
  const response = await apiClient.put<AdminPlanFeature>(
    `/api/admin/plans/${planId}/features/${planFeatureId}`,
    request,
  )
  return response.data
}

export async function removeAdminPlanFeature(planId: string, planFeatureId: string) {
  await apiClient.delete(`/api/admin/plans/${planId}/features/${planFeatureId}`)
}

export async function updateAdminPlanActive(planId: string, active: boolean) {
  const response = await apiClient.patch<AdminPlan>(`/api/admin/plans/${planId}/active`, undefined, {
    params: { active },
  })
  return response.data
}
