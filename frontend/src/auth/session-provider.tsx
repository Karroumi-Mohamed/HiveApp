import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";
import type { Account, AdminMe, AuthResponse, Company, MemberPermissions } from "@/api/contracts";
import { apiRequest, jsonBody } from "@/api/http";
import { readSession, type StoredSession, subscribeSessions, writeSession } from "@/auth/session-store";

type Credentials = { identifier?: string; password: string; accountCode?: string; employeeNumber?: string };

function stored(response: AuthResponse): StoredSession {
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    expiresAt: Date.now() + response.expiresIn * 1000,
    passwordChangeRequired: response.passwordChangeRequired,
  };
}

function useSessionRefresh(audience: "admin" | "client", session: StoredSession | null) {
  useEffect(() => {
    if (!session?.refreshToken || session.passwordChangeRequired) return;
    const refresh = async () => {
      try {
        const response = await apiRequest<AuthResponse>(
          audience === "admin" ? "/api/admin/auth/refresh" : "/api/v1/auth/refresh",
          { method: "POST", body: jsonBody({ refreshToken: session.refreshToken }) },
        );
        writeSession(audience, stored(response));
      } catch {
        writeSession(audience, null);
      }
    };
    const delay = Math.max(1_000, session.expiresAt - Date.now() - 60_000);
    const timer = window.setTimeout(() => void refresh(), delay);
    return () => window.clearTimeout(timer);
  }, [audience, session]);
}

type AdminSessionValue = {
  session: StoredSession | null;
  me: AdminMe | null;
  loading: boolean;
  error: boolean;
  retry: () => void;
  login: (credentials: Credentials) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  can: (permission: string) => boolean;
};

const AdminSessionContext = createContext<AdminSessionValue | null>(null);

export function AdminSessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useSyncExternalStore(
    subscribeSessions,
    () => readSession("admin"),
    () => null,
  );
  useSessionRefresh("admin", session);
  const meQuery = useQuery({
    queryKey: ["admin", "me", session?.accessToken],
    queryFn: () => apiRequest<AdminMe>("/api/admin/me", { audience: "admin" }),
    enabled: Boolean(session),
    retry: false,
  });

  const value = useMemo<AdminSessionValue>(
    () => ({
      session,
      me: meQuery.data ?? null,
      loading: Boolean(session) && meQuery.isLoading,
      error: meQuery.isError,
      retry: () => void meQuery.refetch(),
      login: async (credentials) => {
        const response = await apiRequest<AuthResponse>("/api/admin/auth/login", {
          method: "POST",
          body: jsonBody(credentials),
        });
        writeSession("admin", stored(response));
        return response;
      },
      logout: async () => {
        if (session?.refreshToken) {
          await apiRequest<void>("/api/admin/auth/logout", {
            audience: "admin",
            method: "POST",
            body: jsonBody({ refreshToken: session.refreshToken }),
          }).catch(() => undefined);
        }
        writeSession("admin", null);
        queryClient.removeQueries({ queryKey: ["admin"] });
      },
      can: (permission) => Boolean(meQuery.data?.isSuperAdmin || meQuery.data?.permissions.includes(permission)),
    }),
    [meQuery.data, meQuery.isError, meQuery.isLoading, meQuery.refetch, queryClient, session],
  );
  return <AdminSessionContext.Provider value={value}>{children}</AdminSessionContext.Provider>;
}

export function useAdminSession() {
  const value = useContext(AdminSessionContext);
  if (!value) throw new Error("useAdminSession must be used inside AdminSessionProvider");
  return value;
}

type ClientSessionValue = {
  session: StoredSession | null;
  account: Account | null;
  companies: Company[];
  selectedCompanyId: string | null;
  permissions: MemberPermissions | null;
  isB2B: boolean;
  loading: boolean;
  error: boolean;
  retry: () => void;
  login: (credentials: Credentials) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  selectCompany: (companyId: string | null) => void;
  setB2B: (value: boolean) => void;
  can: (permission: string) => boolean;
};

