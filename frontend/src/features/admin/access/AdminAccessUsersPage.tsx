import axios from 'axios'
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useI18n } from '../../../lib/i18n-context'
import { ADMIN_PERMISSIONS } from '../admin-permissions'
import { useAdminSession } from '../auth/AdminSessionContext'
import {
  assignAdminUserRole,
  createAdminUser,
  getAdminRoles,
  getAdminUsers,
  removeAdminUserRole,
  toggleAdminUserActive,
  type AdminRoleRecord,
  type AdminRoleSummary,
  type AdminUserRecord,
} from './api'

type Notice = { tone: 'danger' | 'success' | 'warning'; text: string }
type UserModalState =
  | { kind: 'create' }
  | { kind: 'assign-role'; user: AdminUserRecord }

export function AdminAccessUsersPage() {
  const { t } = useI18n()
  const { user, hasPermission } = useAdminSession()
  const [users, setUsers] = useState<AdminUserRecord[]>([])
  const [roles, setRoles] = useState<AdminRoleRecord[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [savingId, setSavingId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [modal, setModal] = useState<UserModalState | null>(null)

  const isSuperAdmin = Boolean(user?.isSuperAdmin)
  const canCreate = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_USERS_CREATE)
  const canToggle = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_USERS_TOGGLE)
  const canAssignRole = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_USERS_ASSIGN_ROLE)
  const canRemoveRole = isSuperAdmin || hasPermission(ADMIN_PERMISSIONS.ADMIN_USERS_REMOVE_ROLE)

  const load = useCallback(async () => {
    setLoading(true)
    setNotice(null)
    try {
      const [userResponse, roleResponse] = await Promise.all([getAdminUsers(), getAdminRoles()])
      setUsers(userResponse)
      setRoles(roleResponse)
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('adminAccessLoadFailed')) })
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void load()
  }, [load])

  const filteredUsers = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return users
    return users.filter((adminUser) =>
      [adminUser.email, adminUser.userId, ...adminUser.roles.map((role) => role.name)]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(normalized)),
    )
  }, [query, users])

  const activeCount = users.filter((adminUser) => adminUser.isActive).length
  const superAdminCount = users.filter((adminUser) => adminUser.isSuperAdmin).length

  async function refreshUsers() {
    await load()
  }

  async function handleCreate(request: { userId: string; isSuperAdmin: boolean }) {
    await createAdminUser(request)
    setModal(null)
    setNotice({ tone: 'success', text: t('adminUserCreated') })
    await refreshUsers()
  }

  async function handleToggle(adminUser: AdminUserRecord) {
    setSavingId(adminUser.id)
    setNotice(null)
    try {
      await toggleAdminUserActive(adminUser.id)
      setNotice({ tone: 'success', text: t('adminUserStatusSaved') })
      await refreshUsers()
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('forbidden')) })
    } finally {
      setSavingId(null)
    }
  }

  async function handleAssignRole(adminUser: AdminUserRecord, roleId: string) {
    setSavingId(adminUser.id)
    setNotice(null)
    try {
      await assignAdminUserRole(adminUser.id, roleId)
      setModal(null)
      setNotice({ tone: 'success', text: t('adminRoleAssigned') })
      await refreshUsers()
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('forbidden')) })
    } finally {
      setSavingId(null)
    }
  }

  async function handleRemoveRole(adminUser: AdminUserRecord, role: AdminRoleSummary) {
    setSavingId(`${adminUser.id}:${role.id}`)
    setNotice(null)
    try {
      await removeAdminUserRole(adminUser.id, role.id)
      setNotice({ tone: 'success', text: t('adminRoleRemoved') })
      await refreshUsers()
    } catch (error) {
      setNotice({ tone: 'danger', text: requestMessage(error, t('forbidden')) })
    } finally {
      setSavingId(null)
    }
  }

  return (
    <section className="admin-work-page" aria-labelledby="admin-users-title">
      <header className="work-header">
        <div>
          <div className="work-kicker">{t('adminAccess')}</div>
          <h1 id="admin-users-title" className="page-title mb-0">
            {t('adminMembers')}
          </h1>
        </div>
        <div className="page-actions">
          {canCreate ? (
            <button type="button" className="btn btn-primary" onClick={() => setModal({ kind: 'create' })}>
              <i className="bi bi-person-plus me-2" aria-hidden="true" />
              {t('newAdminMember')}
            </button>
          ) : null}
          <button type="button" className="btn btn-dark" onClick={() => void refreshUsers()}>
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
        <Metric label={t('adminMembers')} value={users.length} icon="bi-people" />
        <Metric label={t('active')} value={activeCount} icon="bi-person-check" />
        <Metric label={t('superAdmin')} value={superAdminCount} icon="bi-shield-lock" />
        <Metric label={t('adminRoles')} value={roles.length} icon="bi-diagram-3" />
      </section>

      <section className="operator-panel access-table-panel">
        <div className="access-toolbar">
          <label className="search-field">
            <i className="bi bi-search" aria-hidden="true" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t('searchAdminMembers')}
              aria-label={t('searchAdminMembers')}
            />
          </label>
        </div>

        {loading ? (
          <div className="table-state">
            <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
            {t('loading')}
          </div>
        ) : filteredUsers.length === 0 ? (
          <div className="table-state">{t('empty')}</div>
        ) : (
          <div className="access-row-list" role="list">
            {filteredUsers.map((adminUser) => {
              const isCurrent = adminUser.id === user?.id
              return (
                <article className="access-row" role="listitem" key={adminUser.id}>
                  <div className="access-row-main">
                    <div className="identity-avatar" aria-hidden="true">
                      {adminUser.email.slice(0, 1).toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <strong>{adminUser.email}</strong>
                      <span className="mono">{adminUser.userId}</span>
                    </div>
                  </div>
                  <div className="access-row-status">
                    <StatusPill active={adminUser.isActive} />
                    {adminUser.isSuperAdmin ? <span className="pill pill-amber">{t('superAdmin')}</span> : null}
                    {isCurrent ? <span className="pill pill-neutral">{t('currentSession')}</span> : null}
                  </div>
                  <div className="access-role-stack">
                    {adminUser.roles.length === 0 ? (
                      <span className="text-secondary">{t('noRoles')}</span>
                    ) : (
                      adminUser.roles.map((role) => (
                        <span className="role-chip" key={role.id}>
                          <span>{role.name}</span>
                          {canRemoveRole ? (
                            <button
                              type="button"
                              title={t('removeRole')}
                              disabled={savingId === `${adminUser.id}:${role.id}`}
                              onClick={() => void handleRemoveRole(adminUser, role)}
                            >
                              <i className="bi bi-x" aria-hidden="true" />
                              <span className="visually-hidden">{t('removeRole')}</span>
                            </button>
                          ) : null}
                        </span>
                      ))
                    )}
                  </div>
                  <div className="access-row-actions">
                    {canAssignRole ? (
                      <button
                        type="button"
                        className="btn btn-outline-secondary btn-sm"
                        title={t('assignRole')}
                        onClick={() => setModal({ kind: 'assign-role', user: adminUser })}
                      >
                        <i className="bi bi-diagram-3" aria-hidden="true" />
                        <span className="visually-hidden">{t('assignRole')}</span>
                      </button>
                    ) : null}
                    {canToggle ? (
                      <button
                        type="button"
                        className={`btn btn-sm ${adminUser.isActive ? 'btn-outline-danger' : 'btn-outline-primary'}`}
                        disabled={savingId === adminUser.id || isCurrent}
                        title={isCurrent ? t('cannotDeactivateSelf') : t('toggleActive')}
                        onClick={() => void handleToggle(adminUser)}
                      >
                        <i className={`bi ${adminUser.isActive ? 'bi-person-slash' : 'bi-person-check'}`} aria-hidden="true" />
                        <span className="visually-hidden">{t('toggleActive')}</span>
                      </button>
                    ) : null}
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>

      {modal?.kind === 'create' ? (
        <CreateAdminDialog
          onClose={() => setModal(null)}
          onSubmit={handleCreate}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}

      {modal?.kind === 'assign-role' ? (
        <AssignRoleDialog
          adminUser={modal.user}
          roles={roles}
          onClose={() => setModal(null)}
          onSubmit={(roleId) => handleAssignRole(modal.user, roleId)}
          onError={(message) => setNotice({ tone: 'danger', text: message })}
        />
      ) : null}
    </section>
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

function CreateAdminDialog({
  onClose,
  onSubmit,
  onError,
}: {
  onClose: () => void
  onSubmit: (request: { userId: string; isSuperAdmin: boolean }) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [userId, setUserId] = useState('')
  const [isSuperAdmin, setIsSuperAdmin] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await onSubmit({ userId: userId.trim(), isSuperAdmin })
    } catch (error) {
      onError(requestMessage(error, t('forbidden')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AccessDialog title={t('newAdminMember')} onClose={onClose}>
      <form className="management-form" onSubmit={(event) => void submit(event)}>
        <label className="form-field">
          <span>{t('userId')}</span>
          <input
            className="form-control mono"
            value={userId}
            onChange={(event) => setUserId(event.target.value)}
            required
          />
          <small>{t('adminUserIdHelp')}</small>
        </label>
        <label className="check-row">
          <input
            className="form-check-input"
            type="checkbox"
            checked={isSuperAdmin}
            onChange={(event) => setIsSuperAdmin(event.target.checked)}
          />
          <span>{t('makeSuperAdmin')}</span>
        </label>
        <DialogActions onClose={onClose} submitting={submitting} submitLabel={t('create')} />
      </form>
    </AccessDialog>
  )
}

function AssignRoleDialog({
  adminUser,
  roles,
  onClose,
  onSubmit,
  onError,
}: {
  adminUser: AdminUserRecord
  roles: AdminRoleRecord[]
  onClose: () => void
  onSubmit: (roleId: string) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const assigned = new Set(adminUser.roles.map((role) => role.id))
  const availableRoles = roles.filter((role) => role.isActive && !assigned.has(role.id))
  const [roleId, setRoleId] = useState(availableRoles[0]?.id ?? '')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!roleId) return
    setSubmitting(true)
    try {
      await onSubmit(roleId)
    } catch (error) {
      onError(requestMessage(error, t('forbidden')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AccessDialog title={t('assignRole')} eyebrow={adminUser.email} onClose={onClose}>
      <form className="management-form" onSubmit={(event) => void submit(event)}>
        <label className="form-field">
          <span>{t('adminRoles')}</span>
          <select
            className="form-select"
            value={roleId}
            disabled={availableRoles.length === 0}
            onChange={(event) => setRoleId(event.target.value)}
          >
            {availableRoles.length === 0 ? (
              <option value="">{t('noAssignableRoles')}</option>
            ) : (
              availableRoles.map((role) => (
                <option key={role.id} value={role.id}>
                  {role.name}
                </option>
              ))
            )}
          </select>
        </label>
        <DialogActions onClose={onClose} submitting={submitting} submitLabel={t('assignRole')} disabled={!roleId} />
      </form>
    </AccessDialog>
  )
}

function AccessDialog({
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
        aria-labelledby="access-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="feature-dialog-header">
          <div>
            {eyebrow ? <div className="work-kicker">{eyebrow}</div> : null}
            <h2 id="access-dialog-title">{title}</h2>
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

function DialogActions({
  onClose,
  submitting,
  submitLabel,
  disabled,
}: {
  onClose: () => void
  submitting: boolean
  submitLabel: string
  disabled?: boolean
}) {
  const { t } = useI18n()
  return (
    <div className="dialog-actions">
      <button type="button" className="btn btn-outline-secondary" onClick={onClose}>
        {t('cancel')}
      </button>
      <button type="submit" className="btn btn-primary" disabled={submitting || disabled}>
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
