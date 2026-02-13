import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '@/components/layout/Layout';
import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { DashboardPage } from '@/pages/dashboard/DashboardPage';
import { useAuthStore } from '@/stores/auth.store';

// Protected Route wrapper
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore();
  
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  
  return <>{children}</>;
}

// Public Route wrapper (redirects to dashboard if authenticated)
function PublicRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore();
  
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }
  
  return <>{children}</>;
}

// Placeholder pages for routes
function AccountsPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Accounts</h1>
      <p className="text-surface-600 dark:text-surface-400">Account management coming soon...</p>
    </div>
  );
}

function CompaniesPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Companies</h1>
      <p className="text-surface-600 dark:text-surface-400">Company management coming soon...</p>
    </div>
  );
}

function MembersPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Members</h1>
      <p className="text-surface-600 dark:text-surface-400">Member management coming soon...</p>
    </div>
  );
}

function RolesPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Roles</h1>
      <p className="text-surface-600 dark:text-surface-400">Role management coming soon...</p>
    </div>
  );
}

function PermissionsPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Permissions</h1>
      <p className="text-surface-600 dark:text-surface-400">Permission management coming soon...</p>
    </div>
  );
}

function CollaborationsPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Collaborations</h1>
      <p className="text-surface-600 dark:text-surface-400">Collaboration management coming soon...</p>
    </div>
  );
}

function SubscriptionsPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Subscriptions</h1>
      <p className="text-surface-600 dark:text-surface-400">Subscription management coming soon...</p>
    </div>
  );
}

function PlansPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Plans</h1>
      <p className="text-surface-600 dark:text-surface-400">Plan management coming soon...</p>
    </div>
  );
}

function ModulesPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Modules</h1>
      <p className="text-surface-600 dark:text-surface-400">Module management coming soon...</p>
    </div>
  );
}

function SettingsPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Settings</h1>
      <p className="text-surface-600 dark:text-surface-400">Settings coming soon...</p>
    </div>
  );
}

function ProfilePage() {
  return (
    <div className="space-y-6 animate-fade-in">
      <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">Profile</h1>
      <p className="text-surface-600 dark:text-surface-400">Profile settings coming soon...</p>
    </div>
  );
}

function NotFoundPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-50 dark:bg-surface-950">
      <div className="text-center">
        <h1 className="text-6xl font-bold text-surface-900 dark:text-surface-100">404</h1>
        <p className="text-xl text-surface-600 dark:text-surface-400 mt-4">Page not found</p>
        <a href="/dashboard" className="btn-primary mt-6 inline-flex">
          Go to Dashboard
        </a>
      </div>
    </div>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route
          path="/login"
          element={
            <PublicRoute>
              <LoginPage />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute>
              <RegisterPage />
            </PublicRoute>
          }
        />

        {/* Protected routes with layout */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="accounts/new" element={<AccountsPage />} />
          <Route path="accounts/:id" element={<AccountsPage />} />
          <Route path="companies" element={<CompaniesPage />} />
          <Route path="companies/new" element={<CompaniesPage />} />
          <Route path="companies/:id" element={<CompaniesPage />} />
          <Route path="members" element={<MembersPage />} />
          <Route path="members/new" element={<MembersPage />} />
          <Route path="members/:id" element={<MembersPage />} />
          <Route path="roles" element={<RolesPage />} />
          <Route path="roles/new" element={<RolesPage />} />
          <Route path="roles/:id" element={<RolesPage />} />
          <Route path="permissions" element={<PermissionsPage />} />
          <Route path="collaborations" element={<CollaborationsPage />} />
          <Route path="collaborations/new" element={<CollaborationsPage />} />
          <Route path="subscriptions" element={<SubscriptionsPage />} />
          <Route path="plans" element={<PlansPage />} />
          <Route path="modules" element={<ModulesPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
