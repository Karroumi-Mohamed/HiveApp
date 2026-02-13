import { Link, useLocation } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Building2, 
  Users, 
  Shield, 
  Key, 
  Handshake, 
  CreditCard, 
  Package, 
  Settings,
  ChevronLeft,
  ChevronRight,
  Hexagon
} from 'lucide-react';
import { useAppStore } from '@/stores/auth.store';

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
  { name: 'Accounts', href: '/accounts', icon: Building2 },
  { name: 'Companies', href: '/companies', icon: Building2 },
  { name: 'Members', href: '/members', icon: Users },
  { name: 'Roles', href: '/roles', icon: Shield },
  { name: 'Permissions', href: '/permissions', icon: Key },
  { name: 'Collaborations', href: '/collaborations', icon: Handshake },
  { name: 'Subscriptions', href: '/subscriptions', icon: CreditCard },
  { name: 'Plans', href: '/plans', icon: CreditCard },
  { name: 'Modules', href: '/modules', icon: Package },
  { name: 'Settings', href: '/settings', icon: Settings },
];

export function Sidebar() {
  const location = useLocation();
  const { sidebarCollapsed, setSidebarCollapsed } = useAppStore();

  return (
    <aside
      className={`fixed left-0 top-0 h-full bg-white dark:bg-surface-900 border-r border-surface-200 dark:border-surface-800 z-40 transition-all duration-300 ${
        sidebarCollapsed ? 'w-20' : 'w-64'
      }`}
    >
      {/* Logo */}
      <div className="flex items-center justify-between h-16 px-4 border-b border-surface-200 dark:border-surface-800">
        <Link to="/dashboard" className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-accent-500 flex items-center justify-center shadow-lg">
            <Hexagon className="w-6 h-6 text-white" />
          </div>
          {!sidebarCollapsed && (
            <span className="text-xl font-bold text-gradient">HiveApp</span>
          )}
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto scrollbar-hide">
        {navigation.map((item) => {
          const isActive = location.pathname.startsWith(item.href);
          const Icon = item.icon;
          
          return (
            <Link
              key={item.name}
              to={item.href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 group ${
                isActive
                  ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400 shadow-sm'
                  : 'text-surface-600 dark:text-surface-400 hover:bg-surface-100 dark:hover:bg-surface-800'
              }`}
              title={sidebarCollapsed ? item.name : undefined}
            >
              <Icon className={`w-5 h-5 flex-shrink-0 ${isActive ? 'text-primary-500' : 'text-surface-400 group-hover:text-surface-600 dark:group-hover:text-surface-300'}`} />
              {!sidebarCollapsed && <span>{item.name}</span>}
            </Link>
          );
        })}
      </nav>

      {/* Collapse Toggle */}
      <button
        onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
        className="absolute -right-3 top-20 w-6 h-6 bg-white dark:bg-surface-800 border border-surface-200 dark:border-surface-700 rounded-full flex items-center justify-center shadow-md hover:bg-surface-50 dark:hover:bg-surface-700 transition-colors"
      >
        {sidebarCollapsed ? (
          <ChevronRight className="w-4 h-4 text-surface-500" />
        ) : (
          <ChevronLeft className="w-4 h-4 text-surface-500" />
        )}
      </button>

      {/* Footer */}
      {!sidebarCollapsed && (
        <div className="p-4 border-t border-surface-200 dark:border-surface-800">
          <div className="px-3 py-2 rounded-lg bg-gradient-to-r from-primary-50 to-accent-50 dark:from-primary-900/20 dark:to-accent-900/20">
            <p className="text-xs font-medium text-surface-600 dark:text-surface-400">
              HiveApp v1.0.0
            </p>
            <p className="text-xs text-surface-500 dark:text-surface-500 mt-0.5">
              Multi-tenant ERP Platform
            </p>
          </div>
        </div>
      )}
    </aside>
  );
}
