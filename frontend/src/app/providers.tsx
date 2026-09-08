import { MutationCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { ApiError } from "@/api/http";
import { AdminSessionProvider, ClientSessionProvider } from "@/auth/session-provider";
import { configureSessionCacheReset } from "@/auth/session-store";
import { LoadingDebugPanel } from "@/components/patterns/loading-debug";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { getLanguageDirection } from "./i18n";
import { LocaleProvider } from "./locale-provider";
import { ThemeProvider, useTheme } from "./theme-provider";

function GlobalFeedback() {
  const { theme } = useTheme();
  const { i18n } = useTranslation();
  const direction = getLanguageDirection(i18n.resolvedLanguage);

  return (
    <Toaster
      theme={theme}
      dir={direction}
      position={direction === "rtl" ? "bottom-left" : "bottom-right"}
      closeButton
      richColors
    />
  );
}

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => {
    let client: QueryClient;
    client = new QueryClient({
      mutationCache: new MutationCache({
        onError: (error) => {
          toast.error(error instanceof ApiError ? error.message : "L’opération n’a pas pu être terminée.");
          if (error instanceof ApiError && error.status === 403) {
            void client.invalidateQueries({ queryKey: ["admin", "me"] });
            void client.invalidateQueries({ queryKey: ["client", "permissions"] });
          }
        },
      }),
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          retry: 1,
          refetchOnWindowFocus: false,
        },
      },
    });
    // Query keys are namespaced by audience, so ending one session drops exactly that audience's
    // data and leaves the other portal's cache intact.
    configureSessionCacheReset((audience) => {
      client.removeQueries({ queryKey: [audience] });
    });
    return client;
  });

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <LocaleProvider>
          <AdminSessionProvider>
            <ClientSessionProvider>
              <TooltipProvider delayDuration={250}>
                {children}
                <GlobalFeedback />
                <LoadingDebugPanel />
              </TooltipProvider>
            </ClientSessionProvider>
          </AdminSessionProvider>
        </LocaleProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