const ClientSessionContext = createContext<ClientSessionValue | null>(null);
const companyKey = "hiveapp-selected-company";
const b2bKey = "hiveapp-b2b-mode";

export function ClientSessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useSyncExternalStore(
    subscribeSessions,
    () => readSession("client"),
    () => null,
  );
  useSessionRefresh("client", session);
  const selectedCompanyId = useSyncExternalStore(
    subscribeSessions,
    () => window.localStorage.getItem(companyKey),
    () => null,
  );
  const isB2B = useSyncExternalStore(
    subscribeSessions,
    () => window.localStorage.getItem(b2bKey) === "true",
    () => false,
  );
  const context = { companyId: selectedCompanyId, isB2B };
  const accountQuery = useQuery({
    queryKey: ["client", "account", session?.accessToken],
    queryFn: () => apiRequest<Account>("/api/v1/accounts/me", { audience: "client" }),
    enabled: Boolean(session && !session.passwordChangeRequired),
    retry: false,
  });
  const companiesQuery = useQuery({
    queryKey: ["client", "companies", session?.accessToken],
    queryFn: () => apiRequest<Company[]>("/api/v1/companies", { audience: "client" }),
    enabled: Boolean(session && !session.passwordChangeRequired),
    retry: false,
  });
  const permissionsQuery = useQuery({
    queryKey: ["client", "permissions", selectedCompanyId, isB2B, session?.accessToken],
    queryFn: () => apiRequest<MemberPermissions>("/api/v1/me/permissions", { audience: "client", context }),
    enabled: Boolean(session && !session.passwordChangeRequired),
    retry: false,
  });

  const notifyContext = useCallback(() => {
    writeSession("client", readSession("client"));
  }, []);
  const value = useMemo<ClientSessionValue>(
    () => ({
      session,
      account: accountQuery.data ?? null,
      companies: companiesQuery.data ?? [],
      selectedCompanyId,
      permissions: permissionsQuery.data ?? null,
      isB2B,
      loading: Boolean(session) && (accountQuery.isLoading || permissionsQuery.isLoading),
      error: accountQuery.isError || companiesQuery.isError || permissionsQuery.isError,
      retry: () => {
        void Promise.all([accountQuery.refetch(), companiesQuery.refetch(), permissionsQuery.refetch()]);
      },
      login: async (credentials) => {
        const response = await apiRequest<AuthResponse>("/api/v1/auth/login", {
          method: "POST",
          body: jsonBody(credentials),
        });
        writeSession("client", stored(response));
        return response;
      },
      logout: async () => {
        if (session?.refreshToken) {
          await apiRequest<void>("/api/v1/auth/logout", {
            audience: "client",
            method: "POST",
            body: jsonBody({ refreshToken: session.refreshToken }),
          }).catch(() => undefined);
        }
        writeSession("client", null);
        queryClient.removeQueries({ queryKey: ["client"] });
      },
      selectCompany: (companyId) => {
        if (companyId) window.localStorage.setItem(companyKey, companyId);
        else window.localStorage.removeItem(companyKey);
        notifyContext();
      },
      setB2B: (value) => {
        window.localStorage.setItem(b2bKey, String(value));
        notifyContext();
      },
      can: (permission) =>
        Boolean(permissionsQuery.data?.isOwner || permissionsQuery.data?.permissions.includes(permission)),
    }),
    [
      accountQuery.data,
      accountQuery.isLoading,
      accountQuery.isError,
      accountQuery.refetch,
      companiesQuery.data,
      companiesQuery.isError,
      companiesQuery.refetch,
      isB2B,
      permissionsQuery.data,
      permissionsQuery.isError,
      permissionsQuery.isLoading,
      permissionsQuery.refetch,
      queryClient,
      selectedCompanyId,
      session,
      notifyContext,
    ],
  );
  return <ClientSessionContext.Provider value={value}>{children}</ClientSessionContext.Provider>;
}

export function useClientSession() {
  const value = useContext(ClientSessionContext);
  if (!value) throw new Error("useClientSession must be used inside ClientSessionProvider");
  return value;
}
