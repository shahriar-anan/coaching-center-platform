"use client";

import type { ReactNode } from "react";
import { ApiError } from "@/lib/api/errors";
import { cn } from "@/components/ui/cn";

/** A message above the form's button, for failures that are not about one field. */
export function FormError({ children }: { children: ReactNode }) {
  return (
    <div
      role="alert"
      className="rounded-control border border-danger/40 bg-danger/5 px-4 py-3 text-body text-text"
    >
      {children}
    </div>
  );
}

export function FormNotice({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      role="status"
      className={cn("rounded-control border border-ok/40 bg-ok/5 px-4 py-3 text-body text-text", className)}
    >
      {children}
    </div>
  );
}

/** The API error code, when the thrown value is one. */
export function codeOf(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null;
}

/** Focuses the first element with the given id, if it exists, so keyboard users land on the problem. */
export function focusFirst(ids: string[]): void {
  for (const id of ids) {
    const el = document.getElementById(id);
    if (el) {
      el.focus();
      return;
    }
  }
}

/** Codes that all mean "this code no longer works". The screen says one sentence and no more. */
export const CODE_DEAD = new Set(["INVALID_CODE", "CODE_EXPIRED", "CODE_ALREADY_USED", "CODE_ATTEMPTS_EXCEEDED"]);
