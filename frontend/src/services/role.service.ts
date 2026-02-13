import { api } from '@/lib/api-client';
import { 
  Role, 
  CreateRoleRequest, 
  UpdateRoleRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const roleService = {
  /**
   * Get all roles for an account
   */
  getByAccount: async (accountId: string): Promise<Role[]> => {
    const response = await api.get<Role[]>(`/accounts/${accountId}/roles`);
    return response.data;
  },

  /**
   * Get role by ID
   */
  getById: async (id: string): Promise<Role> => {
    const response = await api.get<Role>(`/roles/${id}`);
    return response.data;
  },

  /**
   * Create a new role
   */
  create: async (data: CreateRoleRequest): Promise<Role> => {
    const response = await api.post<Role>('/roles', data);
    return response.data;
  },

  /**
   * Update a role
   */
  update: async (id: string, data: UpdateRoleRequest): Promise<Role> => {
    const response = await api.put<Role>(`/roles/${id}`, data);
    return response.data;
  },

  /**
   * Delete a role
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/roles/${id}`);
  },

  /**
   * Add permission to role
   */
  addPermission: async (roleId: string, permissionId: string): Promise<Role> => {
    const response = await api.post<Role>(`/roles/${roleId}/permissions/${permissionId}`);
    return response.data;
  },

  /**
   * Remove permission from role
   */
  removePermission: async (roleId: string, permissionId: string): Promise<Role> => {
    const response = await api.delete<Role>(`/roles/${roleId}/permissions/${permissionId}`);
    return response.data;
  },

  /**
   * Get all roles (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    accountId?: string;
  }): Promise<PageResponse<Role>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Role>>(`/roles${queryString}`);
    return response.data;
  },
};
