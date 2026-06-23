import axios from 'axios'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { featureTranslationKey, useI18n } from '../../../lib/i18n-context'
import { ADMIN_PERMISSIONS } from '../admin-permissions'
import { useAdminSession } from '../auth/AdminSessionContext'
import {
  getPlatformFeatureCatalog,
  updatePlatformFeatureActive,
  type FeatureStatus,
  type FeatureSurface,
  type PlatformFeature,
  type PlatformFeatureModule,
  type QuotaSlot,
  type RegistryPermission,
} from './api'

type FeatureFilter = 'ALL' | 'CLIENT' | 'ADMIN' | 'PLANS' | 'CATALOG'
type FeatureDialogMode = 'permissions' | 'quotas' | 'usage'
type FeatureDialogState = {
  feature: PlatformFeature
  mode: FeatureDialogMode
} | null

export function AdminFeaturesPage() {
  const { t } = useI18n()
  const { user, hasPermission } = useAdminSession()
  const [modules, setModules] = useState<PlatformFeatureModule[]>([])
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<FeatureFilter>('ALL')
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [savingFeatureId, setSavingFeatureId] = useState<string | null>(null)
  const [dialog, setDialog] = useState<FeatureDialogState>(null)
  const [notice, setNotice] = useState<{ tone: 'danger' | 'success'; text: string } | null>(null)

  const canUpdateActive =
    Boolean(user?.isSuperAdmin) || hasPermission(ADMIN_PERMISSIONS.FEATURE_ACTIVE_UPDATE)

  const loadCatalog = useCallback(async (showRefresh = false) => {
    if (showRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setNotice(null)

    try {
      const response = await getPlatformFeatureCatalog()
      setModules(response)
      setDialog((current) => {
        if (!current) return null
        const nextFeature = flattenModules(response).find(
          (feature) => feature.code === current.feature.code,
        )
        return nextFeature ? { ...current, feature: nextFeature } : null
      })
    } catch (requestError) {
      setNotice({ tone: 'danger', text: requestMessage(requestError, t('loginFailed')) })
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [t])

  useEffect(() => {
    void loadCatalog()
  }, [loadCatalog])

  const features = useMemo(() => flattenModules(modules), [modules])
  const filteredFeatures = useMemo(
    () => filterFeatures(features, filter, search),
    [features, filter, search],
  )

  async function toggleFeatureActive(feature: PlatformFeature, active: boolean) {
    if (!feature.id || !canUpdateActive || !canEditActivation(feature) || savingFeatureId) {
      return
    }

    setSavingFeatureId(feature.id)
    setNotice(null)
    try {
      await updatePlatformFeatureActive(feature.id, active)
      setModules((current) =>
        current.map((module) => ({
          ...module,
          features: module.features.map((item) =>
            item.id === feature.id ? { ...item, active } : item,
          ),
        })),
      )
      setNotice({ tone: 'success', text: t('statusSaved') })
    } catch (requestError) {
      setNotice({ tone: 'danger', text: requestMessage(requestError, t('forbidden')) })
    } finally {
      setSavingFeatureId(null)
    }
  }

  return (
    <section className="features-page admin-work-page" aria-labelledby="features-page-title">
      <header className="work-header">
        <div>
          <div className="work-kicker">{t('catalogGroup')}</div>
          <h1 id="features-page-title" className="page-title mb-0">
            {t('features')}
          </h1>
        </div>
        <button
          type="button"
          className="btn btn-dark"
          onClick={() => void loadCatalog(true)}
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

      {notice ? (
        <div
          className={`notice notice-${notice.tone}`}
          role={notice.tone === 'danger' ? 'alert' : 'status'}
        >
          {notice.text}
        </div>
      ) : null}

      <section className="feature-grid-toolbar operator-panel" aria-label={t('filters')}>
        <label className="search-box" htmlFor="feature-search">
          <i className="bi bi-search" aria-hidden="true" />
          <input
            id="feature-search"
            value={search}
            placeholder={t('search')}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <div className="segmented-control compact" aria-label={t('filters')}>
          {filterOptions().map((option) => (
            <button
              key={option.value}
              type="button"
              className={filter === option.value ? 'active' : ''}
              onClick={() => setFilter(option.value)}
            >
              {t(option.label)}
            </button>
          ))}
        </div>
        <div className="feature-grid-count">
          <span>{t('featuresShown')}</span>
          <strong>
            {filteredFeatures.length}/{features.length}
          </strong>
        </div>
      </section>

      {loading ? (
        <div className="operator-panel table-state">
          <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
          {t('loading')}
        </div>
      ) : filteredFeatures.length === 0 ? (
        <div className="operator-panel table-state">{t('empty')}</div>
      ) : (
        <FeatureGrid
          features={filteredFeatures}
          canUpdateActive={canUpdateActive}
          savingFeatureId={savingFeatureId}
          onOpenDialog={(feature, mode) => setDialog({ feature, mode })}
          onToggleActive={(feature, active) => void toggleFeatureActive(feature, active)}
        />
      )}

      {dialog ? (
        <FeatureDialog
          feature={dialog.feature}
          mode={dialog.mode}
          onChangeMode={(mode) => setDialog({ feature: dialog.feature, mode })}
          onClose={() => setDialog(null)}
        />
      ) : null}
    </section>
  )
}

function FeatureGrid({
  features,
  canUpdateActive,
  savingFeatureId,
  onOpenDialog,
  onToggleActive,
}: {
  features: PlatformFeature[]
  canUpdateActive: boolean
  savingFeatureId: string | null
  onOpenDialog: (feature: PlatformFeature, mode: FeatureDialogMode) => void
  onToggleActive: (feature: PlatformFeature, active: boolean) => void
}) {
  return (
    <div className="feature-card-grid" role="list">
      {features.map((feature) => (
        <FeatureCard
          key={feature.code}
          feature={feature}
          canUpdateActive={canUpdateActive}
          saving={savingFeatureId === feature.id}
          onOpenDialog={onOpenDialog}
          onToggleActive={onToggleActive}
        />
      ))}
    </div>
  )
}

function FeatureCard({
  feature,
  canUpdateActive,
  saving,
  onOpenDialog,
  onToggleActive,
}: {
  feature: PlatformFeature
  canUpdateActive: boolean
  saving: boolean
  onOpenDialog: (feature: PlatformFeature, mode: FeatureDialogMode) => void
  onToggleActive: (feature: PlatformFeature, active: boolean) => void
}) {
  const { t } = useI18n()
  const editable = canUpdateActive && canEditActivation(feature)
  const issues = featureIssues(feature)

  return (
    <article className="feature-grid-card" role="listitem">
      <div className="feature-card-head">
        <div className="feature-card-title">
          <strong>{featureName(feature, t)}</strong>
          <span className="mono">{feature.code}</span>
        </div>
        <span className={`feature-state-dot ${activationDotClass(feature)}`} title={activationLabel(feature, t)} />
      </div>

      <div className="feature-card-status">
        <span className={`pill ${surfacePillClass(feature.surface)}`}>
          {t(`surface_${feature.surface}`)}
        </span>
        <span className={`pill ${lifecyclePillClass(feature.status)}`}>
          {lifecycleLabel(feature.status, t)}
        </span>
        <span className={`pill ${feature.active ? 'pill-green' : 'pill-neutral'}`}>
          {activationLabel(feature, t)}
        </span>
      </div>

      <div className="feature-card-usage" aria-label={t('availabilityColumn')}>
        <UsageBadge icon="bi-bag-check" label={t('planAssignable')} enabled={feature.planAssignable} />
        <UsageBadge icon="bi-window" label={t('publicCatalogVisible')} enabled={feature.publicCatalogVisible} />
        <UsageBadge icon="bi-person-badge" label={t('clientRoleGrantable')} enabled={feature.clientRoleGrantable} />
        <UsageBadge icon="bi-shield-lock" label={t('platformAdminRoleGrantable')} enabled={feature.platformAdminRoleGrantable} />
        <UsageBadge icon="bi-building-check" label={t('b2bDelegatable')} enabled={feature.b2bDelegatable} />
      </div>

      <div className="feature-card-metrics">
        <MetricButton
          icon="bi-key"
          label={t('permissions')}
          value={feature.permissions.length}
          tooltip={t('viewPermissions')}
          onClick={() => onOpenDialog(feature, 'permissions')}
        />
        <MetricButton
          icon="bi-speedometer2"
          label={t('quotas')}
          value={feature.quotaSchema.length}
          tooltip={t('viewQuotas')}
          onClick={() => onOpenDialog(feature, 'quotas')}
        />
        <MetricButton
          icon="bi-diagram-3"
          label={t('availabilityColumn')}
          value={availabilityItems(feature).length}
          tooltip={t('viewUsage')}
          onClick={() => onOpenDialog(feature, 'usage')}
        />
      </div>

      <div className="feature-card-actions" aria-label={t('featureActions')}>
        {canEditActivation(feature) ? (
          <button
            type="button"
            className="btn btn-outline-secondary btn-sm"
            disabled={!editable || saving}
            title={editable ? t('updateActivation') : t('forbidden')}
            onClick={() => onToggleActive(feature, !feature.active)}
          >
            {saving ? (
              <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
            ) : (
              <i className={`bi ${feature.active ? 'bi-pause-circle' : 'bi-play-circle'} me-2`} aria-hidden="true" />
            )}
            {feature.active ? t('inactive') : t('active')}
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-outline-secondary btn-sm"
            disabled
            title={t('technicalLocked')}
          >
            <i className="bi bi-lock me-2" aria-hidden="true" />
            {t('codeLocked')}
          </button>
        )}
        {issues.length > 0 ? (
          <button
            type="button"
            className="btn btn-outline-warning btn-sm"
            title={issues.map((issue) => t(issue)).join(', ')}
            onClick={() => onOpenDialog(feature, 'usage')}
          >
            <i className="bi bi-exclamation-triangle me-2" aria-hidden="true" />
            {issues.length}
          </button>
        ) : null}
      </div>
    </article>
  )
}

function UsageBadge({ icon, label, enabled }: { icon: string; label: string; enabled: boolean }) {
  return (
    <span className={`feature-usage-badge ${enabled ? 'is-on' : 'is-off'}`} title={label}>
      <i className={`bi ${icon}`} aria-hidden="true" />
      <span>{label}</span>
    </span>
  )
}

function MetricButton({
  icon,
  label,
  value,
  tooltip,
  onClick,
}: {
  icon: string
  label: string
  value: number
  tooltip: string
  onClick: () => void
}) {
  return (
    <button type="button" className="feature-metric-button" title={tooltip} onClick={onClick}>
      <span>
        <i className={`bi ${icon}`} aria-hidden="true" />
        {label}
      </span>
      <strong>{value}</strong>
    </button>
  )
}

function FeatureDialog({
  feature,
  mode,
  onChangeMode,
  onClose,
}: {
  feature: PlatformFeature
  mode: FeatureDialogMode
  onChangeMode: (mode: FeatureDialogMode) => void
  onClose: () => void
}) {
  const { t } = useI18n()

  return (
    <div className="feature-dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="feature-dialog operator-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="feature-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="feature-dialog-header">
          <div>
            <div className="work-kicker">{t('featureInspector')}</div>
            <h2 id="feature-dialog-title">{featureName(feature, t)}</h2>
            <span className="mono">{feature.code}</span>
          </div>
          <button type="button" className="btn btn-outline-secondary btn-icon" onClick={onClose}>
            <i className="bi bi-x-lg" aria-hidden="true" />
            <span className="visually-hidden">{t('closeDetails')}</span>
          </button>
        </header>

        <div className="feature-dialog-tabs" role="tablist" aria-label={t('details')}>
          <DialogTab mode="permissions" activeMode={mode} label={t('permissions')} onChange={onChangeMode} />
          <DialogTab mode="quotas" activeMode={mode} label={t('quotas')} onChange={onChangeMode} />
          <DialogTab mode="usage" activeMode={mode} label={t('availabilityColumn')} onChange={onChangeMode} />
        </div>

        <div className="feature-dialog-body">
          {mode === 'permissions' ? <PermissionList permissions={feature.permissions} /> : null}
          {mode === 'quotas' ? <QuotaList quotas={feature.quotaSchema} /> : null}
          {mode === 'usage' ? <UsageDetails feature={feature} /> : null}
        </div>
      </section>
    </div>
  )
}

function DialogTab({
  mode,
  activeMode,
  label,
  onChange,
}: {
  mode: FeatureDialogMode
  activeMode: FeatureDialogMode
  label: string
  onChange: (mode: FeatureDialogMode) => void
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={activeMode === mode}
      className={activeMode === mode ? 'active' : ''}
      onClick={() => onChange(mode)}
    >
      {label}
    </button>
  )
}

function PermissionList({ permissions }: { permissions: RegistryPermission[] }) {
  const { t } = useI18n()

  if (permissions.length === 0) {
    return <div className="empty-detail-line">{t('noPermissions')}</div>
  }

  return (
    <div className="dialog-list" role="list" aria-label={t('permissionInventory')}>
      {permissions.map((permission) => (
        <div className="dialog-list-row" key={permission.id} role="listitem">
          <div>
            <strong className="mono">{permission.code}</strong>
            {permission.description ? <span>{permission.description}</span> : null}
          </div>
          <span className="pill pill-neutral">{permission.action ?? '-'}</span>
        </div>
      ))}
    </div>
  )
}

function QuotaList({ quotas }: { quotas: QuotaSlot[] }) {
  const { t } = useI18n()

  if (quotas.length === 0) {
    return <div className="empty-detail-line">{t('noQuotas')}</div>
  }

  return (
    <div className="dialog-list" role="list" aria-label={t('quotaInventory')}>
      {quotas.map((quota, index) => (
        <div className="dialog-list-row" key={`${quota.resource ?? 'quota'}-${index}`} role="listitem">
          <div>
            <strong className="mono">{quota.resource ?? '-'}</strong>
            <span>{quota.unit ?? '-'}</span>
          </div>
          <span className="pill pill-neutral">{quota.type ?? '-'}</span>
        </div>
      ))}
    </div>
  )
}

function UsageDetails({ feature }: { feature: PlatformFeature }) {
  const { t } = useI18n()
  const items = [
    { icon: 'bi-bag-check', label: 'planAssignable', enabled: feature.planAssignable },
    { icon: 'bi-window', label: 'publicCatalogVisible', enabled: feature.publicCatalogVisible },
    { icon: 'bi-person-badge', label: 'clientRoleGrantable', enabled: feature.clientRoleGrantable },
    {
      icon: 'bi-shield-lock',
      label: 'platformAdminRoleGrantable',
      enabled: feature.platformAdminRoleGrantable,
    },
    { icon: 'bi-building-check', label: 'b2bDelegatable', enabled: feature.b2bDelegatable },
    {
      icon: 'bi-toggles2',
      label: 'operationsActivationToggleable',
      enabled: feature.operationsActivationToggleable,
    },
    { icon: 'bi-database-check', label: 'registryPresent', enabled: feature.registryPresent },
  ]

  return (
    <div className="usage-detail-grid">
      {items.map((item) => (
        <div className="usage-detail-item" key={item.label}>
          <i className={`bi ${item.icon}`} aria-hidden="true" />
          <span>{t(item.label)}</span>
          <strong className={item.enabled ? 'text-success' : 'text-secondary'}>
            {item.enabled ? t('yes') : t('no')}
          </strong>
        </div>
      ))}
    </div>
  )
}

function flattenModules(modules: PlatformFeatureModule[]) {
  return modules.flatMap((module) =>
    module.features.map((feature) => ({
      ...feature,
      moduleCode: feature.moduleCode || module.code,
    })),
  )
}

function filterFeatures(features: PlatformFeature[], filter: FeatureFilter, search: string) {
  const query = search.trim().toLowerCase()
  return features.filter((feature) => {
    const filterMatch =
      filter === 'ALL' ||
      (filter === 'CLIENT' && feature.surface === 'CLIENT_WORKSPACE') ||
      (filter === 'ADMIN' && feature.surface === 'PLATFORM_CONTROL') ||
      (filter === 'PLANS' && feature.planAssignable) ||
      (filter === 'CATALOG' && feature.publicCatalogVisible)

    if (!filterMatch) {
      return false
    }
    if (!query) {
      return true
    }

    return [
      feature.code,
      feature.displayName,
      feature.moduleCode,
      feature.featureKey,
      ...feature.permissions.map((permission) => permission.code),
    ]
      .join(' ')
      .toLowerCase()
      .includes(query)
  })
}

function filterOptions(): Array<{ value: FeatureFilter; label: string }> {
  return [
    { value: 'ALL', label: 'all' },
    { value: 'CLIENT', label: 'client' },
    { value: 'ADMIN', label: 'admin' },
    { value: 'PLANS', label: 'plans' },
    { value: 'CATALOG', label: 'catalog' },
  ]
}

function availabilityItems(feature: PlatformFeature) {
  const items: string[] = []
  if (feature.planAssignable) items.push('planAssignable')
  if (feature.publicCatalogVisible) items.push('publicCatalogVisible')
  if (feature.operationsActivationToggleable) items.push('operationsActivationToggleable')
  if (feature.clientRoleGrantable) items.push('clientRoleGrantable')
  if (feature.platformAdminRoleGrantable) items.push('platformAdminRoleGrantable')
  if (feature.b2bDelegatable) items.push('b2bDelegatable')
  return items
}

function featureIssues(feature: PlatformFeature) {
  const issues: string[] = []
  if (!feature.registryPresent) issues.push('missingDb')
  if (!feature.active) issues.push('featureInactive')
  if (feature.permissions.length === 0) issues.push('noPermissionLinks')
  return issues
}

function featureName(feature: PlatformFeature, t: (key: string, fallback?: string) => string) {
  return t(featureTranslationKey(feature.code), feature.displayName)
}

function canEditActivation(feature: PlatformFeature) {
  return (
    feature.publicCatalogVisible &&
    feature.operationsActivationToggleable &&
    feature.registryPresent &&
    Boolean(feature.id)
  )
}

function lifecycleLabel(
  status: FeatureStatus | null,
  t: (key: string, fallback?: string) => string,
) {
  if (!status) return t('missingDb')
  if (status === 'PUBLIC') return t('status_PUBLIC')
  if (status === 'INTERNAL') return t('status_INTERNAL')
  if (status === 'BETA') return t('status_BETA')
  if (status === 'DEPRECATED') return t('status_DEPRECATED')
  return t('missingDb')
}

function activationLabel(feature: PlatformFeature, t: (key: string, fallback?: string) => string) {
  if (!feature.registryPresent) return t('missingDb')
  if (!feature.publicCatalogVisible) return t('activationLocked')
  if (!feature.operationsActivationToggleable) return t('codeLocked')
  return feature.active ? t('activeInCatalog') : t('inactiveInCatalog')
}

function surfacePillClass(surface: FeatureSurface) {
  if (surface === 'CLIENT_WORKSPACE') return 'pill-green'
  if (surface === 'PLATFORM_CONTROL') return 'pill-amber'
  if (surface === 'PUBLIC') return 'pill-blue'
  return 'pill-neutral'
}

function lifecyclePillClass(status: FeatureStatus | null) {
  if (status === 'PUBLIC') return 'pill-green'
  if (status === 'BETA') return 'pill-blue'
  if (status === 'DEPRECATED') return 'pill-red'
  return 'pill-neutral'
}

function activationDotClass(feature: PlatformFeature) {
  if (!feature.registryPresent) return 'dot-warning'
  if (!feature.publicCatalogVisible || !feature.operationsActivationToggleable) return 'dot-muted'
  return feature.active ? 'dot-ok' : 'dot-danger'
}

function requestMessage(error: unknown, fallback: string) {
  const backendMessage = axios.isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof backendMessage === 'string' ? backendMessage : fallback
}
