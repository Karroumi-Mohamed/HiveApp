import { api } from '@/lib/api-client';
import { 
  Plan, 
  CreatePlanRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const planService = {
  /**
   * Get all active plans
   */
  getActive: async (): Promise<Plan[]> => {
    const response = await api.get<Plan[]>('/plans/active');
    return response.data;
  },

  /**
   * Get all plans (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    active?: boolean;
  }): Promise<PageResponse<Plan>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Plan>>(`/plans${queryString}`);
    return response.data;
  },

  /**
   * Get plan by ID
   */
  getById: async (id: string): Promise<Plan> => {
    const response = await api.get<Plan>(`/plans/${id}`);
    return response.data;
  },

  /**
   * Create a new plan (admin only)
   */
  create: async (data: CreatePlanRequest): Promise<Plan> => {
    const response = await api.post<Plan>('/plans', data);
    return response.data;
  },

  /**
   * Update a plan (admin only)
   */
  update: async (id: string, data: Partial<CreatePlanRequest>): Promise<Plan> => {
    const response = await api.put<Plan>(`/plans/${id}`, data);
    return response.data;
  },

  /**
   * Delete a plan (admin only)
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/plans/${id}`);
  },

  /**
   * Activate a plan
   */
  activate: async (id: string): Promise<Plan> => {
    const response = await api.post<Plan>(`/plans/${id}/activate`);
    return response.data;
  },

  /**
   * Deactivate a plan
   */
  deactivate: async (id: string): Promise<Plan> => {
    const response = await api.post<Plan>(`/plans/${id}/deactivate`);
    return response.data;
  },

  /**
   * Add feature to plan
   */
  addFeature: async (planId: string, featureId: string, limit?: number): Promise<Plan> => {
    const response = await api.post<Plan>(`/plans/${planId}/features/${featureId}`, { limit });
    return response.data;
  },

  /**
   * Remove feature from plan
   */
  removeFeature: async (planId: string, featureId: string): Promise<Plan> => {
    const response = await api.delete<Plan>(`/plans/${planId}/features/${featureId}`);
    return response.data;
  },
};
