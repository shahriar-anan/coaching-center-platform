"use client";

import { useState, type ReactNode } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { LocaleProvider } from "@/i18n/locale-context";
import { SessionProvider } from "@/lib/auth/session-context";
import { getQueryClient } from "@/lib/api/query-client";
import { ToastProvider } from "@/components/ui/toast";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(getQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <LocaleProvider>
        <ToastProvider>
          <SessionProvider>{children}</SessionProvider>
        </ToastProvider>
      </LocaleProvider>
    </QueryClientProvider>
  );
}
