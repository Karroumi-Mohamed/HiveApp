import { api } from '@/lib/api-client';
import { 
  Company, 
  CreateCompanyRequest, 
  UpdateCompanyRequest,
  PageResponse 
} from '@/types';
import { buildQueryString } from '@/lib/api-client';

export const companyService = {
  /**
   * Get all companies for an account
   */
  getByAccount: async (accountId: string): Promise<Company[]> => {
    const response = await api.get<Company[]>(`/accounts/${accountId}/companies`);
    return response.data;
  },

  /**
   * Get company by ID
   */
  getById: async (id: string): Promise<Company> => {
    const response = await api.get<Company>(`/companies/${id}`);
    return response.data;
  },

  /**
   * Create a new company
   */
  create: async (data: CreateCompanyRequest): Promise<Company> => {
    const response = await api.post<Company>('/companies', data);
    return response.data;
  },

  /**
   * Update a company
   */
  update: async (id: string, data: UpdateCompanyRequest): Promise<Company> => {
    const response = await api.put<Company>(`/companies/${id}`, data);
    return response.data;
  },

  /**
   * Delete a company
   */
  delete: async (id: string): Promise<void> => {
    await api.delete(`/companies/${id}`);
  },

  /**
   * Activate a module for a company
   */
  activateModule: async (companyId: string, moduleId: string): Promise<Company> => {
    const response = await api.post<Company>(`/companies/${companyId}/modules/${moduleId}/activate`);
    return response.data;
  },

  /**
   * Deactivate a module for a company
   */
  deactivateModule: async (companyId: string, moduleId: string): Promise<Company> => {
    const response = await api.post<Company>(`/companies/${companyId}/modules/${moduleId}/deactivate`);
    return response.data;
  },

  /**
   * Get all companies (admin only)
   */
  getAll: async (params?: { 
    page?: number; 
    size?: number; 
    sort?: string;
    accountId?: string;
  }): Promise<PageResponse<Company>> => {
    const queryString = buildQueryString(params || {});
    const response = await api.get<PageResponse<Company>>(`/companies${queryString}`);
    return response.data;
  },
};
