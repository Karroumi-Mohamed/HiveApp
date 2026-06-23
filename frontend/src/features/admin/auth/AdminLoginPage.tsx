import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { LanguageToggle } from '../../../components/LanguageToggle'
import { useI18n } from '../../../lib/i18n-context'
import { useAdminSession } from './AdminSessionContext'

export function AdminLoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { t } = useI18n()
  const { signIn, status } = useAdminSession()
  const [email, setEmail] = useState('admin@hiveapp.com')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to="/admin/features" replace />
  }

  const from = typeof location.state?.from === 'string' ? location.state.from : '/admin/features'

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      await signIn(email.trim(), password)
      navigate(from, { replace: true })
    } catch (requestError) {
      const backendMessage = axios.isAxiosError(requestError)
        ? requestError.response?.data?.message
        : undefined
      setError(typeof backendMessage === 'string' ? backendMessage : t('loginFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card p-4" aria-labelledby="admin-login-title">
        <div className="d-flex align-items-center justify-content-between gap-3 mb-4">
          <div className="d-flex align-items-center gap-3">
            <div className="brand-mark" aria-hidden="true">
              H
            </div>
            <div>
              <h1 id="admin-login-title" className="h3 mb-0">
                {t('loginTitle')}
              </h1>
              <div className="small text-secondary">{t('appName')}</div>
            </div>
          </div>
          <LanguageToggle />
        </div>

        <form onSubmit={onSubmit} noValidate>
          <div className="mb-3">
            <label className="form-label" htmlFor="admin-email">
              {t('loginEmail')}
            </label>
            <input
              id="admin-email"
              className="form-control"
              type="email"
              autoComplete="username"
              value={email}
              placeholder={t('emailPlaceholder')}
              required
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="mb-3">
            <label className="form-label" htmlFor="admin-password">
              {t('loginPassword')}
            </label>
            <input
              id="admin-password"
              className="form-control"
              type="password"
              autoComplete="current-password"
              value={password}
              placeholder={t('passwordPlaceholder')}
              required
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {error ? (
            <div className="alert alert-danger py-2" role="alert">
              {error}
            </div>
          ) : null}

          <button className="btn btn-primary w-100" type="submit" disabled={submitting}>
            {submitting ? (
              <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
            ) : null}
            {t('loginSubmit')}
          </button>
        </form>
      </section>
    </main>
  )
}
