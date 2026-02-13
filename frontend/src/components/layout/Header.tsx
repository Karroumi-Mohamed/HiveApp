import { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { 
  Bell, 
  Search, 
  Moon, 
  Sun, 
  ChevronDown, 
  LogOut, 
  User, 
  Settings,
  Building2,
  Check
} from 'lucide-react';
import { useAuthStore, useAppStore } from '@/stores/auth.store';

export function Header() {
  const { user, logout } = useAuthStore();
  const { currentAccount, accounts, setCurrentAccount, theme, toggleTheme, sidebarCollapsed } = useAppStore();
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showAccountMenu, setShowAccountMenu] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  
  const userMenuRef = useRef<HTMLDivElement>(null);
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const notificationsRef = useRef<HTMLDivElement>(null);

  // Close menus when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setShowUserMenu(false);
      }
      if (accountMenuRef.current && !accountMenuRef.current.contains(event.target as Node)) {
        setShowAccountMenu(false);
      }
      if (notificationsRef.current && !notificationsRef.current.contains(event.target as Node)) {
        setShowNotifications(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Get user initials for avatar
  const getInitials = () => {
    if (!user) return '?';
    return `${user.firstName?.[0] || ''}${user.lastName?.[0] || ''}`.toUpperCase();
  };

  return (
    <header 
      className={`fixed top-0 right-0 h-16 bg-white/80 dark:bg-surface-900/80 backdrop-blur-xl border-b border-surface-200 dark:border-surface-800 z-30 transition-all duration-300 ${
        sidebarCollapsed ? 'left-20' : 'left-64'
      }`}
    >
      <div className="flex items-center justify-between h-full px-6">
        {/* Left side - Search and Account Switcher */}
        <div className="flex items-center gap-4">
          {/* Account Switcher */}
          <div className="relative" ref={accountMenuRef}>
            <button
              onClick={() => setShowAccountMenu(!showAccountMenu)}
              className="flex items-center gap-2 px-3 py-2 rounded-lg bg-surface-50 dark:bg-surface-800 hover:bg-surface-100 dark:hover:bg-surface-700 transition-colors"
            >
              <Building2 className="w-4 h-4 text-surface-500" />
              <span className="text-sm font-medium text-surface-700 dark:text-surface-300 max-w-[150px] truncate">
                {currentAccount?.name || 'Select Account'}
              </span>
              <ChevronDown className={`w-4 h-4 text-surface-400 transition-transform ${showAccountMenu ? 'rotate-180' : ''}`} />
            </button>

            {showAccountMenu && (
              <div className="dropdown mt-1">
                <div className="px-3 py-2 text-xs font-semibold text-surface-500 dark:text-surface-400 uppercase tracking-wider">
                  Your Accounts
                </div>
                {accounts.map((account) => (
                  <button
                    key={account.id}
                    onClick={() => {
                      setCurrentAccount(account);
                      setShowAccountMenu(false);
                    }}
                    className="dropdown-item w-full justify-between"
                  >
                    <span className="truncate">{account.name}</span>
                    {currentAccount?.id === account.id && (
                      <Check className="w-4 h-4 text-primary-500" />
                    )}
                  </button>
                ))}
                {accounts.length === 0 && (
                  <div className="px-4 py-3 text-sm text-surface-500 dark:text-surface-400">
                    No accounts found
                  </div>
                )}
                <div className="border-t border-surface-200 dark:border-surface-700 mt-1 pt-1">
                  <Link
                    to="/accounts/new"
                    className="dropdown-item text-primary-600 dark:text-primary-400"
                    onClick={() => setShowAccountMenu(false)}
                  >
                    + Create New Account
                  </Link>
                </div>
              </div>
            )}
          </div>

          {/* Search */}
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-surface-400" />
            <input
              type="text"
              placeholder="Search..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-64 pl-10 pr-4 py-2 text-sm bg-surface-50 dark:bg-surface-800 border border-surface-200 dark:border-surface-700 rounded-lg focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500 transition-all"
            />
          </div>
        </div>

        {/* Right side - Actions and User Menu */}
        <div className="flex items-center gap-3">
          {/* Theme Toggle */}
          <button
            onClick={toggleTheme}
            className="p-2 rounded-lg text-surface-500 hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors"
            title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
          >
            {theme === 'light' ? (
              <Moon className="w-5 h-5" />
            ) : (
              <Sun className="w-5 h-5" />
            )}
          </button>

          {/* Notifications */}
          <div className="relative" ref={notificationsRef}>
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              className="relative p-2 rounded-lg text-surface-500 hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors"
            >
              <Bell className="w-5 h-5" />
              <span className="absolute top-1 right-1 w-2 h-2 bg-error-500 rounded-full" />
            </button>

            {showNotifications && (
              <div className="dropdown right-0 mt-1 w-80">
                <div className="px-4 py-3 border-b border-surface-200 dark:border-surface-700">
                  <h3 className="font-semibold text-surface-900 dark:text-surface-100">Notifications</h3>
                </div>
                <div className="max-h-64 overflow-y-auto">
                  <div className="px-4 py-3 text-sm text-surface-500 dark:text-surface-400 text-center">
                    No new notifications
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* User Menu */}
          <div className="relative" ref={userMenuRef}>
            <button
              onClick={() => setShowUserMenu(!showUserMenu)}
              className="flex items-center gap-3 px-2 py-1.5 rounded-lg hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors"
            >
              <div className="avatar-md">
                {getInitials()}
              </div>
              <div className="hidden md:block text-left">
                <p className="text-sm font-medium text-surface-700 dark:text-surface-300">
                  {user?.firstName} {user?.lastName}
                </p>
                <p className="text-xs text-surface-500 dark:text-surface-400">
                  {user?.email}
                </p>
              </div>
              <ChevronDown className={`w-4 h-4 text-surface-400 transition-transform ${showUserMenu ? 'rotate-180' : ''}`} />
            </button>

            {showUserMenu && (
              <div className="dropdown right-0 mt-1">
                <div className="px-4 py-3 border-b border-surface-200 dark:border-surface-700">
                  <p className="text-sm font-medium text-surface-900 dark:text-surface-100">
                    {user?.firstName} {user?.lastName}
                  </p>
                  <p className="text-xs text-surface-500 dark:text-surface-400">
                    {user?.email}
                  </p>
                </div>
                <Link
                  to="/profile"
                  className="dropdown-item"
                  onClick={() => setShowUserMenu(false)}
                >
                  <User className="w-4 h-4" />
                  Profile
                </Link>
                <Link
                  to="/settings"
                  className="dropdown-item"
                  onClick={() => setShowUserMenu(false)}
                >
                  <Settings className="w-4 h-4" />
                  Settings
                </Link>
                <div className="border-t border-surface-200 dark:border-surface-700 mt-1 pt-1">
                  <button
                    onClick={() => {
                      logout();
                      setShowUserMenu(false);
                    }}
                    className="dropdown-item w-full text-error-600 dark:text-error-400"
                  >
                    <LogOut className="w-4 h-4" />
                    Sign Out
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
