import { api } from '@/lib/api-client';
import { 
  Account, 
  CreateAccountRequest, 
  UpdateAccountRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const accountService = {
  /**
   * Get all accounts for the current user
   */
  getMyAccounts: async (): Promise<Account[]> => {
    const response = await api.get<Account[]>('/accounts/my');
    return response.data;
  },

  /**
   * Get account by ID
   */
  getById: async (id: string): Promise<Account> => {
    const response = await api.get<Account>(`/accounts/${id}`);
    return response.data;
  },

  /**
   * Create a new account
   */
  create: async (data: CreateAccountRequest): Promise<Account> => {
    const response = await api.post<Account>('/accounts', data);
    return response.data;
  },

  /**
   * Update an account
   */
  update: async (id: string, data: UpdateAccountRequest): Promise<Account> => {
    const response = await api.put<Account>(`/accounts/${id}`, data);
    return response.data;
  },

  /**
   * Delete an account
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/accounts/${id}`);
  },

  /**
   * Get all accounts (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
  }): Promise<PageResponse<Account>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Account>>(`/accounts${queryString}`);
    return response.data;
  },

  /**
   * Check if account slug is available
   */
  checkSlugAvailability: async (slug: string): Promise<boolean> => {
    const response = await api.get<{ available: boolean }>(`/accounts/check-slug?slug=${slug}`);
    return response.data.available;
  },
};
