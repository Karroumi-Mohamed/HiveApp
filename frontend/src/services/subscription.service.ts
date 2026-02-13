import { api } from '@/lib/api-client';
import { 
  Subscription, 
  CreateSubscriptionRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const subscriptionService = {
  /**
   * Get subscription for an account
   */
  getByAccount: async (accountId: string): Promise<Subscription | null> => {
    const response = await api.get<Subscription>(`/accounts/${accountId}/subscription`);
    return response.data;
  },

  /**
   * Get subscription by ID
   */
  getById: async (id: string): Promise<Subscription> => {
    const response = await api.get<Subscription>(`/subscriptions/${id}`);
    return response.data;
  },

  /**
   * Create a new subscription
   */
  create: async (data: CreateSubscriptionRequest): Promise<Subscription> => {
    const response = await api.post<Subscription>('/subscriptions', data);
    return response.data;
  },

  /**
   * Cancel a subscription
   */
  cancel: async (id: string): Promise<Subscription> => {
    const response = await api.post<Subscription>(`/subscriptions/${id}/cancel`);
    return response.data;
  },

  /**
   * Change plan for a subscription
   */
  changePlan: async (id: string, planId: string): Promise<Subscription> => {
    const response = await api.post<Subscription>(`/subscriptions/${id}/change-plan`, { planId });
    return response.data;
  },

  /**
   * Get all subscriptions (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    status?: string;
  }): Promise<PageResponse<Subscription>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Subscription>>(`/subscriptions${queryString}`);
    return response.data;
  },
};
