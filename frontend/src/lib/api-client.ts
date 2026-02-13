import axios, { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { ApiError } from '@/types';

// Create axios instance with default config
const createApiClient = (): AxiosInstance => {
  const client = axios.create({
    baseURL: '/api',
    timeout: 30000,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  // Request interceptor - add auth token
  client.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('accessToken');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  // Response interceptor - handle errors and token refresh
  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiError>) => {
      const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

      // Handle 401 Unauthorized - try to refresh token
      if (error.response?.status === 401 && !originalRequest._retry) {
        originalRequest._retry = true;

        try {
          const refreshToken = localStorage.getItem('refreshToken');
          if (refreshToken) {
            const response = await axios.post('/api/auth/refresh', {
              refreshToken,
            });

            const { accessToken, refreshToken: newRefreshToken } = response.data;
            localStorage.setItem('accessToken', accessToken);
            localStorage.setItem('refreshToken', newRefreshToken);

            // Retry original request with new token
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${accessToken}`;
            }
            return axios(originalRequest);
          }
        } catch (refreshError) {
          // Refresh failed - clear tokens and redirect to login
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
          localStorage.removeItem('user');
          window.location.href = '/login';
          return Promise.reject(refreshError);
        }
      }

      // Handle other errors
      const apiError: ApiError = {
        status: error.response?.status || 500,
        message: error.response?.data?.message || error.message || 'An unexpected error occurred',
        errors: error.response?.data?.errors,
        timestamp: error.response?.data?.timestamp || new Date().toISOString(),
        path: error.response?.data?.path || '',
      };

      return Promise.reject(apiError);
    }
  );

  return client;
};

export const apiClient = createApiClient();

// Helper functions for common HTTP methods
export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> => 
    apiClient.get<T>(url, config),

  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> => 
    apiClient.post<T>(url, data, config),

  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> => 
    apiClient.put<T>(url, data, config),

  patch: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> => 
    apiClient.patch<T>(url, data, config),

  delete: <T>(url: string, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> => 
    apiClient.delete<T>(url, config),
};

// Query params helper
export const buildQueryString = (params: Record<string, unknown>): string => {
  const searchParams = new URLSearchParams();
  
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      if (Array.isArray(value)) {
        value.forEach((item) => searchParams.append(key, String(item)));
      } else {
        searchParams.set(key, String(value));
      }
    }
  });

  const queryString = searchParams.toString();
  return queryString ? `?${queryString}` : '';
};
