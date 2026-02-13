import { api } from '@/lib/api-client';
import { 
  AuthResponse, 
  LoginRequest, 
  RegisterRequest, 
  User,
  RefreshTokenRequest 
} from '@/types';

export const authService = {
  /**
   * Login with email and password
   */
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/login', credentials);
    return response.data;
  },

  /**
   * Register a new user
   */
  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/register', data);
    return response.data;
  },

  /**
   * Refresh the access token
   */
  refreshToken: async (data: RefreshTokenRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/refresh', data);
    return response.data;
  },

  /**
   * Get current user profile
   */
  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>('/users/me');
    return response.data;
  },

  /**
   * Logout - clear local tokens
   */
  logout: (): void => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
  },

  /**
   * Check if user is authenticated
   */
  isAuthenticated: (): boolean => {
    return !!localStorage.getItem('accessToken');
  },

  /**
   * Get stored tokens
   */
  getStoredTokens: (): { accessToken: string | null; refreshToken: string | null } => {
    return {
      accessToken: localStorage.getItem('accessToken'),
      refreshToken: localStorage.getItem('refreshToken'),
    };
  },

  /**
   * Store tokens
   */
  storeTokens: (accessToken: string, refreshToken: string): void => {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
  },

  /**
   * Store user data
   */
  storeUser: (user: User): void => {
    localStorage.setItem('user', JSON.stringify(user));
  },

  /**
   * Get stored user data
   */
  getStoredUser: (): User | null => {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  },
};
