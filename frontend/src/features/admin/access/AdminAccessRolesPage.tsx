import axios from 'axios'
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useI18n } from '../../../lib/i18n-context'
import { ADMIN_PERMISSIONS } from '../admin-permissions'
import { useAdminSession } from '../auth/AdminSessionContext'
import {
  createAdminRole,
  getAdminRoles,
  getPlatformAdminPermissionCatalog,
  grantAdminRolePermission,
  revokeAdminRolePermission,
  toggleAdminRoleActive,
  updateAdminRole,
  type AdminRoleRecord,
  type PermissionCatalogModule,
} from './api'

type Notice = { tone: 'danger' | 'success' | 'warning'; text: string }
type RoleModalState =
  | { kind: 'create' }
  | { kind: 'edit'; role: AdminRoleRecord }

export function AdminAccessRolesPage() {
  const { t } = useI18n()
  const { user, hasPermission } = useAdminSession()
  const [roles, setRoles] = useState<AdminRoleRecord[]>([])
  const [catalog, setCatalog] = useState<PermissionCatalogModule[]>([])
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [modal, setModal] = useState<RoleModalState | null>(null)

  const isSuperAdmin = Boolean(user?.isSuperAdmin)
  const canCreate = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_ROLES_CREATE)
  const canUpdate = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_ROLES_UPDATE)
  const canToggle = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_ROLES_TOGGLE)
  const canGrant = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_ROLES_GRANT_PERMISSION)
  const canRevoke = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_ROLES_REVOKE_PERMISSION)

  const load = useCallback(async () => {
    setLoading(true)
    setNotice(null)
    try {
      const [roleResponse, catalogResponse] = await Promise.allSettled([
        getAdminRoles(),
        getPlatformAdminPermissionCatalog(),
      ])

      if (roleResponse.status === 'fulfilled') {
        setRoles(roleResponse.value)
        setSelectedRoleId((current) => {
          if (current && roleResponse.value.some((role) => role.id === current)) return current
          return roleResponse.value[0]?.id ?? null
        })
      } else {
        throw roleResponse.reason
      }

      if (catalogResponse.status === 'fulfilled') {
        setCatalog(catalogResponse.value)
      } else {
        setCatalog([])
        setNotice({ tone: 'warning', text: requestMessage(catalogResponse.reason, t('permissionCatalogUnavailable')) })
      }
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('adminAccessLoadFailed')) })
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void load()
  }, [load])

  const filteredRoles = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return roles
    return roles.filter((role) =>
      [role.name, role.description ?? '', ...role.permissions.map((permission) => permission.code)]
        .some((value) => value.toLowerCase().includes(normalized)),
    )
  }, [query, roles])

  const selectedRole = useMemo(
    () => roles.find((role) => role.id === selectedRoleId) ?? null,
    [roles, selectedRoleId],
  )

  const activeRoleCount = roles.filter((role) => role.isActive).length
  const permissionCount = selectedRole?.permissions.length ?? 0

  async function refreshRoles() {
    await load()
  }

  async function handleCreate(request: { name: string; description: string | null }) {
    const created = await createAdminRole(request)
    setModal(null)
    setNotice({ tone: 'success', text: t('adminRoleCreated') })
    await refreshRoles()
    setSelectedRoleId(created.id)
  }

  async function handleUpdate(role: AdminRoleRecord, request: { name: string; description: string | null }) {
    const updated = await updateAdminRole(role.id, request)
    setModal(null)
    setNotice({ tone: 'success', text: t('adminRoleUpdated') })
    await refreshRoles()
    setSelectedRoleId(updated.id)
  }

  async function handleToggle(role: AdminRoleRecord) {
    setSavingKey(`role:${role.id}`)
    setNotice(null)
    try {
      await toggleAdminRoleActive(role.id)
      setNotice({ tone: 'success', text: t('adminRoleStatusSaved') })
      await refreshRoles()
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('forbidden')) })
    } finally {
      setSavingKey(null)
    }
  }

  async function handlePermissionToggle(role: AdminRoleRecord, permissionId: string, enabled: boolean) {
    setSavingKey(`${role.id}:${permissionId}`)
    setNotice(null)
    try {
      if (enabled) {
        await grantAdminRolePermission(role.id, permissionId)
      } else {
        await revokeAdminRolePermission(role.id, permissionId)
      }
      await refreshRoles()
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('forbidden')) })
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <section className="admin-work-page" aria-labelledby="admin-roles-title">
      <header className="work-header">
        <div>
          <div className="work-kicker">{t('adminAccess')}</div>
          <h1 id="admin-roles-title" className="page-title mb-0">
            {t('adminRoles')}
          </h1>
        </div>
        <div className="page-actions">
          {canCreate ? (
            <button type="button" className="btn btn-primary" onClick={() => setModal({ kind: 'create' })}>
              <i className="bi bi-plus-lg me-2" aria-hidden="true" />
              {t('newAdminRole')}
            </button>
          ) : null}
          <button type="button" className="btn btn-dark" onClick={() => void refreshRoles()}>
            <i className="bi bi-arrow-clockwise me-2" aria-hidden="true" />
            {t('refresh')}
          </button>
        </div>
      </header>

      {notice ? (
        <div className={`notice notice-${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'}>
          {notice.text}
        </div>
      ) : null}

      <section className="access-summary-grid" aria-label={t('overview')}>
        <Metric label={t('adminRoles')} value={roles.length} icon="bi-diagram-3" />
        <Metric label={t('active')} value={activeRoleCount} icon="bi-check2-circle" />
        <Metric label={t('selectedPermissions')} value={permissionCount} icon="bi-key" />
      </section>

      <div className="access-workspace">
        <section className="operator-panel access-index-panel" aria-label={t('adminRoles')}>
          <div className="access-toolbar">
            <label className="search-field">
              <i className="bi bi-search" aria-hidden="true" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t('searchAdminRoles')}
                aria-label={t('searchAdminRoles')}
              />
            </label>
          </div>

          {loading ? (
            <div className="table-state">
              <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
              {t('loading')}
            </div>
          ) : filteredRoles.length === 0 ? (
            <div className="table-state">{t('empty')}</div>
          ) : (
            <div className="access-list" role="list">
              {filteredRoles.map((role) => (
                <button
                  type="button"
                  className={`access-list-item ${role.id === selectedRoleId ? 'is-selected' : ''}`}
                  key={role.id}
                  onClick={() => setSelectedRoleId(role.id)}
                >
                  <span>
                    <strong>{role.name}</strong>
                    <small>{role.description || t('noDescription')}</small>
                  </span>
                  <span className="access-list-meta">
                    <StatusPill active={role.isActive} />
                    <span className="usage-token">
                      <i className="bi bi-key me-1" aria-hidden="true" />
                      {role.permissions.length}
                    </span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="operator-panel access-detail-panel" aria-label={t('details')}>
          {selectedRole ? (
            <>
              <div className="access-detail-header">
                <div>
                  <div className="work-kicker">{t('selectedRole')}</div>
                  <h2>{selectedRole.name}</h2>
                  <p>{selectedRole.description || t('noDescription')}</p>
                </div>
                <div className="access-detail-actions">
                  <StatusPill active={selectedRole.isActive} />
                  {canUpdate ? (
                    <button
                      type="button"
                      className="btn btn-outline-secondary btn-sm"
                      onClick={() => setModal({ kind: 'edit', role: selectedRole })}
                    >
                      <i className="bi bi-pencil-square me-2" aria-hidden="true" />
                      {t('edit')}
                    </button>
                  ) : null}
                  {canToggle ? (
                    <button
                      type="button"
                      className={`btn btn-sm ${selectedRole.isActive ? 'btn-outline-danger' : 'btn-outline-primary'}`}
                      disabled={savingKey === `role:${selectedRole.id}`}
                      onClick={() => void handleToggle(selectedRole)}
                    >
                      {selectedRole.isActive ? t('deactivate') : t('activate')}
                    </button>
                  ) : null}
                </div>
              </div>

              <PermissionMatrix
                role={selectedRole}
                catalog={catalog}
                canGrant={canGrant}
                canRevoke={canRevoke}
                savingKey={savingKey}
                onToggle={(permissionId, enabled) => void handlePermissionToggle(selectedRole, permissionId, enabled)}
              />
            </>
          ) : (
            <div className="table-state">{t('selectRole')}</div>
          )}
        </section>
      </div>

      {modal?.kind === 'create' ? (
        <RoleDialog
          title={t('newAdminRole')}
          onClose={() => setModal(null)}
          onSubmit={handleCreate}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}

      {modal?.kind === 'edit' ? (
        <RoleDialog
          title={t('editRole')}
          role={modal.role}
          onClose={() => setModal(null)}
          onSubmit={(request) => handleUpdate(modal.role, request)}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}
    </section>
  )
}

function PermissionMatrix({
  role,
  catalog,
  canGrant,
  canRevoke,
  savingKey,
  onToggle,
}: {
  role: AdminRoleRecord
  catalog: PermissionCatalogModule[]
  canGrant: boolean
  canRevoke: boolean
  savingKey: string | null
  onToggle: (permissionId: string, enabled: boolean) => void
}) {
  const { t } = useI18n()
  const granted = useMemo(() => new Set(role.permissions.map((permission) => permission.code)), [role.permissions])

  if (catalog.length === 0) {
    return (
      <section className="access-permission-section">
        <div className="section-title-row">
          <h3>{t('permissions')}</h3>
          <span className="pill pill-neutral">{role.permissions.length}</span>
        </div>
        <div className="dialog-list">
          {role.permissions.length === 0 ? (
            <div className="table-state">{t('noPermissions')}</div>
          ) : (
            role.permissions.map((permission) => (
              <div className="dialog-list-row" key={permission.id}>
                <div>
                  <strong>{permission.code}</strong>
                  <span>{permission.description || permission.name}</span>
                </div>
              </div>
            ))
          )}
        </div>
      </section>
    )
  }

  return (
    <section className="access-permission-section">
      <div className="section-title-row">
        <h3>{t('permissionMatrix')}</h3>
        <span className="pill pill-neutral">{role.permissions.length}</span>
      </div>
      <div className="permission-matrix">
        {catalog.flatMap((module) =>
          module.features.map((feature) => (
            <section className="permission-group" key={feature.code}>
              <header>
                <div>
                  <strong>{feature.displayName || feature.code}</strong>
                  <span className="mono">{feature.code}</span>
                </div>
                <span>{feature.permissions.length}</span>
              </header>
              <div className="permission-toggle-list">
                {feature.permissions.map((permission) => {
                  const checked = granted.has(permission.code)
                  const disabled = checked ? !canRevoke : !canGrant
                  return (
                    <label className="permission-toggle-row" key={permission.id}>
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={checked}
                        disabled={disabled || savingKey === `${role.id}:${permission.id}`}
                        onChange={(event) => onToggle(permission.id, event.target.checked)}
                      />
                      <span>
                        <strong>{permission.code}</strong>
                        <small>{permission.description || permission.name}</small>
                      </span>
                    </label>
                  )
                })}
              </div>
            </section>
          )),
        )}
      </div>
    </section>
  )
}

function RoleDialog({
  title,
  role,
  onClose,
  onSubmit,
  onError,
}: {
  title: string
  role?: AdminRoleRecord
  onClose: () => void
  onSubmit: (request: { name: string; description: string | null }) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [name, setName] = useState(role?.name ?? '')
  const [description, setDescription] = useState(role?.description ?? '')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await onSubmit({ name: name.trim(), description: description.trim() || null })
    } catch (error) {
      onError(requestMessage(error, t('forbidden')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AccessDialog title={title} onClose={onClose}>
      <form className="management-form" onSubmit={(event) => void submit(event)}>
        <label className="form-field">
          <span>{t('name')}</span>
          <input className="form-control" value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <label className="form-field">
          <span>{t('description')}</span>
          <input className="form-control" value={description} onChange={(event) => setDescription(event.target.value)} />
        </label>
        <DialogActions onClose={onClose} submitting={submitting} submitLabel={t('save')} />
      </form>
    </AccessDialog>
  )
}

function Metric({ label, value, icon }: { label: string; value: number; icon: string }) {
  return (
    <div className="access-metric">
      <i className={`bi ${icon}`} aria-hidden="true" />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function AccessDialog({
  title,
  children,
  onClose,
}: {
  title: string
  children: ReactNode
  onClose: () => void
}) {
  return (
    <div className="feature-dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="feature-dialog operator-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="access-role-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="feature-dialog-header">
          <h2 id="access-role-dialog-title">{title}</h2>
          <button type="button" className="btn btn-outline-secondary btn-icon" onClick={onClose}>
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </header>
        <div className="feature-dialog-body">{children}</div>
      </section>
    </div>
  )
}

function DialogActions({
  onClose,
  submitting,
  submitLabel,
}: {
  onClose: () => void
  submitting: boolean
  submitLabel: string
}) {
  const { t } = useI18n()
  return (
    <div className="dialog-actions">
      <button type="button" className="btn btn-outline-secondary" onClick={onClose}>
        {t('cancel')}
      </button>
      <button type="submit" className="btn btn-primary" disabled={submitting}>
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

function requestMessage(error: unknown, fallback: string) {
  const backendMessage = axios.isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof backendMessage === 'string' ? backendMessage : fallback
}
