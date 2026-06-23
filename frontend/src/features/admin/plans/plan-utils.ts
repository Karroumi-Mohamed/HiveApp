import { featureTranslationKey } from '../../../lib/i18n-context'
import { type PlatformFeature, type PlatformFeatureModule } from '../features/api'
import { getAdminPlanFeatures, type AdminPlan, type BillingCycle } from './api'

export async function loadFeatureMaps(plans: AdminPlan[]) {
  const entries = await Promise.all(
    plans.map(async (plan) => [plan.id, await getAdminPlanFeatures(plan.id)] as const),
  )
  return Object.fromEntries(entries)
}

export function flattenModules(modules: PlatformFeatureModule[]) {
  return modules.flatMap((module) =>
    module.features.map((feature) => ({
      ...feature,
      moduleCode: feature.moduleCode || module.code,
    })),
  )
}

export function indexFeatures(features: PlatformFeature[]) {
  return new Map(features.map((feature) => [feature.code, feature]))
}

export function planIsActive(plan: AdminPlan) {
  return Boolean(plan.active ?? plan.isActive)
}

export function featureName(
  feature: PlatformFeature,
  t: (key: string, fallback?: string) => string,
) {
  return t(featureTranslationKey(feature.code), feature.displayName)
}

export function billingCycleLabel(
  cycle: BillingCycle,
  t: (key: string, fallback?: string) => string,
) {
  return t(`billingCycle_${cycle}`, cycle)
}

export function formatPlanPrice(plan: AdminPlan, locale: string) {
  const value = toNumber(plan.price)
  if (value === null) {
    return '-'
  }
  return formatCurrency(value, locale)
}

export function formatMoneyValue(
  value: number | string | null | undefined,
  locale: string,
  emptyLabel: string,
) {
  const parsed = toNumber(value)
  return parsed === null ? emptyLabel : formatCurrency(parsed, locale)
}

export function formatCurrency(value: number, locale: string) {
  return new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : 'fr-FR', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: value % 1 === 0 ? 0 : 2,
  }).format(value)
}

export function formatNumber(value: number, locale: string) {
  return new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : 'fr-FR').format(value)
}

function toNumber(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}
