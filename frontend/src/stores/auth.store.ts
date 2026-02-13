import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { User, Account } from '@/types';
import { authService } from '@/services/auth.service';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  
  // Actions
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, firstName: string, lastName: string) => Promise<void>;
  logout: () => void;
  refreshAuth: () => Promise<void>;
  setUser: (user: User) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  initialize: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      login: async (email: string, password: string) => {
        set({ isLoading: true, error: null });
        try {
          const response = await authService.login({ email, password });
          set({
            user: response.user,
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            isAuthenticated: true,
            isLoading: false,
          });
        } catch (error) {
          const message = error instanceof Error ? error.message : 'Login failed';
          set({ error: message, isLoading: false, isAuthenticated: false });
          throw error;
        }
      },

      register: async (email: string, password: string, firstName: string, lastName: string) => {
        set({ isLoading: true, error: null });
        try {
          const response = await authService.register({ email, password, firstName, lastName });
          set({
            user: response.user,
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            isAuthenticated: true,
            isLoading: false,
          });
        } catch (error) {
          const message = error instanceof Error ? error.message : 'Registration failed';
          set({ error: message, isLoading: false, isAuthenticated: false });
          throw error;
        }
      },

      logout: () => {
        authService.logout();
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
          error: null,
        });
      },

      refreshAuth: async () => {
        const { refreshToken } = get();
        if (!refreshToken) {
          get().logout();
          return;
        }
        try {
          const response = await authService.refreshToken({ refreshToken });
          set({
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            user: response.user,
          });
        } catch {
          get().logout();
        }
      },

      setUser: (user: User) => set({ user }),

      setLoading: (isLoading: boolean) => set({ isLoading }),

      setError: (error: string | null) => set({ error }),

      initialize: () => {
        const tokens = authService.getStoredTokens();
        const user = authService.getStoredUser();
        if (tokens.accessToken && tokens.refreshToken && user) {
          set({
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            user,
            isAuthenticated: true,
          });
        }
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);

// App State Store
interface AppState {
  currentAccount: Account | null;
  accounts: Account[];
  sidebarOpen: boolean;
  sidebarCollapsed: boolean;
  theme: 'light' | 'dark';
  
  // Actions
  setCurrentAccount: (account: Account | null) => void;
  setAccounts: (accounts: Account[]) => void;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  toggleTheme: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      currentAccount: null,
      accounts: [],
      sidebarOpen: true,
      sidebarCollapsed: false,
      theme: 'light',

      setCurrentAccount: (account) => set({ currentAccount: account }),
      
      setAccounts: (accounts) => set({ accounts }),
      
      toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
      
      setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),
      
      toggleTheme: () => set((state) => ({ 
        theme: state.theme === 'light' ? 'dark' : 'light' 
      })),
    }),
    {
      name: 'app-storage',
      partialize: (state) => ({
        currentAccount: state.currentAccount,
        sidebarCollapsed: state.sidebarCollapsed,
        theme: state.theme,
      }),
    }
  )
);
