"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { IconButton } from "@/components/ui/icon-button";
import { X } from "lucide-react";

export type ToastTone = "ok" | "danger" | "info";

export type ToastInput = { title: string; tone?: ToastTone };

type ToastItem = ToastInput & { id: number };

type ToastContextValue = { toast: (input: ToastInput) => void };

const ToastContext = createContext<ToastContextValue | null>(null);

const LIFETIME_MS = 4000;

const toneClass: Record<ToastTone, string> = {
  ok: "border-ok",
  danger: "border-danger",
  info: "border-border",
};

/** Shows short messages in a polite live region. Mount once, near the root. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setItems((list) => list.filter((item) => item.id !== id));
  }, []);

  const toast = useCallback(
    (input: ToastInput) => {
      const id = nextId.current++;
      setItems((list) => [...list.slice(-2), { ...input, id }]);
      window.setTimeout(() => dismiss(id), LIFETIME_MS);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        role="status"
        aria-live="polite"
        aria-label={t("common.toast.region")}
        className="pointer-events-none fixed inset-x-0 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-50 grid justify-items-center gap-2 px-4 md:bottom-6"
      >
        {items.map((item) => (
          <div
            key={item.id}
            className={cn(
              "pointer-events-auto flex w-full max-w-md items-center gap-2 rounded-control border-s-4 bg-card py-2 ps-4 pe-2 text-label text-text shadow-lg",
              toneClass[item.tone ?? "info"],
            )}
          >
            <span className="min-w-0 flex-1">{item.title}</span>
            <IconButton
              label={t("common.toast.dismiss")}
              icon={<X aria-hidden="true" className="size-4" />}
              onClick={() => dismiss(item.id)}
            />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return ctx;
}
