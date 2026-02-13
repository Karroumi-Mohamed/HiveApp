import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { useAppStore } from '@/stores/auth.store';

export function Layout() {
  const { sidebarCollapsed } = useAppStore();

  return (
    <div className="min-h-screen bg-surface-50 dark:bg-surface-950">
      <Sidebar />
      <Header />
      <main 
        className={`pt-16 min-h-screen transition-all duration-300 ${
          sidebarCollapsed ? 'pl-20' : 'pl-64'
        }`}
      >
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
