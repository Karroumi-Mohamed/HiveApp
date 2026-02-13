import { api } from '@/lib/api-client';
import { 
  Module, 
  Feature,
  CreateModuleRequest,
  CreateFeatureRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const moduleService = {
  /**
   * Get all active modules
   */
  getActive: async (): Promise<Module[]> => {
    const response = await api.get<Module[]>('/modules/active');
    return response.data;
  },

  /**
   * Get all modules (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    active?: boolean;
  }): Promise<PageResponse<Module>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Module>>(`/modules${queryString}`);
    return response.data;
  },

  /**
   * Get module by ID
   */
  getById: async (id: string): Promise<Module> => {
    const response = await api.get<Module>(`/modules/${id}`);
    return response.data;
  },

  /**
   * Create a new module (admin only)
   */
  create: async (data: CreateModuleRequest): Promise<Module> => {
    const response = await api.post<Module>('/modules', data);
    return response.data;
  },

  /**
   * Update a module (admin only)
   */
  update: async (id: string, data: Partial<CreateModuleRequest>): Promise<Module> => {
    const response = await api.put<Module>(`/modules/${id}`, data);
    return response.data;
  },

  /**
   * Delete a module (admin only)
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/modules/${id}`);
  },

  /**
   * Activate a module
   */
  activate: async (id: string): Promise<Module> => {
    const response = await api.post<Module>(`/modules/${id}/activate`);
    return response.data;
  },

  /**
   * Deactivate a module
   */
  deactivate: async (id: string): Promise<Module> => {
    const response = await api.post<Module>(`/modules/${id}/deactivate`);
    return response.data;
  },

  /**
   * Get features for a module
   */
  getFeatures: async (moduleId: string): Promise<Feature[]> => {
    const response = await api.get<Feature[]>(`/modules/${moduleId}/features`);
    return response.data;
  },

  /**
   * Create a feature for a module
   */
  createFeature: async (data: CreateFeatureRequest): Promise<Feature> => {
    const response = await api.post<Feature>('/modules/features', data);
    return response.data;
  },

  /**
   * Delete a feature
   */
  deleteFeature: async (featureId: string): Promise<void> => {
    await api.delete(`/modules/features/${featureId}`);
  },
};
