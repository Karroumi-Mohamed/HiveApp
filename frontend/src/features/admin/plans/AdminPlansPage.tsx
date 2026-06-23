import axios from 'axios'
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type FormEvent,
  type ReactNode,
  type SetStateAction,
} from 'react'
import { useI18n } from '../../../lib/i18n-context'
import { ADMIN_PERMISSIONS } from '../admin-permissions'
import { useAdminSession } from '../auth/AdminSessionContext'
import {
  getPlatformFeatureCatalog,
  type PlatformFeature,
  type PlatformFeatureModule,
} from '../features/api'
import {
  assignAdminPlanFeature,
  createAdminPlan,
  getAdminPlanFeatures,
  getAdminPlans,
  removeAdminPlanFeature,
  updateAdminPlanActive,
  updateAdminPlanFeature,
  type AdminPlan,
  type AdminPlanFeature,
  type BillingCycle,
  type CreateAdminPlanRequest,
  type PlanQuotaConfig,
  type SaveAdminPlanFeatureRequest,
} from './api'
import {
  billingCycleLabel,
  featureName,
  flattenModules,
  formatMoneyValue,
  formatNumber,
  formatPlanPrice,
  indexFeatures,
  loadFeatureMaps,
  planIsActive,
} from './plan-utils'

type Notice = { tone: 'danger' | 'success' | 'warning'; text: string }
type PlanModalState =
  | { kind: 'create' }
  | { kind: 'assign'; plan: AdminPlan; availableFeatures: PlatformFeature[] }
  | { kind: 'edit'; plan: AdminPlan; planFeature: AdminPlanFeature; feature: PlatformFeature | null }
  | { kind: 'remove'; plan: AdminPlan; planFeature: AdminPlanFeature; label: string }

