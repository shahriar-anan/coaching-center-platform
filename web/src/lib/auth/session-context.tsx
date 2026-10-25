"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { api, onSessionLost } from "@/lib/api/client";
import { setAccessToken } from "@/lib/auth/token-store";
import type { Me } from "@/lib/api/exam/types";
import { onSiteHost, onStaffHost, staffHome, studentHome } from "@/lib/config";

export type SessionState =
  | { status: "loading"; user?: undefined }
  | { status: "anonymous"; user?: undefined }
  | { status: "authenticated"; user: Me };

export type LoginOptions = {
  /** Where to go after a student logs in; only same-site student paths are used. */
  next?: string | null;
  /** Skip the redirect (the caller routes). */
  redirect?: boolean;
};

export type SessionContextValue = {
  session: SessionState;
  /** True after the API dropped a session that was signed in. */
  sessionExpired: boolean;
  login: (identifier: string, password: string, options?: LoginOptions) => Promise<Me>;
  logout: () => Promise<void>;
  refreshMe: () => Promise<void>;
  /** Signs in with tokens obtained elsewhere (Google return page). */
  acceptToken: (accessToken: string, options?: LoginOptions) => Promise<Me>;
};

export const SessionContext = createContext<SessionContextValue | null>(null);

/** Thrown by `login` when split hosts send an account to the other surface's login page. */
export class WrongAudienceError extends Error {
  readonly audience: "staff" | "student";

  constructor(audience: "staff" | "student") {
    super(audience === "staff" ? "STAFF_ON_STUDENT_SITE" : "STUDENT_ON_STAFF_SITE");
    this.name = "WrongAudienceError";
    this.audience = audience;
  }
}

const AUTH_PAGES = [
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
  "/activate",
  "/set-phone",
];

/** A `?next=` value the student may be sent to: same site, not staff, not an auth page. */
export function safeStudentNext(next: string | null | undefined): string | null {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return null;
  }
  const path = next.split(/[?#]/)[0];
  if (path === "/staff" || path.startsWith("/staff/")) {
    return null;
  }
  if (AUTH_PAGES.some((p) => path === p || path.startsWith(`${p}/`))) {
    return null;
  }
  return next;
}

export function destinationFor(user: Me, next?: string | null): string {
  if (user.role === "STUDENT") {
    return safeStudentNext(next) ?? studentHome;
  }
  return staffHome;
}

function go(router: ReturnType<typeof useRouter>, href: string) {
  if (/^https?:\/\//.test(href)) {
    window.location.assign(href);
  } else {
    router.replace(href);
  }
}

async function loadMe(): Promise<Me | null> {
  try {
    return (await api.me()) as Me;
  } catch {
    setAccessToken(null);
    return null;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [session, setSession] = useState<SessionState>({ status: "loading" });
  const [sessionExpired, setSessionExpired] = useState(false);
  const signedIn = useRef(false);

  useEffect(() => {
    signedIn.current = session.status === "authenticated";
  }, [session.status]);

  useEffect(() => {
    onSessionLost(() => {
      if (signedIn.current) {
        setSessionExpired(true);
      }
      setSession({ status: "anonymous" });
    });
    return () => onSessionLost(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tokens = await api.refresh();
        setAccessToken(tokens.accessToken);
        const user = await loadMe();
        if (!cancelled) {
          setSession(user ? { status: "authenticated", user } : { status: "anonymous" });
        }
      } catch {
        if (!cancelled) {
          setAccessToken(null);
          setSession({ status: "anonymous" });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const finishLogin = useCallback(
    async (options: LoginOptions = {}) => {
      const user = await loadMe();
      if (!user) {
        setSession({ status: "anonymous" });
        throw new Error("ME_UNAVAILABLE");
      }
      // With split hosts each login page accepts only its own audience.
      const audience =
        user.role !== "STUDENT" && onSiteHost()
          ? "staff"
          : user.role === "STUDENT" && onStaffHost()
            ? "student"
            : null;
      if (audience) {
        try {
          await api.logout();
        } catch {
          /* ignore */
        }
        setAccessToken(null);
        setSession({ status: "anonymous" });
        throw new WrongAudienceError(audience);
      }
      queryClient.clear();
      setSessionExpired(false);
      setSession({ status: "authenticated", user });
      if (options.redirect !== false) {
        const next =
          options.next !== undefined
            ? options.next
            : new URLSearchParams(window.location.search).get("next");
        go(router, destinationFor(user, next));
      }
      return user;
    },
    [queryClient, router],
  );

  const login = useCallback(
    async (identifier: string, password: string, options?: LoginOptions) => {
      const tokens = await api.login(identifier, password);
      setAccessToken(tokens.accessToken);
      return finishLogin(options);
    },
    [finishLogin],
  );

  const acceptToken = useCallback(
    async (accessToken: string, options?: LoginOptions) => {
      setAccessToken(accessToken);
      return finishLogin(options);
    },
    [finishLogin],
  );

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* ignore */
    }
    setAccessToken(null);
    queryClient.clear();
    setSessionExpired(false);
    setSession({ status: "anonymous" });
    router.replace("/");
  }, [queryClient, router]);

  const refreshMe = useCallback(async () => {
    const user = await loadMe();
    setSession(user ? { status: "authenticated", user } : { status: "anonymous" });
  }, []);

  const value = useMemo(
    () => ({ session, sessionExpired, login, logout, refreshMe, acceptToken }),
    [session, sessionExpired, login, logout, refreshMe, acceptToken],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext);
  if (!ctx) {
    throw new Error("useSession must be used within SessionProvider");
  }
  return ctx;
}

export function useUser(): Me | null {
  const { session } = useSession();
  return session.status === "authenticated" ? session.user : null;
}
