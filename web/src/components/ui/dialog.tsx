"use client";

import type { ReactNode } from "react";
import * as RadixDialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";

/**
 * A modal dialog. Radix traps focus, closes on Escape and returns focus to the
 * trigger. The panel is centred on desktop and a full-width sheet on phones.
 */
export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  className,
  initialFocus,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children?: ReactNode;
  footer?: ReactNode;
  className?: string;
  /** Element to focus on open, instead of the first focusable one. Returns null to keep the default. */
  initialFocus?: () => HTMLElement | null | undefined;
}) {
  const { t } = useLocale();
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-40 bg-black/40 motion-safe:animate-in motion-safe:fade-in" />
        <RadixDialog.Content
          onOpenAutoFocus={(event) => {
            const target = initialFocus?.();
            if (target) {
              event.preventDefault();
              target.focus();
            }
          }}
          className={cn(
            "fixed z-50 grid max-h-[85dvh] w-full gap-4 overflow-y-auto bg-card p-5 text-text shadow-xl",
            "inset-x-0 bottom-0 rounded-t-card pb-[max(1.25rem,env(safe-area-inset-bottom))]",
            "sm:inset-x-auto sm:bottom-auto sm:left-1/2 sm:top-1/2 sm:max-w-md sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-card",
            className,
          )}
        >
          <div className="flex items-start justify-between gap-3">
            <div className="grid gap-1">
              <RadixDialog.Title className="text-section font-semibold">{title}</RadixDialog.Title>
              {description ? (
                <RadixDialog.Description className="text-body text-muted">
                  {description}
                </RadixDialog.Description>
              ) : null}
            </div>
            <RadixDialog.Close
              aria-label={t("common.actions.close")}
              className="inline-flex size-11 shrink-0 items-center justify-center rounded-control hover:bg-bg"
            >
              <X aria-hidden="true" className="size-5" />
            </RadixDialog.Close>
          </div>
          {children}
          {footer ? <div className="flex flex-wrap justify-end gap-2">{footer}</div> : null}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
