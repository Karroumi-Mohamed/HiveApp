import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { LanguageToggle } from '../../../components/LanguageToggle'
import { useI18n } from '../../../lib/i18n-context'
import { ADMIN_PERMISSIONS } from '../admin-permissions'
import { useAdminSession } from '../auth/AdminSessionContext'

export function AdminLayout() {
  const { t } = useI18n()
  const { user, signOut, hasPermission } = useAdminSession()
  const location = useLocation()
  const plansActive = location.pathname.startsWith('/admin/plans')
  const accessActive = location.pathname.startsWith('/admin/access')
  const [plansOpen, setPlansOpen] = useState(plansActive)
  const [accessOpen, setAccessOpen] = useState(accessActive)
  const canViewAccess =
    Boolean(user?.isSuperAdmin) ||
    hasPermission(ADMIN_PERMISSIONS.ADMIN_USERS_READ) ||
    hasPermission(ADMIN_PERMISSIONS.ADMIN_ROLES_READ)

  useEffect(() => {
    if (plansActive) {
      setPlansOpen(true)
    }
  }, [plansActive])

  useEffect(() => {
    if (accessActive) {
      setAccessOpen(true)
    }
  }, [accessActive])

  return (
    <div className="admin-shell d-lg-flex">
      <a href="#main-content" className="skip-link btn btn-dark btn-sm">
        {t('skipToContent')}
      </a>

      <aside className="admin-sidebar d-none d-lg-flex flex-column p-3">
        <div className="d-flex align-items-center gap-3 pb-3 mb-3 border-bottom">
          <div className="brand-mark" aria-hidden="true">
            H
          </div>
          <div className="min-w-0">
            <div className="fw-semibold">{t('appName')}</div>
            <div className="small text-secondary text-truncate">{user?.email}</div>
          </div>
        </div>

        <nav className="admin-nav" aria-label="Admin navigation">
          <div className="nav-section-label">{t('catalogGroup')}</div>
          <NavLink
            to="/admin/features"
            className={({ isActive }) =>
              `nav-rail-link d-flex align-items-center gap-2 px-3 ${isActive ? 'active' : ''}`
            }
          >
            <i className="bi bi-grid-1x2" aria-hidden="true" />
            <span>{t('features')}</span>
          </NavLink>

          {canViewAccess ? (
            <>
              <div className="nav-section-label mt-3">{t('adminAccess')}</div>
              <button
                type="button"
                className={`nav-rail-link nav-group-trigger d-flex align-items-center gap-2 px-3 ${accessActive ? 'active' : ''}`}
                aria-expanded={accessOpen}
                aria-controls="access-nav-group"
                onClick={() => setAccessOpen((current) => !current)}
              >
                <i className="bi bi-shield-lock" aria-hidden="true" />
                <span>{t('adminAccess')}</span>
                <i className={`bi ${accessOpen ? 'bi-chevron-up' : 'bi-chevron-down'} ms-auto`} aria-hidden="true" />
              </button>
              {accessOpen ? (
                <div id="access-nav-group" className="nav-subtree">
                  <NavLink
                    to="/admin/access/members"
                    className={({ isActive }) => `nav-sub-link ${isActive ? 'active' : ''}`}
                  >
                    {t('adminMembers')}
                  </NavLink>
                  <NavLink
                    to="/admin/access/roles"
                    className={({ isActive }) => `nav-sub-link ${isActive ? 'active' : ''}`}
                  >
                    {t('adminRoles')}
                  </NavLink>
                </div>
              ) : null}
            </>
          ) : null}

          <div className="nav-section-label mt-3">{t('billingGroup')}</div>
          <button
            type="button"
            className={`nav-rail-link nav-group-trigger d-flex align-items-center gap-2 px-3 ${plansActive ? 'active' : ''}`}
            aria-expanded={plansOpen}
            aria-controls="plans-nav-group"
            onClick={() => setPlansOpen((current) => !current)}
          >
            <i className="bi bi-credit-card-2-front" aria-hidden="true" />
            <span>{t('plans')}</span>
            <i className={`bi ${plansOpen ? 'bi-chevron-up' : 'bi-chevron-down'} ms-auto`} aria-hidden="true" />
          </button>
          {plansOpen ? (
            <div id="plans-nav-group" className="nav-subtree">
              <NavLink
                to="/admin/plans/templates"
                className={({ isActive }) => `nav-sub-link ${isActive ? 'active' : ''}`}
              >
                {t('planTemplates')}
              </NavLink>
              <NavLink
                to="/admin/plans/features"
                className={({ isActive }) => `nav-sub-link ${isActive ? 'active' : ''}`}
              >
                {t('planFeatureMatrix')}
              </NavLink>
            </div>
          ) : null}
        </nav>

        <div className="mt-auto pt-3 border-top">
          <div className="small text-secondary mb-2">{t('account')}</div>
          <button type="button" className="btn btn-outline-secondary w-100" onClick={signOut}>
            <i className="bi bi-box-arrow-right me-2" aria-hidden="true" />
            {t('signOut')}
          </button>
        </div>
      </aside>

      <div className="admin-main flex-grow-1">
        <header className="admin-topbar d-flex align-items-center px-3 px-lg-4">
          <div className="mobile-nav-strip d-lg-none">
            <div className="brand-mark" aria-hidden="true">
              H
            </div>
            <NavLink to="/admin/features" className="btn btn-outline-secondary">
              {t('features')}
            </NavLink>
            {canViewAccess ? (
              <NavLink to="/admin/access/members" className="btn btn-outline-secondary">
                {t('adminAccess')}
              </NavLink>
            ) : null}
            <NavLink to="/admin/plans/templates" className="btn btn-outline-secondary">
              {t('planTemplates')}
            </NavLink>
            <NavLink to="/admin/plans/features" className="btn btn-outline-secondary">
              {t('planFeatureMatrix')}
            </NavLink>
          </div>

          <div className="ms-auto d-flex align-items-center gap-2 gap-md-3">
            <LanguageToggle />
            <div className="d-none d-md-block text-end">
              <div className="small text-secondary">{t('signedIn')}</div>
              <div className="fw-semibold">{user?.email}</div>
            </div>
            <button type="button" className="btn btn-outline-secondary btn-icon" onClick={signOut}>
              <i className="bi bi-box-arrow-right" aria-hidden="true" />
              <span className="visually-hidden">{t('signOut')}</span>
            </button>
          </div>
        </header>

        <main id="main-content" className="content-shell">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
