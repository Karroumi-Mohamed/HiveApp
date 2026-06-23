import axios from 'axios'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useI18n } from '../../../lib/i18n-context'
import { ADMIN_PERMISSIONS } from '../admin-permissions'
import { useAdminSession } from '../auth/AdminSessionContext'
import { getPlatformFeatureCatalog, type PlatformFeatureModule } from '../features/api'
import { getAdminPlans, type AdminPlan, type AdminPlanFeature } from './api'
import {
  featureName,
  flattenModules,
  formatMoneyValue,
  formatNumber,
  loadFeatureMaps,
  planIsActive,
} from './plan-utils'

export function AdminPlanFeatureMatrixPage() {
  const { t, locale } = useI18n()
  const { user, hasPermission } = useAdminSession()
  const [plans, setPlans] = useState<AdminPlan[]>([])
  const [featuresByPlan, setFeaturesByPlan] = useState<Record<string, AdminPlanFeature[]>>({})
  const [catalogModules, setCatalogModules] = useState<PlatformFeatureModule[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'danger'; text: string } | null>(null)

  const canListPlanFeatures =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_LIST_FEATURES)

  const loadMatrix = useCallback(async (showRefresh = false) => {
    if (showRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setNotice(null)

    try {
      const [planResponse, catalogResponse] = await Promise.all([
        getAdminPlans(),
        getPlatformFeatureCatalog('PLAN_ASSIGNABLE'),
      ])
      setPlans(planResponse)
      setCatalogModules(catalogResponse)
      setFeaturesByPlan(canListPlanFeatures ? await loadFeatureMaps(planResponse) : {})
    } catch (requestError) {
      setNotice({ tone: 'danger', text: requestMessage(requestError, t('plansLoadFailed')) })
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [canListPlanFeatures, t])

  useEffect(() => {
    void loadMatrix()
  }, [loadMatrix])

  const catalogFeatures = useMemo(() => flattenModules(catalogModules), [catalogModules])
  const filteredFeatures = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return catalogFeatures
    return catalogFeatures.filter((feature) =>
      [feature.code, feature.displayName, feature.featureKey]
        .join(' ')
        .toLowerCase()
        .includes(query),
    )
  }, [catalogFeatures, search])

  return (
    <section className="plans-page admin-work-page" aria-labelledby="plan-features-title">
      <header className="work-header">
        <div>
          <div className="work-kicker">{t('billingGroup')}</div>
          <h1 id="plan-features-title" className="page-title mb-0">
            {t('planFeatureMatrix')}
          </h1>
        </div>
        <button
          type="button"
          className="btn btn-dark"
          onClick={() => void loadMatrix(true)}
          disabled={refreshing}
        >
          {refreshing ? (
            <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
          ) : (
            <i className="bi bi-arrow-clockwise me-2" aria-hidden="true" />
          )}
          {t('refresh')}
        </button>
      </header>

      {notice ? <div className="notice notice-danger" role="alert">{notice.text}</div> : null}

      {loading ? (
        <div className="operator-panel table-state">
          <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
          {t('loading')}
        </div>
      ) : (
        <section className="operator-panel matrix-panel" aria-label={t('planFeatureMatrix')}>
          <div className="index-toolbar">
            <label className="search-box" htmlFor="plan-feature-search">
              <i className="bi bi-search" aria-hidden="true" />
              <input
                id="plan-feature-search"
                value={search}
                placeholder={t('search')}
                onChange={(event) => setSearch(event.target.value)}
              />
            </label>
            <div className="index-result-bar inline">
              <span>{t('featuresShown')}</span>
              <strong>
                {filteredFeatures.length}/{catalogFeatures.length}
              </strong>
            </div>
          </div>

          {!canListPlanFeatures ? (
            <div className="table-state">{t('forbidden')}</div>
          ) : filteredFeatures.length === 0 ? (
            <div className="table-state">{t('empty')}</div>
          ) : (
            <div className="matrix-list">
              {filteredFeatures.map((feature) => (
                <article className="matrix-feature-row" key={feature.code}>
                  <div className="matrix-feature-main">
                    <strong>{featureName(feature, t)}</strong>
                    <span className="mono">{feature.code}</span>
                    <span className="matrix-feature-meta">
                      <span className="usage-token">
                        <i className="bi bi-key me-1" aria-hidden="true" />
                        {feature.permissions.length}
                      </span>
                      <span className="usage-token">
                        <i className="bi bi-speedometer2 me-1" aria-hidden="true" />
                        {feature.quotaSchema.length}
                      </span>
                    </span>
                  </div>
                  <div className="matrix-plan-grid">
                    {plans.map((plan) => (
                      <PlanFeatureCell
                        key={`${feature.code}-${plan.id}`}
                        plan={plan}
                        feature={featuresByPlan[plan.id]?.find(
                          (item) => item.featureCode === feature.code,
                        )}
                        locale={locale}
                      />
                    ))}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}
    </section>
  )
}

function PlanFeatureCell({
  plan,
  feature,
  locale,
}: {
  plan: AdminPlan
  feature: AdminPlanFeature | undefined
  locale: string
}) {
  const { t } = useI18n()

  return (
    <div className={`matrix-plan-cell ${feature ? 'is-included' : ''}`}>
      <div className="matrix-plan-head">
        <strong>{plan.code}</strong>
        <span>{planIsActive(plan) ? t('active') : t('inactive')}</span>
      </div>
      {feature ? (
        <>
          <span className="matrix-inclusion">
            <i className="bi bi-check2" aria-hidden="true" />
            {t('included')}
          </span>
          <span className="text-secondary">
            {formatMoneyValue(feature.addOnPrice, locale, t('noAddOnPrice'))}
          </span>
          {feature.quotaConfigs.length > 0 ? (
            <div className="quota-chip-row compact">
              {feature.quotaConfigs.map((quota) => (
                <span className="quota-chip" key={quota.resource}>
                  <span className="mono">{quota.resource}</span>
                  <strong>
                    {quota.limit === null ? t('unlimited') : formatNumber(quota.limit, locale)}
                  </strong>
                </span>
              ))}
            </div>
          ) : null}
        </>
      ) : (
        <span className="matrix-inclusion muted">
          <i className="bi bi-dash" aria-hidden="true" />
          {t('notIncluded')}
        </span>
      )}
    </div>
  )
}

function requestMessage(error: unknown, fallback: string) {
  const backendMessage = axios.isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof backendMessage === 'string' ? backendMessage : fallback
}
