import type { ReactElement, ReactNode } from "react";
import { render, type RenderOptions } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { LocaleProvider, type Locale } from "@/i18n/locale-context";
import { SessionContext, type SessionContextValue } from "@/lib/auth/session-context";
import { ToastProvider } from "@/components/ui/toast";
import type { Me } from "@/lib/api/exam/types";
import { resetRouter } from "./router";

export type RenderWithProvidersOptions = Omit<RenderOptions, "wrapper"> & {
  /** Signed-in user. Omit for a signed-out visitor. */
  user?: Me | null;
  locale?: Locale;
  /** Initial route, such as "/mcq?page=2". */
  route?: string;
};

function sessionValue(user: Me | null | undefined): SessionContextValue {
  const noop = async () => undefined;
  return {
    session: user ? { status: "authenticated", user } : { status: "anonymous" },
    sessionExpired: false,
    login: async () => {
      throw new Error("login is not available in tests");
    },
    logout: noop,
    refreshMe: noop,
    acceptToken: async () => {
      throw new Error("acceptToken is not available in tests");
    },
  };
}

/**
 * Renders with the session, locale, query client, toast and router providers
 * the app uses. The session is given directly, so no refresh call is made.
 */
export function renderWithProviders(
  ui: ReactElement,
  { user = null, locale = "bn", route = "/", ...options }: RenderWithProvidersOptions = {},
) {
  resetRouter(route);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const value = sessionValue(user);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <LocaleProvider initialLocale={locale}>
          <ToastProvider>
            <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
          </ToastProvider>
        </LocaleProvider>
      </QueryClientProvider>
    );
  }

  return { ...render(ui, { wrapper: Wrapper, ...options }), queryClient };
}
