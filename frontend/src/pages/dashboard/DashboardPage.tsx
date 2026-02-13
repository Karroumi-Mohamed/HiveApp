import { 
  Building2, 
  Users, 
  Shield, 
  Handshake, 
  TrendingUp,
  ArrowUpRight,
  ArrowDownRight,
  MoreHorizontal,
  Plus
} from 'lucide-react';
import { useAppStore } from '@/stores/auth.store';

const stats = [
  {
    name: 'Total Accounts',
    value: '12',
    change: '+2',
    changeType: 'positive',
    icon: Building2,
    color: 'from-primary-500 to-primary-600',
  },
  {
    name: 'Active Members',
    value: '48',
    change: '+8',
    changeType: 'positive',
    icon: Users,
    color: 'from-accent-500 to-accent-600',
  },
  {
    name: 'Roles Defined',
    value: '24',
    change: '+3',
    changeType: 'positive',
    icon: Shield,
    color: 'from-success-500 to-success-600',
  },
  {
    name: 'Collaborations',
    value: '6',
    change: '-1',
    changeType: 'negative',
    icon: Handshake,
    color: 'from-warning-500 to-warning-600',
  },
];

const recentActivity = [
  { id: 1, action: 'New member added', target: 'John Doe', account: 'Acme Corp', time: '5 minutes ago' },
  { id: 2, action: 'Role updated', target: 'Admin Role', account: 'Tech Inc', time: '1 hour ago' },
  { id: 3, action: 'Account created', target: 'New Company', account: 'Startup XYZ', time: '2 hours ago' },
  { id: 4, action: 'Permission granted', target: 'VIEW_REPORTS', account: 'Acme Corp', time: '3 hours ago' },
  { id: 5, action: 'Collaboration started', target: 'Partner Account', account: 'Tech Inc', time: '5 hours ago' },
];

export function DashboardPage() {
  const { currentAccount } = useAppStore();

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-surface-900 dark:text-surface-100">
            Dashboard
          </h1>
          <p className="text-surface-600 dark:text-surface-400 mt-1">
            Welcome back! Here's what's happening with your accounts.
          </p>
        </div>
        <button className="btn-primary">
          <Plus className="w-4 h-4" />
          New Account
        </button>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <div key={stat.name} className="card p-6 hover:shadow-lg transition-shadow">
              <div className="flex items-center justify-between">
                <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${stat.color} flex items-center justify-center shadow-lg`}>
                  <Icon className="w-6 h-6 text-white" />
                </div>
                <span className={`flex items-center gap-1 text-sm font-medium ${
                  stat.changeType === 'positive' ? 'text-success-600' : 'text-error-600'
                }`}>
                  {stat.changeType === 'positive' ? (
                    <ArrowUpRight className="w-4 h-4" />
                  ) : (
                    <ArrowDownRight className="w-4 h-4" />
                  )}
                  {stat.change}
                </span>
              </div>
              <div className="mt-4">
                <p className="text-3xl font-bold text-surface-900 dark:text-surface-100">
                  {stat.value}
                </p>
                <p className="text-sm text-surface-500 dark:text-surface-400 mt-1">
                  {stat.name}
                </p>
              </div>
            </div>
          );
        })}
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Activity */}
        <div className="lg:col-span-2 card">
          <div className="card-header flex items-center justify-between">
            <h2 className="text-lg font-semibold text-surface-900 dark:text-surface-100">
              Recent Activity
            </h2>
            <button className="btn-ghost text-sm">
              View all
            </button>
          </div>
          <div className="divide-y divide-surface-100 dark:divide-surface-800">
            {recentActivity.map((activity) => (
              <div key={activity.id} className="px-6 py-4 flex items-center justify-between hover:bg-surface-50 dark:hover:bg-surface-800/50 transition-colors">
                <div className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-full bg-surface-100 dark:bg-surface-800 flex items-center justify-center">
                    <TrendingUp className="w-5 h-5 text-surface-500" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-surface-900 dark:text-surface-100">
                      {activity.action}
                    </p>
                    <p className="text-sm text-surface-500 dark:text-surface-400">
                      {activity.target} • {activity.account}
                    </p>
                  </div>
                </div>
                <span className="text-xs text-surface-400 dark:text-surface-500">
                  {activity.time}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="card">
          <div className="card-header">
            <h2 className="text-lg font-semibold text-surface-900 dark:text-surface-100">
              Quick Actions
            </h2>
          </div>
          <div className="p-6 space-y-3">
            <button className="w-full btn-secondary justify-start">
              <Users className="w-4 h-4" />
              Invite Team Member
            </button>
            <button className="w-full btn-secondary justify-start">
              <Shield className="w-4 h-4" />
              Create New Role
            </button>
            <button className="w-full btn-secondary justify-start">
              <Handshake className="w-4 h-4" />
              Start Collaboration
            </button>
            <button className="w-full btn-secondary justify-start">
              <Building2 className="w-4 h-4" />
              Add Company
            </button>
          </div>
        </div>
      </div>

      {/* Current Account Info */}
      {currentAccount && (
        <div className="card p-6 bg-gradient-to-r from-primary-50 to-accent-50 dark:from-primary-900/20 dark:to-accent-900/20 border-primary-100 dark:border-primary-800">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-primary-600 dark:text-primary-400">
                Current Account
              </p>
              <h3 className="text-xl font-bold text-surface-900 dark:text-surface-100 mt-1">
                {currentAccount.name}
              </h3>
              <p className="text-sm text-surface-500 dark:text-surface-400 mt-1">
                Slug: {currentAccount.slug}
              </p>
            </div>
            <button className="btn-ghost">
              <MoreHorizontal className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
