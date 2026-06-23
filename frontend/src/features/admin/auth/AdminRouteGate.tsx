import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useI18n } from '../../../lib/i18n-context'
import { useAdminSession } from './AdminSessionContext'

export function AdminRouteGate() {
  const location = useLocation()
  const { t } = useI18n()
  const { status } = useAdminSession()

  if (status === 'checking') {
    return (
      <div className="auth-page">
        <div className="d-flex align-items-center gap-3 text-secondary">
          <span className="spinner-border spinner-border-sm" aria-hidden="true" />
          <span>{t('loading')}</span>
        </div>
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/admin/login" state={{ from: location.pathname }} replace />
  }

  return <Outlet />
}