export function AdminPlansPage() {
  const { t, locale } = useI18n()
  const { user, hasPermission } = useAdminSession()
  const [plans, setPlans] = useState<AdminPlan[]>([])
  const [featuresByPlan, setFeaturesByPlan] = useState<Record<string, AdminPlanFeature[]>>({})
  const [catalogModules, setCatalogModules] = useState<PlatformFeatureModule[]>([])
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [savingPlanId, setSavingPlanId] = useState<string | null>(null)
  const [modal, setModal] = useState<PlanModalState | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)

  const canListPlanFeatures =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_LIST_FEATURES)
  const canTogglePlan =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_TOGGLE_ACTIVE)
  const canCreatePlan =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_CREATE)
  const canAssignFeature =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_ASSIGN_FEATURE)
  const canUpdateFeature =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_UPDATE_FEATURE)
  const canRemoveFeature =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.PLAN_REMOVE_FEATURE)

  const catalogFeatures = useMemo(() => flattenModules(catalogModules), [catalogModules])
  const catalogByCode = useMemo(() => indexFeatures(catalogFeatures), [catalogFeatures])
  const selectedPlan = useMemo(
    () => plans.find((plan) => plan.id === selectedPlanId) ?? null,
    [plans, selectedPlanId],
  )
  const selectedFeatures = useMemo(
    () => (selectedPlanId ? (featuresByPlan[selectedPlanId] ?? []) : []),
    [featuresByPlan, selectedPlanId],
  )
  const availableFeatures = useMemo(() => {
    const included = new Set(selectedFeatures.map((feature) => feature.featureCode))
    return catalogFeatures.filter((feature) => !included.has(feature.code))
  }, [catalogFeatures, selectedFeatures])

  const loadPlans = useCallback(async (showRefresh = false) => {
    if (showRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setNotice(null)

    try {
      const [planResponse, catalogResponse] = await Promise.all([
        getAdminPlans(),
        getPlatformFeatureCatalog('PLAN_ASSIGNABLE').catch(() => []),
      ])
      const nextFeaturesByPlan = canListPlanFeatures ? await loadFeatureMaps(planResponse) : {}

      setPlans(planResponse)
      setCatalogModules(catalogResponse)
      setFeaturesByPlan(nextFeaturesByPlan)
      setSelectedPlanId((current) => {
        if (current && planResponse.some((plan) => plan.id === current)) {
          return current
        }
        return planResponse[0]?.id ?? null
      })
    } catch (requestError) {
      setNotice({ tone: 'danger', text: requestMessage(requestError, t('plansLoadFailed')) })
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [canListPlanFeatures, t])

  useEffect(() => {
    void loadPlans()
  }, [loadPlans])

  async function reloadPlanFeatures(planId: string) {
    const updatedFeatures = await getAdminPlanFeatures(planId)
    setFeaturesByPlan((current) => ({
      ...current,
      [planId]: updatedFeatures,
    }))
    return updatedFeatures
  }

  async function togglePlanActive(plan: AdminPlan, active: boolean) {
    if (!canTogglePlan || savingPlanId) {
      return
    }

    setSavingPlanId(plan.id)
    setNotice(null)
    try {
      const updatedPlan = await updateAdminPlanActive(plan.id, active)
      setPlans((current) =>
        current.map((item) => (item.id === plan.id ? normalizeUpdatedPlan(item, updatedPlan) : item)),
      )
      setNotice({ tone: 'success', text: t('planStatusSaved') })
    } catch (requestError) {
      setNotice({ tone: 'danger', text: requestMessage(requestError, t('forbidden')) })
    } finally {
      setSavingPlanId(null)
    }
  }

  async function handleCreatePlan(request: CreateAdminPlanRequest) {
    const created = await createAdminPlan(request)
    const inheritedFeatures = canListPlanFeatures ? await getAdminPlanFeatures(created.id) : []
    setPlans((current) => [...current, created])
    setFeaturesByPlan((current) => ({ ...current, [created.id]: inheritedFeatures }))
    setSelectedPlanId(created.id)
    setModal(null)
    setNotice({ tone: 'success', text: t('planCreated') })
  }

  async function handleSavePlanFeature(plan: AdminPlan, request: SaveAdminPlanFeatureRequest, planFeatureId?: string) {
    if (planFeatureId) {
      await updateAdminPlanFeature(plan.id, planFeatureId, request)
      setNotice({ tone: 'success', text: t('planFeatureUpdated') })
    } else {
      await assignAdminPlanFeature(plan.id, request)
      setNotice({ tone: 'success', text: t('planFeatureAssigned') })
    }
    await reloadPlanFeatures(plan.id)
    setModal(null)
  }

  async function handleRemovePlanFeature(plan: AdminPlan, planFeature: AdminPlanFeature) {
    await removeAdminPlanFeature(plan.id, planFeature.id)
    await reloadPlanFeatures(plan.id)
    setModal(null)
    setNotice({ tone: 'success', text: t('planFeatureRemoved') })
  }

  return (
    <section className="plans-page admin-work-page" aria-labelledby="plans-page-title">
      <header className="work-header">
        <div>
          <div className="work-kicker">{t('billingGroup')}</div>
          <h1 id="plans-page-title" className="page-title mb-0">
            {t('planTemplates')}
          </h1>
        </div>
        <div className="page-actions">
          {canCreatePlan ? (
            <button type="button" className="btn btn-primary" onClick={() => setModal({ kind: 'create' })}>
              <i className="bi bi-plus-lg me-2" aria-hidden="true" />
              {t('newPlan')}
            </button>
          ) : null}
          <button
            type="button"
            className="btn btn-dark"
            onClick={() => void loadPlans(true)}
            disabled={refreshing}
          >
            {refreshing ? (
              <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
            ) : (
              <i className="bi bi-arrow-clockwise me-2" aria-hidden="true" />
            )}
            {t('refresh')}
          </button>
        </div>
      </header>

      {notice ? (
        <div
          className={`notice notice-${notice.tone}`}
          role={notice.tone === 'danger' ? 'alert' : 'status'}
        >
          {notice.text}
        </div>
      ) : null}

      {loading ? (
        <div className="operator-panel table-state">
          <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
          {t('loading')}
        </div>
      ) : (
        <div className="plans-workspace">
          <PlanIndex
            plans={plans}
            featuresByPlan={featuresByPlan}
            selectedPlanId={selectedPlanId}
            locale={locale}
            onSelectPlan={setSelectedPlanId}
          />
          <PlanDetail
            plan={selectedPlan}
            planFeatures={selectedFeatures}
            availableFeatures={availableFeatures}
            catalogByCode={catalogByCode}
            canListFeatures={canListPlanFeatures}
            canTogglePlan={canTogglePlan}
            canAssignFeature={canAssignFeature}
            canUpdateFeature={canUpdateFeature}
            canRemoveFeature={canRemoveFeature}
            saving={selectedPlan ? savingPlanId === selectedPlan.id : false}
            locale={locale}
            onToggleActive={(plan, active) => void togglePlanActive(plan, active)}
            onAssignFeature={(plan) => setModal({ kind: 'assign', plan, availableFeatures })}
            onEditFeature={(plan, planFeature, feature) =>
              setModal({ kind: 'edit', plan, planFeature, feature })
            }
            onRemoveFeature={(plan, planFeature, label) =>
              setModal({ kind: 'remove', plan, planFeature, label })
            }
          />
        </div>
      )}

      {modal?.kind === 'create' ? (
        <CreatePlanDialog
          plans={plans}
          featuresByPlan={featuresByPlan}
          onClose={() => setModal(null)}
          onSubmit={handleCreatePlan}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}

      {modal?.kind === 'assign' ? (
        <PlanFeatureDialog
          mode="assign"
          plan={modal.plan}
          availableFeatures={modal.availableFeatures}
          onClose={() => setModal(null)}
          onSubmit={(request) => handleSavePlanFeature(modal.plan, request)}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}

      {modal?.kind === 'edit' ? (
        <PlanFeatureDialog
          mode="edit"
          plan={modal.plan}
          planFeature={modal.planFeature}
          feature={modal.feature}
          availableFeatures={modal.feature ? [modal.feature] : []}
          onClose={() => setModal(null)}
          onSubmit={(request) => handleSavePlanFeature(modal.plan, request, modal.planFeature.id)}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}

      {modal?.kind === 'remove' ? (
        <ConfirmRemoveDialog
          label={modal.label}
          onClose={() => setModal(null)}
          onConfirm={() => handleRemovePlanFeature(modal.plan, modal.planFeature)}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}
    </section>
  )
}

function PlanIndex({
  plans,
  featuresByPlan,
  selectedPlanId,
  locale,
  onSelectPlan,
}: {
  plans: AdminPlan[]
  featuresByPlan: Record<string, AdminPlanFeature[]>
  selectedPlanId: string | null
  locale: string
  onSelectPlan: (planId: string) => void
}) {
  const { t } = useI18n()

  return (
    <section className="operator-panel plan-index-panel" aria-label={t('planTemplates')}>
      <div className="panel-title-row">
        <div>
          <h2>{t('planTemplates')}</h2>
          <span>{plans.length}</span>
        </div>
      </div>

      {plans.length === 0 ? (
        <div className="table-state">{t('empty')}</div>
      ) : (
        <div className="plan-list" role="list">
          {plans.map((plan) => {
            const selected = plan.id === selectedPlanId
            const featureCount = featuresByPlan[plan.id]?.length
            return (
              <button
                type="button"
                key={plan.id}
                className={`plan-list-item ${selected ? 'is-selected' : ''}`}
                aria-pressed={selected}
                onClick={() => onSelectPlan(plan.id)}
              >
                <span className="plan-list-title">
                  <strong>{plan.name}</strong>
                  <span className="mono">{plan.code}</span>
                </span>
                <span className="plan-list-price">
                  <strong>{formatPlanPrice(plan, locale)}</strong>
                  <span>{billingCycleLabel(plan.billingCycle, t)}</span>
                </span>
                <span className="plan-list-meta">
                  <span className="usage-token">
                    <i className="bi bi-grid-1x2 me-1" aria-hidden="true" />
                    {featureCount ?? '-'}
                  </span>
                  <StatusPill active={planIsActive(plan)} />
                </span>
              </button>
            )
          })}
        </div>
      )}
    </section>
  )
}

function PlanDetail({
  plan,
  planFeatures,
  availableFeatures,
  catalogByCode,
  canListFeatures,
  canTogglePlan,
  canAssignFeature,
  canUpdateFeature,
  canRemoveFeature,
  saving,
  locale,
  onToggleActive,
  onAssignFeature,
  onEditFeature,
  onRemoveFeature,
}: {
  plan: AdminPlan | null
  planFeatures: AdminPlanFeature[]
  availableFeatures: PlatformFeature[]
  catalogByCode: Map<string, PlatformFeature>
  canListFeatures: boolean
  canTogglePlan: boolean
  canAssignFeature: boolean
  canUpdateFeature: boolean
  canRemoveFeature: boolean
  saving: boolean
  locale: string
  onToggleActive: (plan: AdminPlan, active: boolean) => void
  onAssignFeature: (plan: AdminPlan) => void
  onEditFeature: (plan: AdminPlan, planFeature: AdminPlanFeature, feature: PlatformFeature | null) => void
  onRemoveFeature: (plan: AdminPlan, planFeature: AdminPlanFeature, label: string) => void
}) {
  const { t } = useI18n()

  if (!plan) {
    return (
      <section className="operator-panel plan-detail-panel" aria-label={t('planDetails')}>
        <div className="table-state">{t('selectPlan')}</div>
      </section>
    )
  }

  const active = planIsActive(plan)
  const quotaConfiguredCount = planFeatures.filter((feature) => feature.quotaConfigs.length > 0).length

  return (
    <section className="operator-panel plan-detail-panel" aria-labelledby="selected-plan-title">
      <div className="plan-detail-header">
        <div>
          <div className="work-kicker">{t('selectedPlan')}</div>
          <h2 id="selected-plan-title">{plan.name}</h2>
          <div className="detail-code-line">
            <span className="mono">{plan.code}</span>
            <StatusPill active={active} />
          </div>
        </div>

        <div className="plan-price-block">
          <strong>{formatPlanPrice(plan, locale)}</strong>
          <span>{billingCycleLabel(plan.billingCycle, t)}</span>
        </div>

        {canTogglePlan ? (
          <button
            type="button"
            className={`btn ${active ? 'btn-outline-secondary' : 'btn-primary'}`}
            disabled={saving}
            onClick={() => onToggleActive(plan, !active)}
          >
            {saving ? (
              <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
            ) : (
              <i className={`bi ${active ? 'bi-pause-circle' : 'bi-play-circle'} me-2`} aria-hidden="true" />
            )}
            {active ? t('deactivatePlan') : t('activatePlan')}
          </button>
        ) : null}
      </div>

      <div className="plan-summary-strip" aria-label={t('overview')}>
        <div>
          <span>{t('includedFeatures')}</span>
          <strong>{canListFeatures ? planFeatures.length : '-'}</strong>
        </div>
        <div>
          <span>{t('quotaConfigured')}</span>
          <strong>{canListFeatures ? quotaConfiguredCount : '-'}</strong>
        </div>
        <div>
          <span>{t('availablePlanFeatures')}</span>
          <strong>{availableFeatures.length}</strong>
        </div>
      </div>

      <section className="plan-composition-section" aria-label={t('includedFeatures')}>
        <div className="section-title-row">
          <h3>{t('planComposition')}</h3>
          {canAssignFeature ? (
            <button
              type="button"
              className="btn btn-outline-primary btn-sm"
              disabled={availableFeatures.length === 0}
              title={availableFeatures.length === 0 ? t('empty') : t('assignFeature')}
              onClick={() => onAssignFeature(plan)}
            >
              <i className="bi bi-plus-lg me-2" aria-hidden="true" />
              {t('assignFeature')}
            </button>
          ) : null}
        </div>
        {canListFeatures ? (
          <IncludedFeaturesList
            plan={plan}
            planFeatures={planFeatures}
            catalogByCode={catalogByCode}
            canUpdateFeature={canUpdateFeature}
            canRemoveFeature={canRemoveFeature}
            locale={locale}
            onEditFeature={onEditFeature}
            onRemoveFeature={onRemoveFeature}
          />
        ) : (
          <div className="table-state">{t('forbidden')}</div>
        )}
      </section>
    </section>
  )
}

function IncludedFeaturesList({
  plan,
  planFeatures,
  catalogByCode,
  canUpdateFeature,
  canRemoveFeature,
  locale,
  onEditFeature,
  onRemoveFeature,
}: {
  plan: AdminPlan
  planFeatures: AdminPlanFeature[]
  catalogByCode: Map<string, PlatformFeature>
  canUpdateFeature: boolean
  canRemoveFeature: boolean
  locale: string
  onEditFeature: (plan: AdminPlan, planFeature: AdminPlanFeature, feature: PlatformFeature | null) => void
  onRemoveFeature: (plan: AdminPlan, planFeature: AdminPlanFeature, label: string) => void
}) {
  const { t } = useI18n()

  if (planFeatures.length === 0) {
    return <div className="table-state">{t('noPlanFeatures')}</div>
  }

  return (
    <div className="plan-feature-list" role="list">
      {planFeatures.map((planFeature) => {
        const feature = catalogByCode.get(planFeature.featureCode) ?? null
        const label = feature ? featureName(feature, t) : planFeature.featureCode
        return (
          <article className="plan-feature-item with-actions" key={planFeature.id} role="listitem">
            <div className="plan-feature-main">
              <strong>{label}</strong>
              <span className="mono">{planFeature.featureCode}</span>
            </div>
            <div className="plan-feature-config">
              <span className="usage-token">{formatMoneyValue(planFeature.addOnPrice, locale, t('noAddOnPrice'))}</span>
              <QuotaConfigList configs={planFeature.quotaConfigs} />
            </div>
            <div className="plan-feature-actions">
              {canUpdateFeature ? (
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm"
                  title={t('editFeature')}
                  onClick={() => onEditFeature(plan, planFeature, feature)}
                >
                  <i className="bi bi-pencil-square" aria-hidden="true" />
                  <span className="visually-hidden">{t('editFeature')}</span>
                </button>
              ) : null}
              {canRemoveFeature ? (
                <button
                  type="button"
                  className="btn btn-outline-danger btn-sm"
                  title={t('removeFeature')}
                  onClick={() => onRemoveFeature(plan, planFeature, label)}
                >
                  <i className="bi bi-trash3" aria-hidden="true" />
                  <span className="visually-hidden">{t('removeFeature')}</span>
                </button>
              ) : null}
            </div>
          </article>
        )
      })}
    </div>
  )
}

function QuotaConfigList({ configs }: { configs: PlanQuotaConfig[] }) {
  const { t, locale } = useI18n()

  if (!configs || configs.length === 0) {
    return <span className="text-secondary">{t('noQuotaConfig')}</span>
  }

  return (
    <div className="quota-chip-row">
      {configs.map((config) => (
        <span className="quota-chip" key={config.resource}>
          <span className="quota-chip-title mono">{config.resource}</span>
          <strong>
            {config.limit === null ? t('unlimited') : `${t('includedLimitShort')} ${formatNumber(config.limit, locale)}`}
          </strong>
          {config.pricePerUnit !== null && config.pricePerUnit !== undefined ? (
            <small>{`${formatMoneyValue(config.pricePerUnit, locale, '')} / ${t('extraUnit')}`}</small>
          ) : (
            <small>{config.limit === null ? t('noExtraPrice') : t('fixedLimit')}</small>
          )}
        </span>
      ))}
    </div>
  )
}

function CreatePlanDialog({
  plans,
  featuresByPlan,
  onClose,
  onSubmit,
  onError,
}: {
  plans: AdminPlan[]
  featuresByPlan: Record<string, AdminPlanFeature[]>
  onClose: () => void
  onSubmit: (request: CreateAdminPlanRequest) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const defaultSourcePlanId = plans.find((plan) => plan.code === 'FREE')?.id ?? plans[0]?.id ?? ''
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState('0')
  const [billingCycle, setBillingCycle] = useState<BillingCycle>('MONTHLY')
  const [inheritFromPlanId, setInheritFromPlanId] = useState(defaultSourcePlanId)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await onSubmit({
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: description.trim() || null,
        price: parseMoney(price),
        billingCycle,
        inheritFromPlanId: inheritFromPlanId || null,
      })
    } catch (requestError) {
      onError(requestMessage(requestError, t('forbidden')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PlanDialog title={t('newPlan')} onClose={onClose}>
      <form className="management-form" onSubmit={(event) => void submit(event)}>
        <TextField label={t('code')} value={code} onChange={setCode} required />
        <TextField label={t('name')} value={name} onChange={setName} required />
        <TextField label={t('description')} value={description} onChange={setDescription} />
        <TextField label={t('priceColumn')} value={price} onChange={setPrice} type="number" min="0" step="0.01" required />
        <label className="form-field">
          <span>{t('billingCycle')}</span>
          <select
            className="form-select"
            value={billingCycle}
            onChange={(event) => setBillingCycle(event.target.value as BillingCycle)}
          >
            <option value="MONTHLY">{t('billingCycle_MONTHLY')}</option>
            <option value="YEARLY">{t('billingCycle_YEARLY')}</option>
            <option value="FOREVER">{t('billingCycle_FOREVER')}</option>
          </select>
        </label>
        <label className="form-field">
          <span>{t('inheritFromPlan')}</span>
          <select
            className="form-select"
            value={inheritFromPlanId}
            onChange={(event) => setInheritFromPlanId(event.target.value)}
            disabled={plans.length === 0}
          >
            {plans.length === 0 ? (
              <option value="">{t('noSourcePlan')}</option>
            ) : (
              plans.map((plan) => {
                const featureCount = featuresByPlan[plan.id]?.length
                return (
                  <option key={plan.id} value={plan.id}>
                    {plan.name} / {plan.code} ({featureCount ?? '-'} {t('features')})
                  </option>
                )
              })
            )}
          </select>
          <small>{t('inheritFromPlanHelp')}</small>
        </label>
        <DialogActions onClose={onClose} submitting={submitting} submitLabel={t('createPlan')} />
      </form>
    </PlanDialog>
  )
}

function PlanFeatureDialog({
  mode,
  plan,
  availableFeatures,
  planFeature,
  feature,
  onClose,
  onSubmit,
  onError,
}: {
  mode: 'assign' | 'edit'
  plan: AdminPlan
  availableFeatures: PlatformFeature[]
  planFeature?: AdminPlanFeature
  feature?: PlatformFeature | null
  onClose: () => void
  onSubmit: (request: SaveAdminPlanFeatureRequest) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const initialFeatureCode = planFeature?.featureCode ?? availableFeatures[0]?.code ?? ''
  const [featureCode, setFeatureCode] = useState(initialFeatureCode)
  const selectedFeature = feature ?? availableFeatures.find((item) => item.code === featureCode) ?? null
  const [addOnPrice, setAddOnPrice] = useState(planFeature?.addOnPrice?.toString() ?? '')
  const [quotaRows, setQuotaRows] = useState<QuotaFormRow[]>(() =>
    buildQuotaRows(selectedFeature, planFeature?.quotaConfigs ?? []),
  )
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'assign') {
      setQuotaRows(buildQuotaRows(selectedFeature, []))
    }
  }, [mode, selectedFeature])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!featureCode) {
      onError(t('empty'))
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        featureCode,
        addOnPrice: addOnPrice.trim() ? parseMoney(addOnPrice) : null,
        quotaConfigs: quotaRows.map((row) => ({
          resource: row.resource,
          limit: row.limit.trim() ? parseInteger(row.limit) : null,
          pricePerUnit: row.limit.trim() && row.pricePerUnit.trim() ? parseMoney(row.pricePerUnit) : null,
        })),
      })
    } catch (requestError) {
      onError(requestMessage(requestError, t('forbidden')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PlanDialog
      title={mode === 'assign' ? t('assignFeature') : t('editFeature')}
      eyebrow={`${plan.name} / ${plan.code}`}
      onClose={onClose}
    >
      <form className="management-form" onSubmit={(event) => void submit(event)}>
        <label className="form-field">
          <span>{t('featureColumn')}</span>
          {mode === 'assign' ? (
            <select
              className="form-select"
              value={featureCode}
              onChange={(event) => setFeatureCode(event.target.value)}
              required
            >
              {availableFeatures.map((item) => (
                <option key={item.code} value={item.code}>
                  {featureName(item, t)} / {item.code}
                </option>
              ))}
            </select>
          ) : (
            <input className="form-control mono" value={featureCode} disabled />
          )}
        </label>

        <TextField
          label={t('addOnPrice')}
          value={addOnPrice}
          onChange={setAddOnPrice}
          type="number"
          min="0"
          step="0.01"
        />

        {quotaRows.length > 0 ? (
          <fieldset className="quota-form-list">
            <legend>{t('quotaPolicy')}</legend>
            <p className="quota-form-help">{t('quotaPolicyHelp')}</p>
            {quotaRows.map((row, index) => (
              <div className="quota-form-row" key={row.resource}>
                <div className="quota-form-resource">
                  <span className={`quota-policy-badge ${quotaPolicyClass(row)}`}>
                    {quotaPolicyLabel(row, t)}
                  </span>
                  <strong className="mono">{row.resource}</strong>
                  <span>{row.unit}</span>
                </div>
                <TextField
                  label={t('includedLimit')}
                  value={row.limit}
                  onChange={(value) =>
                    updateQuotaRow(setQuotaRows, index, {
                      limit: value,
                      pricePerUnit: value.trim() ? row.pricePerUnit : '',
                    })
                  }
                  type="number"
                  min="0"
                  help={t('includedLimitHelp')}
                />
                <TextField
                  label={t('extraUnitPrice')}
                  value={row.pricePerUnit}
                  onChange={(value) => updateQuotaRow(setQuotaRows, index, { pricePerUnit: value })}
                  type="number"
                  min="0"
                  step="0.01"
                  disabled={!row.limit.trim()}
                  help={row.limit.trim() ? t('extraUnitPriceHelp') : t('extraUnitPriceDisabledHelp')}
                />
              </div>
            ))}
          </fieldset>
        ) : null}

        <DialogActions
          onClose={onClose}
          submitting={submitting}
          submitLabel={mode === 'assign' ? t('assignFeature') : t('save')}
        />
      </form>
    </PlanDialog>
  )
}

function ConfirmRemoveDialog({
  label,
  onClose,
  onConfirm,
  onError,
}: {
  label: string
  onClose: () => void
  onConfirm: () => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [submitting, setSubmitting] = useState(false)

  async function confirm() {
    setSubmitting(true)
    try {
      await onConfirm()
    } catch (requestError) {
      onError(requestMessage(requestError, t('forbidden')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PlanDialog title={t('removeFeature')} eyebrow={label} onClose={onClose}>
      <div className="management-form">
        <div className="notice notice-danger mb-0">{t('removeFeatureConfirm')}</div>
        <DialogActions onClose={onClose} submitting={submitting} submitLabel={t('removeFeature')} onSubmit={confirm} danger />
      </div>
    </PlanDialog>
  )
}

function PlanDialog({
  title,
  eyebrow,
  children,
  onClose,
}: {
  title: string
  eyebrow?: string
  children: ReactNode
  onClose: () => void
}) {
  return (
    <div className="feature-dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="feature-dialog operator-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="plan-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="feature-dialog-header">
          <div>
            {eyebrow ? <div className="work-kicker">{eyebrow}</div> : null}
            <h2 id="plan-dialog-title">{title}</h2>
          </div>
          <button type="button" className="btn btn-outline-secondary btn-icon" onClick={onClose}>
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </header>
        <div className="feature-dialog-body">{children}</div>
      </section>
    </div>
  )
}

function TextField({
  label,
  value,
  onChange,
  type = 'text',
  required,
  min,
  step,
  disabled,
  help,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  required?: boolean
  min?: string
  step?: string
  disabled?: boolean
  help?: string
}) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <input
        className="form-control"
        value={value}
        type={type}
        required={required}
        min={min}
        step={step}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
      {help ? <small>{help}</small> : null}
    </label>
  )
}

function DialogActions({
  onClose,
  submitting,
  submitLabel,
  onSubmit,
  danger,
}: {
  onClose: () => void
  submitting: boolean
  submitLabel: string
  onSubmit?: () => void
  danger?: boolean
}) {
  const { t } = useI18n()
  return (
    <div className="dialog-actions">
      <button type="button" className="btn btn-outline-secondary" onClick={onClose}>
        {t('cancel')}
      </button>
      <button
        type={onSubmit ? 'button' : 'submit'}
        className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`}
        disabled={submitting}
        onClick={onSubmit}
      >
        {submitting ? <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" /> : null}
        {submitLabel}
      </button>
    </div>
  )
}

function StatusPill({ active }: { active: boolean }) {
  const { t } = useI18n()
  return <span className={`pill ${active ? 'pill-green' : 'pill-neutral'}`}>{active ? t('active') : t('inactive')}</span>
}

type QuotaFormRow = {
  resource: string
  unit: string
  limit: string
  pricePerUnit: string
}

function buildQuotaRows(feature: PlatformFeature | null, configs: PlanQuotaConfig[]) {
  if (!feature) return []
  return feature.quotaSchema.map((slot) => {
    const existing = configs.find((config) => config.resource === slot.resource)
    return {
      resource: slot.resource ?? '',
      unit: slot.unit ?? slot.type ?? '',
      limit: existing?.limit === null || existing?.limit === undefined ? '' : String(existing.limit),
      pricePerUnit:
        existing?.pricePerUnit === null || existing?.pricePerUnit === undefined
          ? ''
          : String(existing.pricePerUnit),
    }
  })
}

function updateQuotaRow(
  setRows: Dispatch<SetStateAction<QuotaFormRow[]>>,
  index: number,
  patch: Partial<QuotaFormRow>,
) {
  setRows((rows) => rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)))
}

function quotaPolicyLabel(row: QuotaFormRow, t: (key: string) => string) {
  if (!row.limit.trim()) {
    return t('quotaPolicyUnlimited')
  }
  if (!row.pricePerUnit.trim()) {
    return t('quotaPolicyFixed')
  }
  return t('quotaPolicyExpandable')
}

function quotaPolicyClass(row: QuotaFormRow) {
  if (!row.limit.trim()) {
    return 'is-unlimited'
  }
  if (!row.pricePerUnit.trim()) {
    return 'is-fixed'
  }
  return 'is-expandable'
}

function normalizeUpdatedPlan(current: AdminPlan, updated: AdminPlan) {
  return {
    ...current,
    ...updated,
    active: updated.active ?? updated.isActive ?? current.active ?? current.isActive,
    isActive: updated.isActive ?? updated.active ?? current.isActive ?? current.active,
  }
}

function parseMoney(value: string) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error('Invalid price')
  }
  return parsed
}

function parseInteger(value: string) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error('Invalid quota')
  }
  return parsed
}

function requestMessage(error: unknown, fallback: string) {
  const backendMessage = axios.isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof backendMessage === 'string' ? backendMessage : fallback
}
