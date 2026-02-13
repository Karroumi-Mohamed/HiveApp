import { api } from '@/lib/api-client';
import { 
  Permission, 
  CreatePermissionRequest,
  PermissionContext,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const permissionService = {
  /**
   * Get all permissions
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    context?: PermissionContext;
    resource?: string;
  }): Promise<PageResponse<Permission>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Permission>>(`/permissions${queryString}`);
    return response.data;
  },

  /**
   * Get all permissions (flat list)
   */
  getAllFlat: async (): Promise<Permission[]> => {
    const response = await api.get<Permission[]>('/permissions/all');
    return response.data;
  },

  /**
   * Get permission by ID
   */
  getById: async (id: string): Promise<Permission> => {
    const response = await api.get<Permission>(`/permissions/${id}`);
    return response.data;
  },

  /**
   * Create a new permission (admin only)
   */
  create: async (data: CreatePermissionRequest): Promise<Permission> => {
    const response = await api.post<Permission>('/permissions', data);
    return response.data;
  },

  /**
   * Delete a permission (admin only)
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/permissions/${id}`);
  },

  /**
   * Get permissions by context
   */
  getByContext: async (context: PermissionContext): Promise<Permission[]> => {
    const response = await api.get<Permission[]>(`/permissions/context/${context}`);
    return response.data;
  },

  /**
   * Get permissions by resource
   */
  getByResource: async (resource: string): Promise<Permission[]> => {
    const response = await api.get<Permission[]>(`/permissions/resource/${resource}`);
    return response.data;
  },

  /**
   * Get available resources
   */
  getResources: async (): Promise<string[]> => {
    const response = await api.get<string[]>('/permissions/resources');
    return response.data;
  },

  /**
   * Get available actions
   */
  getActions: async (): Promise<string[]> => {
    const response = await api.get<string[]>('/permissions/actions');
    return response.data;
  },

  /**
   * Resolve effective permissions for a member
   */
  resolveForMember: async (memberId: string): Promise<{
    rolePermissions: string[];
    planCeiling: string[];
    modulePermissions: string[];
    effectivePermissions: string[];
  }> => {
    const response = await api.get<{
      rolePermissions: string[];
      planCeiling: string[];
      modulePermissions: string[];
      effectivePermissions: string[];
    }>(`/permissions/resolve/member/${memberId}`);
    return response.data;
  },
};
