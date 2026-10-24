"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import { api, onSessionLost } from "@/lib/api/client";
import { setAccessToken } from "@/lib/auth/token-store";
import type { MeView } from "@/lib/api/types";

type SessionState =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "authenticated"; user: MeView };

type SessionContextValue = {
  session: SessionState;
  login: (identifier: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshMe: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

function isStudentAuthPath(pathname: string): boolean {
  return (
    pathname === "/login" ||
    pathname === "/register" ||
    pathname === "/forgot-password" ||
    pathname === "/reset-password" ||
    pathname === "/account" ||
    pathname.startsWith("/account/")
  );
}

function allowsStudent(pathname: string): boolean {
  return (
    pathname === "/purchase/result" ||
    pathname.startsWith("/purchase/result/") ||
    pathname === "/live-exams" ||
    pathname.startsWith("/live-exams/") ||
    pathname === "/sandbox" ||
    pathname.startsWith("/sandbox/") ||
    isStudentAuthPath(pathname)
  );
}

async function loadMe(keepStudent: boolean): Promise<MeView | null> {
  try {
    const user = await api.me();
    if (user.role === "STUDENT" && !keepStudent) {
      try {
        await api.logout();
      } catch {
        /* ignore */
      }
      setAccessToken(null);
      return null;
    }
    return user;
  } catch {
    setAccessToken(null);
    return null;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const keepStudent = allowsStudent(pathname);
  const [session, setSession] = useState<SessionState>({ status: "loading" });

  const goLogin = useCallback(() => {
    setSession({ status: "anonymous" });
    if (!allowsStudent(pathname)) {
      router.replace("/login");
    }
  }, [pathname, router]);

  useEffect(() => {
    onSessionLost(goLogin);
    return () => onSessionLost(null);
  }, [goLogin]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tokens = await api.refresh();
        setAccessToken(tokens.accessToken);
        const user = await loadMe(keepStudent);
        if (cancelled) {
          return;
        }
        if (user) {
          setSession({ status: "authenticated", user });
        } else {
          setSession({ status: "anonymous" });
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
  }, [keepStudent]);

  const login = useCallback(async (identifier: string, password: string) => {
    const tokens = await api.login(identifier, password);
    setAccessToken(tokens.accessToken);
    const user = await loadMe(keepStudent);
    if (!user) {
      setSession({ status: "anonymous" });
      throw new Error("STUDENT_REJECTED");
    }
    setSession({ status: "authenticated", user });
    const onSandbox = pathname === "/sandbox" || pathname.startsWith("/sandbox/");
    if (user.role === "STUDENT" && isStudentAuthPath(pathname)) {
      router.replace("/account");
    } else if (user.role !== "STUDENT" && !onSandbox) {
      router.replace("/students");
    }
  }, [keepStudent, pathname, router]);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* ignore */
    }
    setAccessToken(null);
    setSession({ status: "anonymous" });
    router.replace("/login");
  }, [router]);

  const refreshMe = useCallback(async () => {
    const user = await loadMe(keepStudent);
    if (user) {
      setSession({ status: "authenticated", user });
    } else {
      goLogin();
    }
  }, [goLogin, keepStudent]);

  const value = useMemo(
    () => ({ session, login, logout, refreshMe }),
    [session, login, logout, refreshMe],
  );

  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext);
  if (!ctx) {
    throw new Error("useSession must be used within SessionProvider");
  }
  return ctx;
}

export function useUser(): MeView | null {
  const { session } = useSession();
  return session.status === "authenticated" ? session.user : null;
}
