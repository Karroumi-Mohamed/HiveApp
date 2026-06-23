import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AdminAccessRolesPage } from './features/admin/access/AdminAccessRolesPage'
import { AdminAccessUsersPage } from './features/admin/access/AdminAccessUsersPage'
import { AdminLoginPage } from './features/admin/auth/AdminLoginPage'
import { AdminRouteGate } from './features/admin/auth/AdminRouteGate'
import { AdminSessionProvider } from './features/admin/auth/AdminSessionProvider'
import { AdminFeaturesPage } from './features/admin/features/AdminFeaturesPage'
import { AdminLayout } from './features/admin/layout/AdminLayout'
import { AdminPlanFeatureMatrixPage } from './features/admin/plans/AdminPlanFeatureMatrixPage'
import { AdminPlansPage } from './features/admin/plans/AdminPlansPage'
import { I18nProvider } from './lib/I18nProvider'

function App() {
  return (
    <I18nProvider>
      <AdminSessionProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/admin/login" element={<AdminLoginPage />} />
            <Route element={<AdminRouteGate />}>
              <Route path="/admin" element={<AdminLayout />}>
                <Route index element={<Navigate to="/admin/features" replace />} />
                <Route path="features" element={<AdminFeaturesPage />} />
                <Route path="access" element={<Navigate to="/admin/access/members" replace />} />
                <Route path="access/members" element={<AdminAccessUsersPage />} />
                <Route path="access/roles" element={<AdminAccessRolesPage />} />
                <Route path="plans" element={<Navigate to="/admin/plans/templates" replace />} />
                <Route path="plans/templates" element={<AdminPlansPage />} />
                <Route path="plans/features" element={<AdminPlanFeatureMatrixPage />} />
                <Route path="registry" element={<Navigate to="/admin/features" replace />} />
              </Route>
            </Route>
            <Route path="/" element={<Navigate to="/admin/features" replace />} />
            <Route path="*" element={<Navigate to="/admin/features" replace />} />
          </Routes>
        </BrowserRouter>
      </AdminSessionProvider>
    </I18nProvider>
  )
}

export default App
