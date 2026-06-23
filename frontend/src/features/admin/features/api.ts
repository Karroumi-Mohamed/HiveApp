import { apiClient } from '../../../lib/api'

export type FeatureSurface = 'PLATFORM_CONTROL' | 'CLIENT_WORKSPACE' | 'PUBLIC' | 'SYSTEM'
export type FeatureStatus = 'PUBLIC' | 'INTERNAL' | 'BETA' | 'DEPRECATED'

export type RegistryPermission = {
  id: string
  code: string
  name: string
  description: string | null
  action: string | null
  resource: string | null
}

export type QuotaSlot = {
  resource?: string
  type?: string
  unit?: string
}

export type PlatformFeature = {
  id: string | null
  code: string
  moduleCode: string
  featureKey: string
  displayName: string
  description: string
  surface: FeatureSurface
  status: FeatureStatus | null
  active: boolean
  registryPresent: boolean
  planAssignable: boolean
  clientRoleGrantable: boolean
  platformAdminRoleGrantable: boolean
  b2bDelegatable: boolean
  publicCatalogVisible: boolean
  operationsActivationToggleable: boolean
  sortOrder: number
  quotaSchema: QuotaSlot[]
  permissions: RegistryPermission[]
}

export type PlatformFeatureModule = {
  code: string
  features: PlatformFeature[]
}

export type FeatureCatalogAudience = 'ALL' | 'PLAN_ASSIGNABLE' | 'PUBLIC_CATALOG'

export async function getPlatformFeatureCatalog(audience: FeatureCatalogAudience = 'ALL') {
  const response = await apiClient.get<PlatformFeatureModule[]>(
    '/api/admin/registry/feature-catalog',
    {
      params: { audience },
    },
  )
  return response.data
}

export async function updatePlatformFeatureActive(featureId: string, active: boolean) {
  await apiClient.patch(`/api/admin/registry/features/${featureId}/active`, undefined, {
    params: { active },
  })
}
