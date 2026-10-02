"use client";

import { LocaleProvider } from "@/i18n/locale-context";
import { SessionProvider } from "@/lib/auth/session-context";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <LocaleProvider>
      <SessionProvider>{children}</SessionProvider>
    </LocaleProvider>
  );
}
