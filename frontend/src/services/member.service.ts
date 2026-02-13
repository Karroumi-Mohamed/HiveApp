import { api } from '@/lib/api-client';
import { 
  Member, 
  CreateMemberRequest, 
  AssignRoleRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const memberService = {
  /**
   * Get all members for an account
   */
  getByAccount: async (accountId: string): Promise<Member[]> => {
    const response = await api.get<Member[]>(`/accounts/${accountId}/members`);
    return response.data;
  },

  /**
   * Get members by company
   */
  getByCompany: async (companyId: string): Promise<Member[]> => {
    const response = await api.get<Member[]>(`/companies/${companyId}/members`);
    return response.data;
  },

  /**
   * Get member by ID
   */
  getById: async (id: string): Promise<Member> => {
    const response = await api.get<Member>(`/members/${id}`);
    return response.data;
  },

  /**
   * Create a new member
   */
  create: async (data: CreateMemberRequest): Promise<Member> => {
    const response = await api.post<Member>('/members', data);
    return response.data;
  },

  /**
   * Assign roles to a member
   */
  assignRoles: async (memberId: string, data: AssignRoleRequest): Promise<Member> => {
    const response = await api.put<Member>(`/members/${memberId}/roles`, data);
    return response.data;
  },

  /**
   * Remove a role from a member
   */
  removeRole: async (memberId: string, roleId: string): Promise<Member> => {
    const response = await api.delete<Member>(`/members/${memberId}/roles/${roleId}`);
    return response.data;
  },

  /**
   * Deactivate a member
   */
  deactivate: async (memberId: string): Promise<Member> => {
    const response = await api.post<Member>(`/members/${memberId}/deactivate`);
    return response.data;
  },

  /**
   * Activate a member
   */
  activate: async (memberId: string): Promise<Member> => {
    const response = await api.post<Member>(`/members/${memberId}/activate`);
    return response.data;
  },

  /**
   * Delete a member
   */
  delete: async (memberId: string): Promise<void> => {
    await api.delete(`/members/${memberId}`);
  },

  /**
   * Get all members (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    accountId?: string;
    companyId?: string;
  }): Promise<PageResponse<Member>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Member>>(`/members${queryString}`);
    return response.data;
  },
};
