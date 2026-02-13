import { api } from '@/lib/api-client';
import { 
  Collaboration, 
  CreateCollaborationRequest,
  UpdateCollaborationPermissionsRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const collaborationService = {
  /**
   * Get collaborations for an account (both incoming and outgoing)
   */
  getByAccount: async (accountId: string): Promise<Collaboration[]> => {
    const response = await api.get<Collaboration[]>(`/accounts/${accountId}/collaborations`);
    return response.data;
  },

  /**
   * Get incoming collaborations (where this account is the target)
   */
  getIncoming: async (accountId: string): Promise<Collaboration[]> => {
    const response = await api.get<Collaboration[]>(`/accounts/${accountId}/collaborations/incoming`);
    return response.data;
  },

  /**
   * Get outgoing collaborations (where this account is the source)
   */
  getOutgoing: async (accountId: string): Promise<Collaboration[]> => {
    const response = await api.get<Collaboration[]>(`/accounts/${accountId}/collaborations/outgoing`);
    return response.data;
  },

  /**
   * Get collaboration by ID
   */
  getById: async (id: string): Promise<Collaboration> => {
    const response = await api.get<Collaboration>(`/collaborations/${id}`);
    return response.data;
  },

  /**
   * Create a new collaboration
   */
  create: async (data: CreateCollaborationRequest): Promise<Collaboration> => {
    const response = await api.post<Collaboration>('/collaborations', data);
    return response.data;
  },

  /**
   * Update collaboration permissions
   */
  updatePermissions: async (id: string, data: UpdateCollaborationPermissionsRequest): Promise<Collaboration> => {
    const response = await api.put<Collaboration>(`/collaborations/${id}/permissions`, data);
    return response.data;
  },

  /**
   * Deactivate a collaboration
   */
  deactivate: async (id: string): Promise<Collaboration> => {
    const response = await api.post<Collaboration>(`/collaborations/${id}/deactivate`);
    return response.data;
  },

  /**
   * Activate a collaboration
   */
  activate: async (id: string): Promise<Collaboration> => {
    const response = await api.post<Collaboration>(`/collaborations/${id}/activate`);
    return response.data;
  },

  /**
   * Delete a collaboration
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/collaborations/${id}`);
  },

  /**
   * Get all collaborations (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    sourceAccountId?: string;
    targetAccountId?: string;
  }): Promise<PageResponse<Collaboration>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Collaboration>>(`/collaborations${queryString}`);
    return response.data;
  },
};
