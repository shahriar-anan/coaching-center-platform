"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import type { Role } from "@/lib/api/exam/types";

/** The role as a small chip. Students have none, so nothing shows for them. */
export function RoleChip({ role }: { role: Role }) {
  const { t } = useLocale();
  if (role === "STUDENT") {
    return null;
  }
  return (
    <span className="inline-flex rounded-control bg-action/10 px-2.5 py-1 text-pill text-action">
      {t(`common.roles.${role}`)}
    </span>
  );
}

/**
 * The signed-in person in the header: a button with the initial and, from 640 px, the name. Its menu
 * shows the name, the role and log out. A press outside, or Escape, closes the menu.
 */
export function StaffProfileMenu() {
  const { t } = useLocale();
  const { session, logout } = useSession();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onPress = (event: Event) => {
      if (!wrapRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPress);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPress);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (session.status !== "authenticated") {
    return null;
  }
  const { user } = session;

  return (
    <div ref={wrapRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="flex min-h-11 items-center gap-2 rounded-control border border-border-control bg-card px-2 text-meta text-text transition-colors hover:bg-bg"
      >
        <span aria-hidden="true" className="grid size-8 place-items-center rounded-full bg-action text-meta font-semibold text-on-action">
          {user.fullName.trim().charAt(0)}
        </span>
        <span className="sr-only">{t("common.nav.profile")}</span>
        <span className="hidden max-w-40 truncate sm:inline">{user.fullName}</span>
        <ChevronDown aria-hidden="true" className="size-4 shrink-0" />
      </button>
      {open ? (
        <div
          role="region"
          aria-label={t("common.nav.profile")}
          className="absolute end-0 top-full z-30 mt-2 grid w-64 max-w-[calc(100vw-2rem)] gap-3 rounded-card border border-border bg-card p-4 shadow-lg"
        >
          <div className="grid gap-2">
            <p className="text-label font-semibold text-text">{user.fullName}</p>
            <RoleChip role={user.role} />
          </div>
          <Button variant="secondary" onClick={() => void logout()} className="min-h-11 justify-start px-3">
            <LogOut aria-hidden="true" className="size-4" />
            {t("common.nav.logout")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
